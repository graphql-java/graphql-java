package graphql.util.querygenerator;

import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
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
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

class QueryGeneratorFieldSelection {
    private final QueryGeneratorOptions options;
    private final GraphQLSchema schema;

    private static final GraphQLObjectType EMPTY_OBJECT_TYPE = GraphQLObjectType.newObject()
            .name("Empty")
            .build();

    QueryGeneratorFieldSelection(GraphQLSchema schema, QueryGeneratorOptions options) {
        this.options = options;
        this.schema = schema;
    }

    FieldSelection buildFields(GraphQLFieldsContainer fieldsContainer) {
        Queue<List<GraphQLFieldsContainer>> containersQueue = new LinkedList<>();
        containersQueue.add(Collections.singletonList(fieldsContainer));

        Queue<FieldSelection> fieldSelectionQueue = new LinkedList<>();
        FieldSelection root = new FieldSelection(fieldsContainer.getName(), new HashMap<>(), false);
        fieldSelectionQueue.add(root);

        Set<FieldCoordinates> visited = new HashSet<>();
        AtomicInteger totalFieldCount = new AtomicInteger(0);

        while (!containersQueue.isEmpty()) {
            processContainers(containersQueue, fieldSelectionQueue, visited, totalFieldCount);

            if (totalFieldCount.get() >= options.getMaxFieldCount()) {
                break;
            }
        }

        return root;
    }

    private void processContainers(Queue<List<GraphQLFieldsContainer>> containersQueue,
                                   Queue<FieldSelection> fieldSelectionQueue,
                                   Set<FieldCoordinates> visited,
                                   AtomicInteger totalFieldCount) {
        List<GraphQLFieldsContainer> containers = containersQueue.poll();
        FieldSelection fieldSelection = fieldSelectionQueue.poll();

        for (GraphQLFieldsContainer container : Objects.requireNonNull(containers)) {
            if (!options.getFilterFieldContainerPredicate().test(container)) {
                continue;
            }

            for (GraphQLFieldDefinition fieldDef : container.getFieldDefinitions()) {
                if (!options.getFilterFieldDefinitionPredicate().test(fieldDef)) {
                    continue;
                }

                if (totalFieldCount.get() >= options.getMaxFieldCount()) {
                    break;
                }

                if (hasRequiredArgs(fieldDef)) {
                    continue;
                }

                FieldCoordinates fieldCoordinates = FieldCoordinates.coordinates(container, fieldDef.getName());

                if (visited.contains(fieldCoordinates)) {
                    continue;
                }

                processField(
                        container,
                        fieldDef,
                        Objects.requireNonNull(fieldSelection),
                        containersQueue,
                        fieldSelectionQueue,
                        fieldCoordinates,
                        visited,
                        totalFieldCount
                );
            }
        }
    }

    private void processField(GraphQLFieldsContainer container,
                              GraphQLFieldDefinition fieldDef,
                              FieldSelection fieldSelection,
                              Queue<List<GraphQLFieldsContainer>> containersQueue,
                              Queue<FieldSelection> fieldSelectionQueue,
                              FieldCoordinates fieldCoordinates,
                              Set<FieldCoordinates> visited,
                              AtomicInteger totalFieldCount) {

        GraphQLType unwrappedType = GraphQLTypeUtil.unwrapAll(fieldDef.getType());
        FieldSelection newFieldSelection = getFieldSelection(fieldDef, unwrappedType);

        fieldSelection.fieldsByContainer.computeIfAbsent(container.getName(), key -> new ArrayList<>()).add(newFieldSelection);

        fieldSelectionQueue.add(newFieldSelection);

        if (unwrappedType instanceof GraphQLInterfaceType) {
            GraphQLInterfaceType interfaceType = (GraphQLInterfaceType) unwrappedType;
            List<GraphQLFieldsContainer> possibleTypes = new ArrayList<>(schema.getImplementations(interfaceType));

            containersQueue.add(possibleTypes);
        } else if (unwrappedType instanceof GraphQLUnionType) {
            GraphQLUnionType unionType = (GraphQLUnionType) unwrappedType;
            List<GraphQLFieldsContainer> possibleTypes = unionType.getTypes().stream()
                    .filter(possibleType -> possibleType instanceof GraphQLFieldsContainer)
                    .map(possibleType -> (GraphQLFieldsContainer) possibleType)
                    .collect(Collectors.toList());

            containersQueue.add(possibleTypes);
        } else if (unwrappedType instanceof GraphQLFieldsContainer) {
            visited.add(fieldCoordinates);
            containersQueue.add(Collections.singletonList((GraphQLFieldsContainer) unwrappedType));
        } else {
            containersQueue.add(Collections.singletonList(EMPTY_OBJECT_TYPE));
        }

        totalFieldCount.incrementAndGet();
    }

    private static FieldSelection getFieldSelection(GraphQLFieldDefinition fieldDef, GraphQLType unwrappedType) {
        boolean typeNeedsClassifier = unwrappedType instanceof GraphQLUnionType || unwrappedType instanceof GraphQLInterfaceType;

        // TODO: This statement is kinda awful
        final FieldSelection newFieldSelection;

        if (typeNeedsClassifier) {
            newFieldSelection = new FieldSelection(fieldDef.getName(), new HashMap<>(), true);
        } else if (unwrappedType instanceof GraphQLFieldsContainer) {
            newFieldSelection = new FieldSelection(fieldDef.getName(), new HashMap<>(), false);
        } else {
            newFieldSelection = new FieldSelection(fieldDef.getName(), null, false);
        }
        return newFieldSelection;
    }

    private boolean hasRequiredArgs(GraphQLFieldDefinition fieldDefinition) {
        // TODO: Maybe provide a hook to allow callers to resolve required arguments
        return fieldDefinition.getArguments().stream()
                .anyMatch(arg -> GraphQLTypeUtil.isNonNull(arg.getType()) && !arg.hasSetDefaultValue());
    }

   static class FieldSelection {
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
