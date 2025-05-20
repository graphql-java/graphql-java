package graphql.util.querygenerator;

import graphql.ExperimentalApi;
import graphql.schema.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ExperimentalApi
public class QueryGenerator {
    private final QueryGeneratorOptions options;
    private final GraphQLSchema schema;
    private final QueryGeneratorFieldSelection fieldSelectionGenerator;
    private final QueryGeneratorPrinter printer;

    public QueryGenerator(QueryGeneratorOptions options) {
        this.options = options;
        this.schema = options.getSchema();
        this.fieldSelectionGenerator = new QueryGeneratorFieldSelection(options);
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

        final List<GraphQLFieldsContainer> possibleTypes;

        if (lastType instanceof GraphQLObjectType) {
            if (typeClassifier != null) {
                throw new IllegalArgumentException("typeClassifier should be used only with interface or union types");
            }
            possibleTypes = List.of((GraphQLFieldsContainer) lastType);
        } else if (lastType instanceof GraphQLUnionType) {
            possibleTypes = ((GraphQLUnionType) lastType).getTypes().stream()
                    .filter(GraphQLObjectType.class::isInstance)
                    .map(GraphQLObjectType.class::cast)
                    .filter(type -> typeClassifier == null || type.getName().equals(typeClassifier))
                    .collect(Collectors.toList());
        } else if (lastType instanceof GraphQLInterfaceType) {
            Stream<GraphQLFieldsContainer> fieldsContainerStream = Stream.concat(
                    Stream.of((GraphQLInterfaceType) lastType),
                    schema.getImplementations((GraphQLInterfaceType) lastType).stream()
            );

            possibleTypes = fieldsContainerStream
                    .filter(type -> typeClassifier == null || type.getName().equals(typeClassifier))
                    .collect(Collectors.toList());
        } else {
            throw new IllegalArgumentException("Type " + lastType + " is not a field container");
        }

        if (possibleTypes.isEmpty()) {
            throw new IllegalArgumentException("No possible types found for " + lastType);
        }

        Map<String, List<QueryGeneratorFieldSelection.FieldSelection>> fieldSelections = possibleTypes.stream()
                .collect(Collectors.toMap(
                        GraphQLFieldsContainer::getName,
                        type -> fieldSelectionGenerator.generateFieldSelection(type.getName())
                ));

//        List<QueryGeneratorFieldSelection.FieldSelection> fieldSelectionList =
//                this.fieldSelectionGenerator.generateFieldSelection(type.getName());

        return printer.print(operationFieldPath, operationName, arguments, fieldSelections);
    }
}
