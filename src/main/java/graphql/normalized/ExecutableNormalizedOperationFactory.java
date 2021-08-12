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
import graphql.language.Argument;
import graphql.language.AstComparator;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.collect.ImmutableKit.map;
import static graphql.execution.MergedField.newMergedField;
import static graphql.schema.GraphQLTypeUtil.isInterfaceOrUnion;
import static graphql.schema.GraphQLTypeUtil.isObjectType;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.util.FpKit.filterSet;
import static graphql.util.FpKit.groupingBy;
import static java.util.Collections.emptySet;
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
        return new ExecutableNormalizedOperation(new ArrayList<>(collectFromOperationResult.children), fieldToNormalizedField.build(), normalizedFieldToMergedField.build(), coordinatesToNormalizedFields.build());
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
        GraphQLUnmodifiedType fieldType = unwrapAll(executableNormalizedField.getType(parameters.getGraphQLSchema()));
        // if not composite we don't have any selectionSet because it is a Scalar or enum
        if (!(fieldType instanceof GraphQLCompositeType)) {
            return new CollectNFResult(Collections.emptyList(), ImmutableListMultimap.of());
        }

        Set<GraphQLObjectType> possibleObjects = resolvePossibleObjects((GraphQLCompositeType) fieldType, parameters.getGraphQLSchema());

        List<CollectedField> collectedFields = new ArrayList<>();
        for (FieldAndAstParent fieldAndAstParent : mergedField) {
            if (fieldAndAstParent.field.getSelectionSet() == null) {
                continue;
            }
            GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(parameters.getGraphQLSchema(), fieldAndAstParent.astParentType, fieldAndAstParent.field.getName());
            GraphQLUnmodifiedType astParentType = unwrapAll(fieldDefinition.getType());
            this.collectFromSelectionSet2(parameters,
                    fieldAndAstParent.field.getSelectionSet(),
                    collectedFields,
                    (GraphQLCompositeType) astParentType,
                    possibleObjects
            );
        }
        Map<String, List<CollectedField>> fieldsByName = new LinkedHashMap<>();
        for (CollectedField collectedField : collectedFields) {
            fieldsByName.computeIfAbsent(collectedField.field.getResultKey(), ignored -> new ArrayList<>()).add(collectedField);
        }
        List<ExecutableNormalizedField> resultNFs = new ArrayList<>();
        ImmutableListMultimap.Builder<ExecutableNormalizedField, FieldAndAstParent> normalizedFieldToAstFields = ImmutableListMultimap.builder();

        createNFs(parameters, fieldsByName, resultNFs, normalizedFieldToAstFields, level, executableNormalizedField);

        return new CollectNFResult(resultNFs, normalizedFieldToAstFields.build());
    }

    public CollectNFResult collectFromOperation(FieldCollectorNormalizedQueryParams parameters,
                                                OperationDefinition operationDefinition,
                                                GraphQLObjectType rootType) {


        Set<GraphQLObjectType> possibleObjects = new LinkedHashSet<>();
        possibleObjects.add(rootType);
        List<CollectedField> collectedFields = new ArrayList<>();
        collectFromSelectionSet2(parameters, operationDefinition.getSelectionSet(), collectedFields, rootType, possibleObjects);
        // group by result key
        Map<String, List<CollectedField>> fieldsByName = new LinkedHashMap<>();
        for (CollectedField collectedField : collectedFields) {
            fieldsByName.computeIfAbsent(collectedField.field.getResultKey(), ignored -> new ArrayList<>()).add(collectedField);
        }
        List<ExecutableNormalizedField> resultNFs = new ArrayList<>();
        ImmutableListMultimap.Builder<ExecutableNormalizedField, FieldAndAstParent> normalizedFieldToAstFields = ImmutableListMultimap.builder();

        createNFs(parameters, fieldsByName, resultNFs, normalizedFieldToAstFields, 1, null);

        return new CollectNFResult(resultNFs, normalizedFieldToAstFields.build());
    }

    private void createNFs(FieldCollectorNormalizedQueryParams parameters,
                           Map<String, List<CollectedField>> fieldsByName,
                           List<ExecutableNormalizedField> resultNFs,
                           ImmutableListMultimap.Builder<ExecutableNormalizedField, FieldAndAstParent> normalizedFieldToAstFields,
                           int level,
                           ExecutableNormalizedField parent) {
        for (String resultKey : fieldsByName.keySet()) {
            List<CollectedField> fieldsWithSameResultKey = fieldsByName.get(resultKey);
            List<CollectedFieldGroup> commonParentsGroups = groupByCommonParents(fieldsWithSameResultKey);
            for (CollectedFieldGroup sameParents : commonParentsGroups) {
                ExecutableNormalizedField nf = createNF(parameters, sameParents, level, parent);
                if (nf == null) {
                    continue;
                }
                for (CollectedField collectedField : sameParents.concreteFields) {
                    normalizedFieldToAstFields.put(nf, new FieldAndAstParent(collectedField.field, collectedField.astTypeCondition));
                }
                for (CollectedField collectedField : sameParents.abstractFields) {
                    normalizedFieldToAstFields.put(nf, new FieldAndAstParent(collectedField.field, collectedField.astTypeCondition));
                }
                resultNFs.add(nf);
            }
        }
    }

    private ExecutableNormalizedField createNF(FieldCollectorNormalizedQueryParams parameters,
                                               CollectedFieldGroup collectedFieldGroup,
                                               int level,
                                               ExecutableNormalizedField parent) {
        Field field;
        Set<GraphQLObjectType> objectTypes = new LinkedHashSet<>();
        if (collectedFieldGroup.concreteFields.size() > 0) {
            field = collectedFieldGroup.concreteFields.iterator().next().field;
            for (CollectedField concreteField : collectedFieldGroup.concreteFields) {
                objectTypes.addAll(concreteField.objectTypes);
            }
        } else {
            field = collectedFieldGroup.abstractFields.iterator().next().field;
            for (CollectedField collectedField : collectedFieldGroup.abstractFields) {
                objectTypes.addAll(collectedField.objectTypes);
            }
        }

        // ... then the intersection is generated with the abstract types
        if (objectTypes.size() == 0) {
            return null;
        }
        String fieldName = field.getName();
        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(parameters.getGraphQLSchema(), objectTypes.iterator().next(), fieldName);

//        if (result.containsKey(resultKey)) {
//            // can we merge it actually?
//            Collection<ExecutableNormalizedField> existingNFs = result.get(resultKey);
//            ExecutableNormalizedField matchingNF = findMatchingNF(parameters.getGraphQLSchema(), existingNFs, fieldDefinition, field.getArguments());
//            if (matchingNF != null) {
//                matchingNF.addObjectTypeNames(map(objectTypes, GraphQLObjectType::getName));
//                normalizedFieldToMergedField.put(matchingNF, field);
//                return;
//            }
//        }
        // this means we have no existing NF
        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDefinition.getArguments(), field.getArguments(), parameters.getCoercedVariableValues());
        Map<String, NormalizedInputValue> normalizedArgumentValues = null;
        if (parameters.getNormalizedVariableValues() != null) {
            normalizedArgumentValues = valuesResolver.getNormalizedArgumentValues(fieldDefinition.getArguments(), field.getArguments(), parameters.getNormalizedVariableValues());
        }
        ImmutableList<String> objectTypeNames = map(objectTypes, GraphQLObjectType::getName);
        ExecutableNormalizedField executableNormalizedField = ExecutableNormalizedField.newNormalizedField()
                .alias(field.getAlias())
                .resolvedArguments(argumentValues)
                .normalizedArguments(normalizedArgumentValues)
                .astArguments(field.getArguments())
                .objectTypeNames(objectTypeNames)
                .fieldName(fieldName)
                .level(level)
                .parent(parent)
                .build();

