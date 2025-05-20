package graphql.util.querygenerator;

import graphql.normalized.nf.NormalizedDocumentFactory;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class QueryGeneratorFieldSelection {
    private final QueryGeneratorOptions options;
    private final GraphQLSchema schema;

    private static final GraphQLObjectType emptyObjectType = GraphQLObjectType.newObject()
            .name("Empty")
            .build();

    public QueryGeneratorFieldSelection(GraphQLSchema schema, QueryGeneratorOptions options) {
        this.options = options;
        this.schema = schema;
    }

    FieldSelection generateFieldSelection(String typeName) {
        GraphQLType type = this.schema.getType(typeName);

        if (type == null) {
            throw new IllegalArgumentException("Type " + typeName + " not found in schema");
        }

        if (!(type instanceof GraphQLFieldsContainer)) {
            throw new IllegalArgumentException("Type " + typeName + " is not a field container");
        }

        return buildFields((GraphQLFieldsContainer) type);
    }

    private FieldSelection buildFields(GraphQLFieldsContainer fieldsContainer) {
        Queue<GraphQLFieldsContainer> containersQueue = new LinkedList<>();
        containersQueue.add(fieldsContainer);

        Queue<FieldSelection> fieldSelectionQueue = new LinkedList<>();
        FieldSelection root = new FieldSelection(fieldsContainer.getName(), new ArrayList<>());
        fieldSelectionQueue.add(root);

        Set<FieldCoordinates> visited = new HashSet<>();
        int totalFieldCount = 0;

        while (!containersQueue.isEmpty()) {
            GraphQLFieldsContainer container = containersQueue.poll();
            FieldSelection fieldSelection = fieldSelectionQueue.poll();

            for (GraphQLFieldDefinition fieldDef : container.getFieldDefinitions()) {
                if (totalFieldCount >= options.getMaxFieldCount()) {
                    break;
                }

                if (hasRequiredArgs(fieldDef)) {
                    continue;
                }

                FieldCoordinates fieldCoordinates = FieldCoordinates.coordinates(container, fieldDef.getName());

                if (visited.contains(fieldCoordinates)) {
                    continue;
                }

                GraphQLType unwrappedType = GraphQLTypeUtil.unwrapAll(fieldDef.getType());
                boolean isFieldContainer = unwrappedType instanceof GraphQLFieldsContainer;

                final FieldSelection newFieldSelection = isFieldContainer ?
                        new FieldSelection(fieldDef.getName(), new ArrayList<>())
                        : new FieldSelection(fieldDef.getName(), null);

                fieldSelection.fields.add(newFieldSelection);

                fieldSelectionQueue.add(newFieldSelection);

                if (isFieldContainer) {
                    visited.add(fieldCoordinates);
                    containersQueue.add((GraphQLFieldsContainer) unwrappedType);
                } else {
                    containersQueue.add(emptyObjectType);
                }

                totalFieldCount++;
            }

            if (totalFieldCount >= options.getMaxFieldCount()) {
                break;
            }
        }

        return root;
    }

    private boolean hasRequiredArgs(GraphQLFieldDefinition fieldDefinition) {
        // TODO: Maybe provide a hook to allow callers to resolve required arguments
        return fieldDefinition.getArguments().stream()
                .anyMatch(arg -> {
                    GraphQLInputType argType = arg.getType();
                    boolean isMandatory = GraphQLTypeUtil.isNonNull(argType);
                    boolean hasDefaultValue = arg.hasSetDefaultValue();

                    return isMandatory && !hasDefaultValue;
                });
    }

    public static class FieldSelection {
        public final String name;
        public final List<FieldSelection> fields;

        public FieldSelection(String name, List<FieldSelection> fields) {
            this.name = name;
            this.fields = fields;
        }

    }

    public static class QueryGeneratorResult {

    }
}
