package graphql.normalized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import graphql.Internal;
import graphql.execution.ConditionalNodes;
import graphql.execution.MergedField;
import graphql.execution.ValuesResolver;
import graphql.execution.nextgen.Common;
import graphql.introspection.Introspection;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.VariableDefinition;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.collect.ImmutableKit.map;
import static graphql.execution.MergedField.newMergedField;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.util.FpKit.filterSet;
import static graphql.util.FpKit.groupingBy;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

@Internal
public class ExecutableNormalizedOperationFactory {

    private final ValuesResolver valuesResolver = new ValuesResolver();
    private final ConditionalNodes conditionalNodes = new ConditionalNodes();

    public static ExecutableNormalizedOperation createExecutableNormalizedOperation(GraphQLSchema graphQLSchema,
                                                                                    Document document,
                                                                                    String operationName,
                                                                                    Map<String, Object> coercedVariableValues) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operationName);
        return new ExecutableNormalizedOperationFactory().createNormalizedQueryImpl(graphQLSchema, getOperationResult.operationDefinition, getOperationResult.fragmentsByName, coercedVariableValues, null);
    }


    public static ExecutableNormalizedOperation createExecutableNormalizedOperation(GraphQLSchema graphQLSchema,
                                                                                    OperationDefinition operationDefinition,
                                                                                    Map<String, FragmentDefinition> fragments,
                                                                                    Map<String, Object> coercedVariableValues) {
        return new ExecutableNormalizedOperationFactory().createNormalizedQueryImpl(graphQLSchema, operationDefinition, fragments, coercedVariableValues, null);
    }

    public static ExecutableNormalizedOperation createExecutableNormalizedOperationWithRawVariables(GraphQLSchema graphQLSchema,
                                                                                                    Document document,
                                                                                                    String operationName,
                                                                                                    Map<String, Object> rawVariables) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operationName);
        return new ExecutableNormalizedOperationFactory().createExecutableNormalizedOperationImplWithRawVariables(graphQLSchema, getOperationResult.operationDefinition, getOperationResult.fragmentsByName, rawVariables);
    }

    private ExecutableNormalizedOperation createExecutableNormalizedOperationImplWithRawVariables(GraphQLSchema graphQLSchema,
                                                                                                  OperationDefinition operationDefinition,
                                                                                                  Map<String, FragmentDefinition> fragments,
                                                                                                  Map<String, Object> rawVariables
    ) {

        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();
        Map<String, Object> coerceVariableValues = valuesResolver.coerceVariableValues(graphQLSchema, variableDefinitions, rawVariables);
        Map<String, NormalizedInputValue> normalizedVariableValues = valuesResolver.getNormalizedVariableValues(graphQLSchema, variableDefinitions, rawVariables);
        return createNormalizedQueryImpl(graphQLSchema, operationDefinition, fragments, coerceVariableValues, normalizedVariableValues);
    }

    /**
     * Creates a new Normalized query tree for the provided query
     */
    private ExecutableNormalizedOperation createNormalizedQueryImpl(GraphQLSchema graphQLSchema,
                                                                    OperationDefinition operationDefinition,
                                                                    Map<String, FragmentDefinition> fragments,
                                                                    Map<String, Object> coercedVariableValues,
                                                                    @Nullable Map<String, NormalizedInputValue> normalizedVariableValues) {
        FieldCollectorNormalizedQueryParams parameters = FieldCollectorNormalizedQueryParams
                .newParameters()
                .fragments(fragments)
                .schema(graphQLSchema)
                .coercedVariables(coercedVariableValues)
                .normalizedVariables(normalizedVariableValues)
                .build();

        GraphQLObjectType rootType = Common.getOperationRootType(graphQLSchema, operationDefinition);

        CollectNFResult collectFromOperationResult = collectFromOperation(parameters, operationDefinition, rootType);

        ImmutableListMultimap.Builder<Field, ExecutableNormalizedField> fieldToNormalizedField = ImmutableListMultimap.builder();
        ImmutableMap.Builder<ExecutableNormalizedField, MergedField> normalizedFieldToMergedField = ImmutableMap.builder();
        ImmutableListMultimap.Builder<FieldCoordinates, ExecutableNormalizedField> coordinatesToNormalizedFields = ImmutableListMultimap.builder();

        for (ExecutableNormalizedField topLevel : collectFromOperationResult.children) {
            ImmutableList<FieldAndAstParent> mergedField = collectFromOperationResult.normalizedFieldToAstFields.get(topLevel);
            normalizedFieldToMergedField.put(topLevel, newMergedField(map(mergedField, fieldAndAstParent -> fieldAndAstParent.field)).build());
            updateFieldToNFMap(topLevel, mergedField, fieldToNormalizedField);
            updateCoordinatedToNFMap(coordinatesToNormalizedFields, topLevel);

            buildFieldWithChildren(topLevel,
                    mergedField,
                    parameters,
                    fieldToNormalizedField,
                    normalizedFieldToMergedField,
                    coordinatesToNormalizedFields,
                    1);

        }
        for (FieldCollectorNormalizedQueryParams.PossibleMerger possibleMerger : parameters.possibleMergerList) {
            List<ExecutableNormalizedField> childrenWithSameResultKey = possibleMerger.parent.getChildrenWithSameResultKey(possibleMerger.resultKey);
            ENFMerger.merge(possibleMerger.parent, childrenWithSameResultKey, graphQLSchema);
        }
        return new ExecutableNormalizedOperation(
                operationDefinition.getOperation(),
                operationDefinition.getName(),
                new ArrayList<>(collectFromOperationResult.children),
                fieldToNormalizedField.build(),
                normalizedFieldToMergedField.build(),
                coordinatesToNormalizedFields.build()
        );
    }


    private void buildFieldWithChildren(ExecutableNormalizedField field,
                                        ImmutableList<FieldAndAstParent> mergedField,
                                        FieldCollectorNormalizedQueryParams fieldCollectorNormalizedQueryParams,
                                        ImmutableListMultimap.Builder<Field, ExecutableNormalizedField> fieldNormalizedField,
                                        ImmutableMap.Builder<ExecutableNormalizedField, MergedField> normalizedFieldToMergedField,
                                        ImmutableListMultimap.Builder<FieldCoordinates, ExecutableNormalizedField> coordinatesToNormalizedFields,
                                        int curLevel) {
        CollectNFResult nextLevel = collectFromMergedField(fieldCollectorNormalizedQueryParams, field, mergedField, curLevel + 1);

        for (ExecutableNormalizedField child : nextLevel.children) {
            field.addChild(child);
            ImmutableList<FieldAndAstParent> mergedFieldForChild = nextLevel.normalizedFieldToAstFields.get(child);
            normalizedFieldToMergedField.put(child, newMergedField(map(mergedFieldForChild, fieldAndAstParent -> fieldAndAstParent.field)).build());
            updateFieldToNFMap(child, mergedFieldForChild, fieldNormalizedField);
            updateCoordinatedToNFMap(coordinatesToNormalizedFields, child);

            buildFieldWithChildren(child,
                    mergedFieldForChild,
                    fieldCollectorNormalizedQueryParams,
                    fieldNormalizedField,
                    normalizedFieldToMergedField,
                    coordinatesToNormalizedFields,
                    curLevel + 1);
        }
    }

    private void updateFieldToNFMap(ExecutableNormalizedField executableNormalizedField,
                                    ImmutableList<FieldAndAstParent> mergedField,
                                    ImmutableListMultimap.Builder<Field, ExecutableNormalizedField> fieldToNormalizedField) {
        for (FieldAndAstParent astField : mergedField) {
            fieldToNormalizedField.put(astField.field, executableNormalizedField);
        }
    }

    private void updateCoordinatedToNFMap(ImmutableListMultimap.Builder<FieldCoordinates, ExecutableNormalizedField> coordinatesToNormalizedFields, ExecutableNormalizedField topLevel) {
        for (String objectType : topLevel.getObjectTypeNames()) {
            FieldCoordinates coordinates = FieldCoordinates.coordinates(objectType, topLevel.getFieldName());
            coordinatesToNormalizedFields.put(coordinates, topLevel);
        }
    }

    private static class FieldAndAstParent {
        final Field field;
        final GraphQLCompositeType astParentType;

        private FieldAndAstParent(Field field, GraphQLCompositeType astParentType) {
            this.field = field;
            this.astParentType = astParentType;
        }
    }


    public static class CollectNFResult {
        private final Collection<ExecutableNormalizedField> children;
        private final ImmutableListMultimap<ExecutableNormalizedField, FieldAndAstParent> normalizedFieldToAstFields;

        public CollectNFResult(Collection<ExecutableNormalizedField> children, ImmutableListMultimap<ExecutableNormalizedField, FieldAndAstParent> normalizedFieldToAstFields) {
            this.children = children;
            this.normalizedFieldToAstFields = normalizedFieldToAstFields;
        }
    }


    public CollectNFResult collectFromMergedField(FieldCollectorNormalizedQueryParams parameters,
                                                  ExecutableNormalizedField executableNormalizedField,
                                                  ImmutableList<FieldAndAstParent> mergedField,
                                                  int level) {
        List<GraphQLFieldDefinition> fieldDefs = executableNormalizedField.getFieldDefinitions(parameters.getGraphQLSchema());
        Set<GraphQLObjectType> possibleObjects = resolvePossibleObjects(fieldDefs, parameters.getGraphQLSchema());
        if (possibleObjects.isEmpty()) {
            return new CollectNFResult(Collections.emptyList(), ImmutableListMultimap.of());
        }

        List<CollectedField> collectedFields = new ArrayList<>();
        for (FieldAndAstParent fieldAndAstParent : mergedField) {
            if (fieldAndAstParent.field.getSelectionSet() == null) {
                continue;
            }
            GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(parameters.getGraphQLSchema(), fieldAndAstParent.astParentType, fieldAndAstParent.field.getName());
            GraphQLUnmodifiedType astParentType = unwrapAll(fieldDefinition.getType());
            this.collectFromSelectionSet(parameters,
                    fieldAndAstParent.field.getSelectionSet(),
                    collectedFields,
                    (GraphQLCompositeType) astParentType,
                    possibleObjects
            );
        }
        Map<String, List<CollectedField>> fieldsByName = fieldsByResultKey(collectedFields);
        ImmutableList.Builder<ExecutableNormalizedField> resultNFs = ImmutableList.builder();
        ImmutableListMultimap.Builder<ExecutableNormalizedField, FieldAndAstParent> normalizedFieldToAstFields = ImmutableListMultimap.builder();

        createNFs(resultNFs, parameters, fieldsByName, normalizedFieldToAstFields, level, executableNormalizedField);

        return new CollectNFResult(resultNFs.build(), normalizedFieldToAstFields.build());
    }

    private Map<String, List<CollectedField>> fieldsByResultKey(List<CollectedField> collectedFields) {
        Map<String, List<CollectedField>> fieldsByName = new LinkedHashMap<>();
        for (CollectedField collectedField : collectedFields) {
            fieldsByName.computeIfAbsent(collectedField.field.getResultKey(), ignored -> new ArrayList<>()).add(collectedField);
        }
        return fieldsByName;
    }

    public CollectNFResult collectFromOperation(FieldCollectorNormalizedQueryParams parameters,
                                                OperationDefinition operationDefinition,
                                                GraphQLObjectType rootType) {


        Set<GraphQLObjectType> possibleObjects = ImmutableSet.of(rootType);
        List<CollectedField> collectedFields = new ArrayList<>();
        collectFromSelectionSet(parameters, operationDefinition.getSelectionSet(), collectedFields, rootType, possibleObjects);
        // group by result key
        Map<String, List<CollectedField>> fieldsByName = fieldsByResultKey(collectedFields);
        ImmutableList.Builder<ExecutableNormalizedField> resultNFs = ImmutableList.builder();
        ImmutableListMultimap.Builder<ExecutableNormalizedField, FieldAndAstParent> normalizedFieldToAstFields = ImmutableListMultimap.builder();

        createNFs(resultNFs, parameters, fieldsByName, normalizedFieldToAstFields, 1, null);

        return new CollectNFResult(resultNFs.build(), normalizedFieldToAstFields.build());
    }

    private void createNFs(ImmutableList.Builder<ExecutableNormalizedField> nfListBuilder,
                           FieldCollectorNormalizedQueryParams parameters,
                           Map<String, List<CollectedField>> fieldsByName,
                           ImmutableListMultimap.Builder<ExecutableNormalizedField, FieldAndAstParent> normalizedFieldToAstFields,
                           int level,
                           ExecutableNormalizedField parent) {
        for (String resultKey : fieldsByName.keySet()) {
            List<CollectedField> fieldsWithSameResultKey = fieldsByName.get(resultKey);
            List<CollectedFieldGroup> commonParentsGroups = groupByCommonParents(fieldsWithSameResultKey);
            for (CollectedFieldGroup fieldGroup : commonParentsGroups) {
                ExecutableNormalizedField nf = createNF(parameters, fieldGroup, level, parent);
                if (nf == null) {
                    continue;
                }
                for (CollectedField collectedField : fieldGroup.fields) {
                    normalizedFieldToAstFields.put(nf, new FieldAndAstParent(collectedField.field, collectedField.astTypeCondition));
                }
                nfListBuilder.add(nf);
            }
            if (commonParentsGroups.size() > 1) {
                parameters.addPossibleMergers(parent, resultKey);
            }
        }
    }

    private ExecutableNormalizedField createNF(FieldCollectorNormalizedQueryParams parameters,
                                               CollectedFieldGroup collectedFieldGroup,
                                               int level,
                                               ExecutableNormalizedField parent) {
        Field field;
        Set<GraphQLObjectType> objectTypes = collectedFieldGroup.objectTypes;
        field = collectedFieldGroup.fields.iterator().next().field;
        String fieldName = field.getName();
        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(parameters.getGraphQLSchema(), objectTypes.iterator().next(), fieldName);

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDefinition.getArguments(), field.getArguments(), parameters.getCoercedVariableValues());
        Map<String, NormalizedInputValue> normalizedArgumentValues = null;
        if (parameters.getNormalizedVariableValues() != null) {
            normalizedArgumentValues = valuesResolver.getNormalizedArgumentValues(fieldDefinition.getArguments(), field.getArguments(), parameters.getNormalizedVariableValues());
        }
        ImmutableList<String> objectTypeNames = map(objectTypes, GraphQLObjectType::getName);

        return ExecutableNormalizedField.newNormalizedField()
                .alias(field.getAlias())
                .resolvedArguments(argumentValues)
                .normalizedArguments(normalizedArgumentValues)
                .astArguments(field.getArguments())
                .objectTypeNames(objectTypeNames)
                .fieldName(fieldName)
                .level(level)
                .parent(parent)
                .build();
    }

    private static class CollectedFieldGroup {
        Set<GraphQLObjectType> objectTypes;
        Set<CollectedField> fields;

        public CollectedFieldGroup(Set<CollectedField> fields, Set<GraphQLObjectType> objectTypes) {
            this.fields = fields;
            this.objectTypes = objectTypes;
        }
    }

    private List<CollectedFieldGroup> groupByCommonParents(Collection<CollectedField> fields) {
        ImmutableSet.Builder<GraphQLObjectType> objectTypes = ImmutableSet.builder();
        for (CollectedField collectedField : fields) {
            objectTypes.addAll(collectedField.objectTypes);
        }
        Set<GraphQLObjectType> allRelevantObjects = objectTypes.build();
        Map<GraphQLType, ImmutableList<CollectedField>> groupByAstParent = groupingBy(fields, fieldAndType -> fieldAndType.astTypeCondition);
        if (groupByAstParent.size() == 1) {
            return singletonList(new CollectedFieldGroup(ImmutableSet.copyOf(fields), allRelevantObjects));
        }
        ImmutableList.Builder<CollectedFieldGroup> result = ImmutableList.builder();
        for (GraphQLObjectType objectType : allRelevantObjects) {
            Set<CollectedField> relevantFields = filterSet(fields, field -> field.objectTypes.contains(objectType));
            result.add(new CollectedFieldGroup(relevantFields, singleton(objectType)));
        }
        return result.build();
    }


    private void collectFromSelectionSet(FieldCollectorNormalizedQueryParams parameters,
                                         SelectionSet selectionSet,
                                         List<CollectedField> result,
                                         GraphQLCompositeType astTypeCondition,
                                         Set<GraphQLObjectType> possibleObjects
    ) {
        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectField(parameters, result, (Field) selection, possibleObjects, astTypeCondition);
            } else if (selection instanceof InlineFragment) {
                collectInlineFragment(parameters, result, (InlineFragment) selection, possibleObjects, astTypeCondition);
            } else if (selection instanceof FragmentSpread) {
                collectFragmentSpread(parameters, result, (FragmentSpread) selection, possibleObjects, astTypeCondition);
            }
        }
    }

    private static class CollectedField {
        Field field;
        Set<GraphQLObjectType> objectTypes;
        GraphQLCompositeType astTypeCondition;

        public CollectedField(Field field, Set<GraphQLObjectType> objectTypes, GraphQLCompositeType astTypeCondition) {
            this.field = field;
            this.objectTypes = objectTypes;
            this.astTypeCondition = astTypeCondition;
        }

        public boolean isAbstract() {
            return GraphQLTypeUtil.isInterfaceOrUnion(astTypeCondition);
        }

        public boolean isConcrete() {
            return GraphQLTypeUtil.isObjectType(astTypeCondition);
        }
    }

    private void collectFragmentSpread(FieldCollectorNormalizedQueryParams parameters,
                                       List<CollectedField> result,
                                       FragmentSpread fragmentSpread,
                                       Set<GraphQLObjectType> possibleObjects,
                                       GraphQLCompositeType astTypeCondition
    ) {
        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), fragmentSpread.getDirectives())) {
            return;
        }
        FragmentDefinition fragmentDefinition = assertNotNull(parameters.getFragmentsByName().get(fragmentSpread.getName()));

        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), fragmentDefinition.getDirectives())) {
            return;
        }
        GraphQLCompositeType newAstTypeCondition = (GraphQLCompositeType) assertNotNull(parameters.getGraphQLSchema().getType(fragmentDefinition.getTypeCondition().getName()));
        Set<GraphQLObjectType> newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newAstTypeCondition, parameters.getGraphQLSchema());
        collectFromSelectionSet(parameters, fragmentDefinition.getSelectionSet(), result, newAstTypeCondition, newPossibleObjects);
    }


    private void collectInlineFragment(FieldCollectorNormalizedQueryParams parameters,
                                       List<CollectedField> result,
                                       InlineFragment inlineFragment,
                                       Set<GraphQLObjectType> possibleObjects,
                                       GraphQLCompositeType astTypeCondition
    ) {
        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), inlineFragment.getDirectives())) {
            return;
        }
        Set<GraphQLObjectType> newPossibleObjects = possibleObjects;
        GraphQLCompositeType newAstTypeCondition = astTypeCondition;

        if (inlineFragment.getTypeCondition() != null) {
            newAstTypeCondition = (GraphQLCompositeType) parameters.getGraphQLSchema().getType(inlineFragment.getTypeCondition().getName());
            newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newAstTypeCondition, parameters.getGraphQLSchema());

        }
        collectFromSelectionSet(parameters, inlineFragment.getSelectionSet(), result, newAstTypeCondition, newPossibleObjects);
    }

    private void collectField(FieldCollectorNormalizedQueryParams parameters,
                              List<CollectedField> result,
                              Field field,
                              Set<GraphQLObjectType> possibleObjectTypes,
                              GraphQLCompositeType astTypeCondition
    ) {
        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), field.getDirectives())) {
            return;
        }
        // this means there is actually no possible type for this field and we are done
        if (possibleObjectTypes.isEmpty()) {
            return;
        }
        result.add(new CollectedField(field, possibleObjectTypes, astTypeCondition));
    }

    private Set<GraphQLObjectType> narrowDownPossibleObjects(Set<GraphQLObjectType> currentOnes,
                                                             GraphQLCompositeType typeCondition,
                                                             GraphQLSchema graphQLSchema) {

        ImmutableSet<GraphQLObjectType> resolvedTypeCondition = resolvePossibleObjects(typeCondition, graphQLSchema);
        if (currentOnes.isEmpty()) {
            return resolvedTypeCondition;
        }
        return Sets.intersection(currentOnes, resolvedTypeCondition);
    }

    private ImmutableSet<GraphQLObjectType> resolvePossibleObjects(List<GraphQLFieldDefinition> defs, GraphQLSchema graphQLSchema) {
        ImmutableSet.Builder<GraphQLObjectType> builder = ImmutableSet.builder();

        for (GraphQLFieldDefinition def : defs) {
            GraphQLUnmodifiedType outputType = unwrapAll(def.getType());
            if (outputType instanceof GraphQLCompositeType) {
                builder.addAll(resolvePossibleObjects((GraphQLCompositeType) outputType, graphQLSchema));
            }
        }

        return builder.build();
    }

    private ImmutableSet<GraphQLObjectType> resolvePossibleObjects(GraphQLCompositeType type, GraphQLSchema graphQLSchema) {
        if (type instanceof GraphQLObjectType) {
            return ImmutableSet.of((GraphQLObjectType) type);
        } else if (type instanceof GraphQLInterfaceType) {
            return ImmutableSet.copyOf(graphQLSchema.getImplementations((GraphQLInterfaceType) type));
        } else if (type instanceof GraphQLUnionType) {
            List types = ((GraphQLUnionType) type).getTypes();
            return ImmutableSet.copyOf(types);
        } else {
            return assertShouldNeverHappen();
        }
    }
}