//        result.put(resultKey, executableNormalizedField);
        return executableNormalizedField;
//        normalizedFieldToMergedField.put(executableNormalizedField, field);
    }

    private static class CollectedFieldGroup {
        Set<CollectedField> concreteFields;
        Set<CollectedField> abstractFields;

        public CollectedFieldGroup(Set<CollectedField> concreteFields, Set<CollectedField> abstractFields) {
            this.concreteFields = concreteFields;
            this.abstractFields = abstractFields;
        }
    }

    private List<CollectedFieldGroup> groupByCommonParents(Collection<CollectedField> fields) {
        Set<CollectedField> abstractTypes = filterSet(fields, fieldAndType -> isInterfaceOrUnion(fieldAndType.astTypeCondition));
        Set<CollectedField> concreteTypes = filterSet(fields, fieldAndType -> isObjectType(fieldAndType.astTypeCondition));
        if (concreteTypes.isEmpty()) {
            CollectedFieldGroup collectedFieldGroup = new CollectedFieldGroup(concreteTypes, abstractTypes);
            return singletonList(collectedFieldGroup);
        }
        Map<GraphQLType, ImmutableList<CollectedField>> groupsByConcreteParent = groupingBy(concreteTypes, fieldAndType -> fieldAndType.astTypeCondition);
        List<CollectedFieldGroup> result = new ArrayList<>();
        Set<GraphQLObjectType> allObjectFromConcreteTypes = new LinkedHashSet<>();
        for (ImmutableList<CollectedField> concreteGroup : groupsByConcreteParent.values()) {
            for (CollectedField collectedField : concreteGroup) {
                allObjectFromConcreteTypes.addAll(collectedField.objectTypes);
            }
            CollectedFieldGroup collectedFieldGroup = new CollectedFieldGroup(new LinkedHashSet<>(concreteGroup), abstractTypes);
            result.add(collectedFieldGroup);
        }
        // checking if there are object types left which are not covered by concrete types
        Set<GraphQLObjectType> allObjectFromAbstractTypes = new LinkedHashSet<>();
        for (CollectedField collectedField : abstractTypes) {
            allObjectFromAbstractTypes.addAll(collectedField.objectTypes);
        }
        allObjectFromAbstractTypes.removeAll(allObjectFromConcreteTypes);
        if (allObjectFromAbstractTypes.size() > 0) {
            for (CollectedField collectedField : abstractTypes) {
                collectedField.objectTypes = allObjectFromAbstractTypes;
            }
            result.add(new CollectedFieldGroup(emptySet(), abstractTypes));
        }
        return result;
    }


