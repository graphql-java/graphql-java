package graphql.util.querygenerator;

import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

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
        Queue<List<GraphQLFieldsContainer>> containersQueue = new LinkedList<>();
        containersQueue.add(Collections.singletonList(fieldsContainer));

        Queue<FieldSelection> fieldSelectionQueue = new LinkedList<>();
        FieldSelection root = new FieldSelection(fieldsContainer.getName(), new HashMap<>(), false);
        fieldSelectionQueue.add(root);

        Set<FieldCoordinates> visited = new HashSet<>();
        int totalFieldCount = 0;

        while (!containersQueue.isEmpty()) {
            List<GraphQLFieldsContainer> containers = containersQueue.poll();
            FieldSelection fieldSelection = fieldSelectionQueue.poll();

            for (GraphQLFieldsContainer container : containers) {

                if (!options.getFilterFieldContainerPredicate().test(container)) {
                    continue;
                }

                for (GraphQLFieldDefinition fieldDef : container.getFieldDefinitions()) {
                    if (!options.getFilterFieldDefinitionPredicate().test(fieldDef)) {
                        continue;
                    }

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
                    boolean isUnionType = unwrappedType instanceof GraphQLUnionType;
                    boolean isInterfaceType = unwrappedType instanceof GraphQLInterfaceType;

                    // TODO: This statement is kinda awful
                    final FieldSelection newFieldSelection;

                    if (isUnionType || isInterfaceType) {
                        newFieldSelection = new FieldSelection(fieldDef.getName(), new HashMap<>(), true);
                    } else if (isFieldContainer) {
                        newFieldSelection = new FieldSelection(fieldDef.getName(), new HashMap<>(), false);
                    } else {
                        newFieldSelection = new FieldSelection(fieldDef.getName(), null, false);
                    }


                    fieldSelection.fieldsByContainer.computeIfAbsent(container.getName(), key -> new ArrayList<>()).add(newFieldSelection);

                    fieldSelectionQueue.add(newFieldSelection);

                    if (unwrappedType instanceof GraphQLInterfaceType) {
                        GraphQLInterfaceType interfaceType = (GraphQLInterfaceType) unwrappedType;
                        List<GraphQLFieldsContainer> possibleTypes = new ArrayList<>(schema.getImplementations(interfaceType));

                        containersQueue.add(possibleTypes);
                    } else if (isFieldContainer) {
                        visited.add(fieldCoordinates);
                        containersQueue.add(Collections.singletonList((GraphQLFieldsContainer) unwrappedType));
                    } else if (isUnionType) {
                        GraphQLUnionType unionType = (GraphQLUnionType) unwrappedType;
                        List<GraphQLFieldsContainer> possibleTypes = unionType.getTypes().stream()
                                .filter(possibleType -> possibleType instanceof GraphQLFieldsContainer)
                                .map(possibleType -> (GraphQLFieldsContainer) possibleType)
                                .collect(Collectors.toList());

                        containersQueue.add(possibleTypes);
                    } else {
                        containersQueue.add(Collections.singletonList(emptyObjectType));
                    }

                    totalFieldCount++;
                }
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
        public final boolean needsTypeClassifier;
        public final Map<String, List<FieldSelection>> fieldsByContainer;

        public FieldSelection(String name, Map<String, List<FieldSelection>> fieldsByContainer, boolean needsTypeClassifier) {
            this.name = name;
            this.needsTypeClassifier = needsTypeClassifier;
            this.fieldsByContainer = fieldsByContainer;
        }

    }
}
