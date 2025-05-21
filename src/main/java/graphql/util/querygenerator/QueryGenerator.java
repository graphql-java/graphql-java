package graphql.util.querygenerator;

import graphql.ExperimentalApi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;

import javax.annotation.Nullable;
import java.util.stream.Stream;

@ExperimentalApi
public class QueryGenerator {
    private final GraphQLSchema schema;
    private final QueryGeneratorFieldSelection fieldSelectionGenerator;
    private final QueryGeneratorPrinter printer;

    public QueryGenerator(GraphQLSchema schema, QueryGeneratorOptions options) {
        this.schema = schema;
        this.fieldSelectionGenerator = new QueryGeneratorFieldSelection(schema, options);
        this.printer = new QueryGeneratorPrinter();
    }

    public String generateQuery(
            String operationFieldPath,
            @Nullable String operationName,
            @Nullable String arguments,
            @Nullable String typeClassifier
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
            if (typeClassifier != null) {
                throw new IllegalArgumentException("typeClassifier should be used only with interface or union types");
            }
            lastFieldContainer = (GraphQLObjectType) lastType;
        } else if (lastType instanceof GraphQLUnionType) {
            if (typeClassifier == null) {
                throw new IllegalArgumentException("typeClassifier is required for union types");
            }
            lastFieldContainer = ((GraphQLUnionType) lastType).getTypes().stream()
                    .filter(GraphQLFieldsContainer.class::isInstance)
                    .map(GraphQLFieldsContainer.class::cast)
                    .filter(type -> type.getName().equals(typeClassifier))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Type " + typeClassifier + " not found in union " + ((GraphQLUnionType) lastType).getName()));
        } else if (lastType instanceof GraphQLInterfaceType) {
            if (typeClassifier == null) {
                throw new IllegalArgumentException("typeClassifier is required for interface types");
            }
            Stream<GraphQLFieldsContainer> fieldsContainerStream = Stream.concat(
                    Stream.of((GraphQLInterfaceType) lastType),
                    schema.getImplementations((GraphQLInterfaceType) lastType).stream()
            );

            lastFieldContainer = fieldsContainerStream
                    .filter(type -> type.getName().equals(typeClassifier))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Type " + typeClassifier + " not found in interface " + ((GraphQLInterfaceType) lastType).getName()));
        } else {
            throw new IllegalArgumentException("Type " + lastType + " is not a field container");
        }

        QueryGeneratorFieldSelection.FieldSelection rootFieldSelection = fieldSelectionGenerator.buildFields(lastFieldContainer);

        return printer.print(operationFieldPath, operationName, arguments, rootFieldSelection);
    }
}