//    private void collectFromSelectionSet(FieldCollectorNormalizedQueryParams parameters,
//                                         SelectionSet selectionSet,
//                                         Multimap<String, ExecutableNormalizedField> result,
//                                         ImmutableListMultimap.Builder<ExecutableNormalizedField, Field> mergedFieldByNormalizedField,
//                                         Set<GraphQLObjectType> possibleObjects,
//                                         int level,
//                                         ExecutableNormalizedField parent) {
//
//        for (Selection<?> selection : selectionSet.getSelections()) {
//            if (selection instanceof Field) {
//                collectField(parameters, result, mergedFieldByNormalizedField, (Field) selection, possibleObjects, level, parent);
//            } else if (selection instanceof InlineFragment) {
//                collectInlineFragment(parameters, result, mergedFieldByNormalizedField, (InlineFragment) selection, possibleObjects, level, parent);
//            } else if (selection instanceof FragmentSpread) {
//                collectFragmentSpread(parameters, result, mergedFieldByNormalizedField, (FragmentSpread) selection, possibleObjects, level, parent);
//            }
//        }
//    }

    private void collectFromSelectionSet2(FieldCollectorNormalizedQueryParams parameters,
                                          SelectionSet selectionSet,
                                          List<CollectedField> result,
                                          GraphQLCompositeType astTypeCondition,
                                          Set<GraphQLObjectType> possibleObjects
    ) {

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectField2(parameters, result, (Field) selection, possibleObjects, astTypeCondition);
            } else if (selection instanceof InlineFragment) {
                collectInlineFragment2(parameters, result, (InlineFragment) selection, possibleObjects, astTypeCondition);
            } else if (selection instanceof FragmentSpread) {
                collectFragmentSpread2(parameters, result, (FragmentSpread) selection, possibleObjects, astTypeCondition);
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

    private void collectFragmentSpread2(FieldCollectorNormalizedQueryParams parameters,
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
        collectFromSelectionSet2(parameters, fragmentDefinition.getSelectionSet(), result, newAstTypeCondition, newPossibleObjects);
    }


    private void collectInlineFragment2(FieldCollectorNormalizedQueryParams parameters,
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
        collectFromSelectionSet2(parameters, inlineFragment.getSelectionSet(), result, newAstTypeCondition, newPossibleObjects);
    }

    private void collectField2(FieldCollectorNormalizedQueryParams parameters,
                               List<CollectedField> result,
                               Field field,
                               Set<GraphQLObjectType> possibleObjectTypes,
                               GraphQLCompositeType astTypeCondition
    ) {
        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), field.getDirectives())) {
            return;
        }
        // this means there is actually no possible type for this field and we are done
        if (possibleObjectTypes.size() == 0) {
            return;
        }
        result.add(new CollectedField(field, possibleObjectTypes, astTypeCondition));
    }

