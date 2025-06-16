package graphql.util.querygenerator;

import graphql.ExperimentalApi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import graphql.util.querygenerator.QueryGeneratorFieldSelection.FieldSelection;
import graphql.util.querygenerator.QueryGeneratorFieldSelection.FieldSelectionResult;
import org.jspecify.annotations.Nullable;

import java.util.stream.Stream;

/**
 * Generates a GraphQL query string based on the provided operation field path, operation name, arguments, and type classifier.
 * <p>
 * While this class is useful for testing purposes, such as ensuring that all fields from a certain type are being
 * fetched correctly, it is important to note that generating GraphQL queries with all possible fields defeats one of
 * the main purposes of a GraphQL API: allowing clients to be selective about the fields they want to fetch.
 * <p>
 * Callers can pass options to customize the query generation process, such as filtering fields or
 * limiting the maximum number of fields.
 * <p>
 *
 */
@ExperimentalApi
public class QueryGenerator {
    private final GraphQLSchema schema;
    private final QueryGeneratorFieldSelection fieldSelectionGenerator;
    private final QueryGeneratorPrinter printer;


    /**
     * Constructor for QueryGenerator using default options.
     *
     * @param schema the GraphQL schema
     */
    public QueryGenerator(GraphQLSchema schema) {
        this.schema = schema;
        this.fieldSelectionGenerator = new QueryGeneratorFieldSelection(schema, QueryGeneratorOptions.newBuilder().build());
        this.printer = new QueryGeneratorPrinter();
    }

    /**
     * Constructor for QueryGenerator.
     *
     * @param schema the GraphQL schema
     * @param options the options for query generation
     */
    public QueryGenerator(GraphQLSchema schema, QueryGeneratorOptions options) {
        this.schema = schema;
        this.fieldSelectionGenerator = new QueryGeneratorFieldSelection(schema, options);
        this.printer = new QueryGeneratorPrinter();
    }

    /**
     * Generates a GraphQL query string based on the provided operation field path, operation name, arguments,
     * and type classifier.
     *
     * <p>
     * operationFieldPath is a string that represents the path to the field in the GraphQL schema. This method
     * will generate a query that includes all fields from the specified type, including nested fields.
     * <p>
     * operationName is optional. When passed, the generated query will contain that value in the operation name.
     * <p>
     * arguments are optional. When passed, the generated query will contain that value in the arguments.
     * <p>
     * typeName is optional. It should not be passed in when the field in the path is an object type, and it
     * **should** be passed when the field in the path is an interface or union type. In the latter case, its value
     * should be an object type that is part of the union or implements the interface.
     *
     * @param operationFieldPath the operation field path (e.g., "Query.user", "Mutation.createUser", "Subscription.userCreated")
     * @param operationName optional: the operation name (e.g., "getUser")
     * @param arguments optional: the arguments for the operation in a plain text form (e.g., "(id: 1)")
     * @param typeName optional: the type name for when operationFieldPath points to a field of union or interface types (e.g., "FirstPartyUser")
     *
     * @return a QueryGeneratorResult containing the generated query string and additional information
     */
    public QueryGeneratorResult generateQuery(
            String operationFieldPath,
            @Nullable String operationName,
            @Nullable String arguments,
            @Nullable String typeName
    ) {
        String[] fieldParts = operationFieldPath.split("\\.");
        String operation = fieldParts[0];

        if (fieldParts.length < 2) {
            throw new IllegalArgumentException("Field path must contain at least an operation and a field");
        }

        if (!operation.equals("Query") && !operation.equals("Mutation") && !operation.equals("Subscription")) {
            throw new IllegalArgumentException("Operation must be 'Query', 'Mutation' or 'Subscription'");
        }

        GraphQLFieldsContainer fieldContainer = schema.getObjectType(operation);

        for (int i = 1; i < fieldParts.length - 1; i++) {
            String fieldName = fieldParts[i];
            GraphQLFieldDefinition fieldDefinition = fieldContainer.getFieldDefinition(fieldName);
            if (fieldDefinition == null) {
                throw new IllegalArgumentException("Field " + fieldName + " not found in type " + fieldContainer.getName());
            }
            // intermediate fields in the path need to be a field container
            if (!(fieldDefinition.getType() instanceof GraphQLFieldsContainer)) {
                throw new IllegalArgumentException("Type " + fieldDefinition.getType() + " is not a field container");
            }
            fieldContainer = (GraphQLFieldsContainer) fieldDefinition.getType();
        }

        String lastFieldName = fieldParts[fieldParts.length - 1];
        GraphQLFieldDefinition lastFieldDefinition = fieldContainer.getFieldDefinition(lastFieldName);
        if (lastFieldDefinition == null) {
            throw new IllegalArgumentException("Field " + lastFieldName + " not found in type " + fieldContainer.getName());
        }

        // last field may be an object, interface or union type
        GraphQLOutputType lastType = lastFieldDefinition.getType();

        final GraphQLFieldsContainer lastFieldContainer;

        if (lastType instanceof GraphQLObjectType) {
            if (typeName != null) {
                throw new IllegalArgumentException("typeName should be used only with interface or union types");
            }
            lastFieldContainer = (GraphQLObjectType) lastType;
        } else if (lastType instanceof GraphQLUnionType) {
            if (typeName == null) {
                throw new IllegalArgumentException("typeName is required for union types");
            }
            lastFieldContainer = ((GraphQLUnionType) lastType).getTypes().stream()
                    .filter(GraphQLFieldsContainer.class::isInstance)
                    .map(GraphQLFieldsContainer.class::cast)
                    .filter(type -> type.getName().equals(typeName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Type " + typeName + " not found in union " + ((GraphQLUnionType) lastType).getName()));
        } else if (lastType instanceof GraphQLInterfaceType) {
            if (typeName == null) {
                throw new IllegalArgumentException("typeName is required for interface types");
            }
            Stream<GraphQLFieldsContainer> fieldsContainerStream = Stream.concat(
                    Stream.of((GraphQLInterfaceType) lastType),
                    schema.getImplementations((GraphQLInterfaceType) lastType).stream()
            );

            lastFieldContainer = fieldsContainerStream
                    .filter(type -> type.getName().equals(typeName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Type " + typeName + " not found in interface " + ((GraphQLInterfaceType) lastType).getName()));
        } else {
            throw new IllegalArgumentException("Type " + lastType + " is not a field container");
        }

        FieldSelectionResult fieldSelectionResult = fieldSelectionGenerator.buildFields(lastFieldContainer);
        FieldSelection rootFieldSelection = fieldSelectionResult.rootFieldSelection;

        String query = printer.print(operationFieldPath, operationName, arguments, rootFieldSelection);

        return new QueryGeneratorResult(
                query,
                lastFieldContainer.getName(),
                fieldSelectionResult.totalFieldCount,
                fieldSelectionResult.reachedMaxFieldCount
        );
    }
}
