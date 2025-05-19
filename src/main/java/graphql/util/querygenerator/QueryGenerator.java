package graphql.util.querygenerator;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLSchema;

import javax.annotation.Nullable;
import java.util.List;

public class QueryGenerator {
    private final QueryGeneratorOptions options;
    private final GraphQLSchema schema;
    private final QueryGeneratorFieldSelection fieldSelectionGenerator;
    private final QueryGeneratorPrinter printer;

    public QueryGenerator(QueryGeneratorOptions options) {
        this.options = options;
        this.schema = options.getSchema();
        this.fieldSelectionGenerator = new QueryGeneratorFieldSelection(options);
        // TODO pass args as options
        this.printer = new QueryGeneratorPrinter();
    }

    public String generateQuery(
            String operationFieldPath,
            @Nullable String operationName,
            @Nullable String arguments
    ) {
        String[] fieldParts = operationFieldPath.split("\\.");
        String operation = fieldParts[0];

        if( fieldParts.length < 2) {
            throw new IllegalArgumentException("Field path must contain at least an operation and a field");
        }

        if (!operation.equals("Query") && !operation.equals("Mutation") && !operation.equals("Subscription")) {
            throw new IllegalArgumentException("Operation must be 'Query', 'Mutation' or 'Subscription'");
        }

        GraphQLFieldsContainer type = schema.getObjectType(operation);

        for (int i = 1; i < fieldParts.length; i++) {
            String fieldName = fieldParts[i];
            GraphQLFieldDefinition fieldDefinition = type.getFieldDefinition(fieldName);
            if (fieldDefinition == null) {
                throw new IllegalArgumentException("Field " + fieldName + " not found in type " + type.getName());
            }
            if (!(fieldDefinition.getType() instanceof GraphQLFieldsContainer)) {
                throw new IllegalArgumentException("Type " + fieldDefinition.getType() + " is not a field container");
            }
            type = (GraphQLFieldsContainer) fieldDefinition.getType();
        }

        List<QueryGeneratorFieldSelection.FieldSelection> fieldSelectionList =
                this.fieldSelectionGenerator.generateFieldSelection(type.getName());

        return printer.print(operationFieldPath, operationName, arguments, fieldSelectionList);
    }
}