//    private void collectFragmentSpread(FieldCollectorNormalizedQueryParams parameters,
//                                       Multimap<String, ExecutableNormalizedField> result,
//                                       ImmutableListMultimap.Builder<ExecutableNormalizedField, Field> mergedFieldByNormalizedField,
//                                       FragmentSpread fragmentSpread,
//                                       Set<GraphQLObjectType> possibleObjects,
//                                       int level,
//                                       ExecutableNormalizedField parent) {
//        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), fragmentSpread.getDirectives())) {
//            return;
//        }
//        FragmentDefinition fragmentDefinition = assertNotNull(parameters.getFragmentsByName().get(fragmentSpread.getName()));
//
//        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), fragmentDefinition.getDirectives())) {
//            return;
//        }
//        GraphQLCompositeType newCondition = (GraphQLCompositeType) parameters.getGraphQLSchema().getType(fragmentDefinition.getTypeCondition().getName());
//        Set<GraphQLObjectType> newConditions = narrowDownPossibleObjects(possibleObjects, newCondition, parameters.getGraphQLSchema());
//        collectFromSelectionSet(parameters, fragmentDefinition.getSelectionSet(), result, mergedFieldByNormalizedField, newConditions, level, parent);
//    }
//
//    private void collectInlineFragment(FieldCollectorNormalizedQueryParams parameters,
//                                       Multimap<String, ExecutableNormalizedField> result,
//                                       ImmutableListMultimap.Builder<ExecutableNormalizedField, Field> mergedFieldByNormalizedField,
//                                       InlineFragment inlineFragment,
//                                       Set<GraphQLObjectType> possibleObjects,
//                                       int level, ExecutableNormalizedField parent) {
//        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), inlineFragment.getDirectives())) {
//            return;
//        }
//        Set<GraphQLObjectType> newPossibleObjects = possibleObjects;
//
//        if (inlineFragment.getTypeCondition() != null) {
//            GraphQLCompositeType newCondition = (GraphQLCompositeType) parameters.getGraphQLSchema().getType(inlineFragment.getTypeCondition().getName());
//            newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newCondition, parameters.getGraphQLSchema());
//
//        }
//        collectFromSelectionSet(parameters, inlineFragment.getSelectionSet(), result, mergedFieldByNormalizedField, newPossibleObjects, level, parent);
//    }
//
//    private void collectField(FieldCollectorNormalizedQueryParams parameters,
//                              Multimap<String, ExecutableNormalizedField> result,
//                              ImmutableListMultimap.Builder<ExecutableNormalizedField, Field> normalizedFieldToMergedField,
//                              Field field,
//                              Set<GraphQLObjectType> objectTypes,
//                              int level,
//                              ExecutableNormalizedField parent) {
//        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), field.getDirectives())) {
//            return;
//        }
//        // this means there is actually no possible type for this field and we are done
//        if (objectTypes.size() == 0) {
//            return;
//        }
//        String resultKey = field.getResultKey();
//        String fieldName = field.getName();
//        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(parameters.getGraphQLSchema(), objectTypes.iterator().next(), fieldName);
//
//        if (result.containsKey(resultKey)) {
//            // can we merge it actually?
//            Collection<ExecutableNormalizedField> existingNFs = result.get(resultKey);
//            ExecutableNormalizedField matchingNF = findMatchingNF(parameters.getGraphQLSchema(), existingNFs, fieldDefinition, field.getArguments());
//            if (matchingNF != null) {
//                matchingNF.addObjectTypeNames(map(objectTypes, GraphQLObjectType::getName));
//                normalizedFieldToMergedField.put(matchingNF, field);
//                return;
//            }
//        }
//        // this means we have no existing NF
//        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDefinition.getArguments(), field.getArguments(), parameters.getCoercedVariableValues());
//        Map<String, NormalizedInputValue> normalizedArgumentValues = null;
//        if (parameters.getNormalizedVariableValues() != null) {
//            normalizedArgumentValues = valuesResolver.getNormalizedArgumentValues(fieldDefinition.getArguments(), field.getArguments(), parameters.getNormalizedVariableValues());
//        }
//        ImmutableList<String> objectTypeNames = map(objectTypes, GraphQLObjectType::getName);
//        ExecutableNormalizedField executableNormalizedField = ExecutableNormalizedField.newNormalizedField()
//                .alias(field.getAlias())
//                .resolvedArguments(argumentValues)
//                .normalizedArguments(normalizedArgumentValues)
//                .astArguments(field.getArguments())
//                .objectTypeNames(objectTypeNames)
//                .fieldName(fieldName)
//                .level(level)
//                .parent(parent)
//                .build();
//
//        result.put(resultKey, executableNormalizedField);
//        normalizedFieldToMergedField.put(executableNormalizedField, field);
//    }

    private ExecutableNormalizedField findMatchingNF(GraphQLSchema schema, Collection<ExecutableNormalizedField> executableNormalizedFields, GraphQLFieldDefinition fieldDefinition, List<Argument> arguments) {
        for (ExecutableNormalizedField nf : executableNormalizedFields) {
            GraphQLFieldDefinition nfFieldDefinition = nf.getOneFieldDefinition(schema);
            // same field name
            if (!nfFieldDefinition.getName().equals(fieldDefinition.getName())) {
                continue;
            }
            // same type
            if (!simplePrint(nfFieldDefinition.getType()).equals(simplePrint(fieldDefinition.getType()))) {
                continue;
            }
            // same arguments
            if (!sameArguments(nf.getAstArguments(), arguments)) {
                continue;
            }
            return nf;
        }
        return null;
    }

    // copied from graphql.validation.rules.OverlappingFieldsCanBeMerged
    private boolean sameArguments(List<Argument> arguments1, List<Argument> arguments2) {
        if (arguments1.size() != arguments2.size()) {
            return false;
        }
        for (Argument argument : arguments1) {
            Argument matchedArgument = findArgumentByName(argument.getName(), arguments2);
            if (matchedArgument == null) {
                return false;
            }
            if (!AstComparator.sameValue(argument.getValue(), matchedArgument.getValue())) {
                return false;
            }
        }
        return true;
    }

    private Argument findArgumentByName(String name, List<Argument> arguments) {
        for (Argument argument : arguments) {
            if (argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }


    private Set<GraphQLObjectType> narrowDownPossibleObjects(Set<GraphQLObjectType> currentOnes,
                                                             GraphQLCompositeType typeCondition,
                                                             GraphQLSchema graphQLSchema) {

        ImmutableSet<GraphQLObjectType> resolvedTypeCondition = resolvePossibleObjects(typeCondition, graphQLSchema);
        if (currentOnes.size() == 0) {
            return resolvedTypeCondition;
        }
        return Sets.intersection(currentOnes, resolvedTypeCondition);
    }

    private ImmutableSet<GraphQLObjectType> resolvePossibleObjects(GraphQLCompositeType type, GraphQLSchema graphQLSchema) {
        if (type instanceof GraphQLObjectType) {
            return ImmutableSet.of((GraphQLObjectType) type);
        } else if (type instanceof GraphQLInterfaceType) {
            return ImmutableSet.copyOf(graphQLSchema.getImplementations((GraphQLInterfaceType) type));
        } else if (type instanceof GraphQLUnionType) {
            List types = ((GraphQLUnionType) type).getTypes();
            return ImmutableSet.copyOf((types));
        } else {
            return assertShouldNeverHappen();
        }

    }

}
