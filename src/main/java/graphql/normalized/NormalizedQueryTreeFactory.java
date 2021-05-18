package graphql.normalized;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import graphql.Assert;
import graphql.Internal;
import graphql.execution.ConditionalNodes;
import graphql.execution.MergedField;
import graphql.execution.ValuesResolver;
import graphql.execution.nextgen.Common;
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
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertNotNull;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;

@Internal
public class NormalizedQueryTreeFactory {

    private final ValuesResolver valuesResolver = new ValuesResolver();
    private final ConditionalNodes conditionalNodes = new ConditionalNodes();

    public static NormalizedQueryTree createNormalizedQuery(GraphQLSchema graphQLSchema,
                                                            Document document,
                                                            String operationName,
                                                            Map<String, Object> coercedVariableValues) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operationName);
        return new NormalizedQueryTreeFactory().createNormalizedQueryImpl(graphQLSchema, getOperationResult.operationDefinition, getOperationResult.fragmentsByName, coercedVariableValues, null);
    }


    public static NormalizedQueryTree createNormalizedQuery(GraphQLSchema graphQLSchema,
                                                            OperationDefinition operationDefinition,
                                                            Map<String, FragmentDefinition> fragments,
                                                            Map<String, Object> coercedVariableValues) {
        return new NormalizedQueryTreeFactory().createNormalizedQueryImpl(graphQLSchema, operationDefinition, fragments, coercedVariableValues, null);
    }

    public static NormalizedQueryTree createNormalizedQueryWithRawVariables(GraphQLSchema graphQLSchema,
                                                                            Document document,
                                                                            String operationName,
                                                                            Map<String, Object> rawVariables) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operationName);
        return new NormalizedQueryTreeFactory().createNormalizedQueryImplWithRawVariables(graphQLSchema, getOperationResult.operationDefinition, getOperationResult.fragmentsByName, rawVariables);
    }

    private NormalizedQueryTree createNormalizedQueryImplWithRawVariables(GraphQLSchema graphQLSchema,
                                                                          OperationDefinition operationDefinition,
                                                                          Map<String, FragmentDefinition> fragments,
                                                                          Map<String, Object> rawVariables
    ) {

        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();
        Map<String, Object> coerceVariableValues = valuesResolver.coerceVariableValues(graphQLSchema, variableDefinitions, rawVariables);
        Map<String, NormalizedInputValue> normalizedVariableValues = valuesResolver.coerceNormalizedVariableValues(graphQLSchema, variableDefinitions, rawVariables);
        return createNormalizedQueryImpl(graphQLSchema, operationDefinition, fragments, coerceVariableValues, normalizedVariableValues);
    }

    /**
     * Creates a new Normalized query tree for the provided query
     */
    private NormalizedQueryTree createNormalizedQueryImpl(GraphQLSchema graphQLSchema,
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

        CollectFieldResult topLevelFields = collectFromOperation(parameters, operationDefinition, rootType);

        ImmutableListMultimap.Builder<Field, NormalizedField> fieldToNormalizedField = ImmutableListMultimap.builder();
        ImmutableMap.Builder<NormalizedField, MergedField> normalizedFieldToMergedField = ImmutableMap.builder();
        ImmutableListMultimap.Builder<FieldCoordinates, NormalizedField> coordinatesToNormalizedFields = ImmutableListMultimap.builder();

        List<NormalizedField> realRoots = new ArrayList<>();

        for (NormalizedField topLevel : topLevelFields.children) {

            ImmutableList<Field> mergedField = topLevelFields.normalizedFieldToAstFields.get(topLevel);
            NormalizedField realTopLevel = buildFieldWithChildren(topLevel, mergedField, parameters, fieldToNormalizedField, normalizedFieldToMergedField, coordinatesToNormalizedFields, 1);
            fixUpParentReference(realTopLevel);

            normalizedFieldToMergedField.put(realTopLevel, MergedField.newMergedField(mergedField).build());
            FieldCoordinates coordinates = FieldCoordinates.coordinates(realTopLevel.getObjectType(), realTopLevel.getFieldDefinition());
            coordinatesToNormalizedFields.put(coordinates, realTopLevel);
            updateByAstFieldMap(realTopLevel, mergedField, fieldToNormalizedField);
            realRoots.add(realTopLevel);
        }
        return new NormalizedQueryTree(realRoots, fieldToNormalizedField.build(), normalizedFieldToMergedField.build(), coordinatesToNormalizedFields.build());
    }

    private void fixUpParentReference(NormalizedField rootNormalizedField) {
        for (NormalizedField child : rootNormalizedField.getChildren()) {
            child.replaceParent(rootNormalizedField);
        }
    }


    private NormalizedField buildFieldWithChildren(NormalizedField field,
                                                   ImmutableList<Field> mergedField,
                                                   FieldCollectorNormalizedQueryParams fieldCollectorNormalizedQueryParams,
                                                   ImmutableListMultimap.Builder<Field, NormalizedField> fieldNormalizedField,
                                                   ImmutableMap.Builder<NormalizedField, MergedField> normalizedFieldToMergedField,
                                                   ImmutableListMultimap.Builder<FieldCoordinates, NormalizedField> coordinatesToNormalizedFields,
                                                   int curLevel) {

        CollectFieldResult fieldsWithoutChildren = collectFields(fieldCollectorNormalizedQueryParams, field, mergedField, curLevel + 1);
        List<NormalizedField> realChildren = new ArrayList<>();
        for (NormalizedField fieldWithoutChildren : fieldsWithoutChildren.children) {

            ImmutableList<Field> mergedFieldForChild = fieldsWithoutChildren.normalizedFieldToAstFields.get(fieldWithoutChildren);
            NormalizedField realChild = buildFieldWithChildren(fieldWithoutChildren, mergedFieldForChild, fieldCollectorNormalizedQueryParams, fieldNormalizedField, normalizedFieldToMergedField, coordinatesToNormalizedFields, curLevel + 1);
            fixUpParentReference(realChild);

            normalizedFieldToMergedField.put(realChild, MergedField.newMergedField(mergedFieldForChild).build());
            FieldCoordinates coordinates = FieldCoordinates.coordinates(realChild.getObjectType(), realChild.getFieldDefinition());
            coordinatesToNormalizedFields.put(coordinates, realChild);

            realChildren.add(realChild);

            updateByAstFieldMap(realChild, mergedFieldForChild, fieldNormalizedField);
        }
        return field.transform(builder -> builder.children(realChildren));
    }

    private void updateByAstFieldMap(NormalizedField normalizedField,
                                     ImmutableList<Field> mergedField,
                                     ImmutableListMultimap.Builder<Field, NormalizedField> fieldToNormalizedField) {
        for (Field astField : mergedField) {
//            if (fieldToNormalizedField.build().get(astField).contains(normalizedField)) {
//                System.out.println("already in: " + normalizedField);
//            }
            fieldToNormalizedField.put(astField, normalizedField);
        }
    }

    public static class CollectFieldResult {
        private final Collection<NormalizedField> children;
        private final ImmutableListMultimap<NormalizedField, Field> normalizedFieldToAstFields;

        public CollectFieldResult(Collection<NormalizedField> children, ImmutableListMultimap<NormalizedField, Field> normalizedFieldToAstFields) {
            this.children = children;
            this.normalizedFieldToAstFields = normalizedFieldToAstFields;
        }
    }


    public CollectFieldResult collectFields(FieldCollectorNormalizedQueryParams parameters,
                                            NormalizedField normalizedField,
                                            ImmutableList<Field> mergedField,
                                            int level) {
        GraphQLUnmodifiedType fieldType = unwrapAll(normalizedField.getFieldDefinition().getType());
        // if not composite we don't have any selectionSet because it is a Scalar or enum
        if (!(fieldType instanceof GraphQLCompositeType)) {
            return new CollectFieldResult(Collections.emptyList(), ImmutableListMultimap.of());
        }

        // result key -> ObjectType -> NormalizedField
        Table<String, GraphQLObjectType, NormalizedField> subFields = HashBasedTable.create();
        ImmutableListMultimap.Builder<NormalizedField, Field> mergedFieldByNormalizedField = ImmutableListMultimap.builder();
        Set<GraphQLObjectType> possibleObjects
                = new LinkedHashSet<>(resolvePossibleObjects((GraphQLCompositeType) fieldType, parameters.getGraphQLSchema()));
        for (Field field : mergedField) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            this.collectFields(parameters,
                    field.getSelectionSet(),
                    subFields,
                    mergedFieldByNormalizedField,
                    possibleObjects,
                    level,
                    normalizedField);
        }
        return new CollectFieldResult(subFields.values(), mergedFieldByNormalizedField.build());
    }

    public CollectFieldResult collectFromOperation(FieldCollectorNormalizedQueryParams parameters,
                                                   OperationDefinition operationDefinition,
                                                   GraphQLObjectType rootType) {
        Table<String, GraphQLObjectType, NormalizedField> subFields = HashBasedTable.create();
        ImmutableListMultimap.Builder<NormalizedField, Field> normalizedFieldToAstFields = ImmutableListMultimap.builder();
        Set<GraphQLObjectType> possibleObjects = new LinkedHashSet<>();
        possibleObjects.add(rootType);
        this.collectFields(parameters, operationDefinition.getSelectionSet(), subFields, normalizedFieldToAstFields, possibleObjects, 1, null);
        return new CollectFieldResult(subFields.values(), normalizedFieldToAstFields.build());
    }


    private void collectFields(FieldCollectorNormalizedQueryParams parameters,
                               SelectionSet selectionSet,
                               Table<String, GraphQLObjectType, NormalizedField> result,
                               ImmutableListMultimap.Builder<NormalizedField, Field> mergedFieldByNormalizedField,
                               Set<GraphQLObjectType> possibleObjects,
                               int level,
                               NormalizedField parent) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectField(parameters, result, mergedFieldByNormalizedField, (Field) selection, possibleObjects, level, parent);
            } else if (selection instanceof InlineFragment) {
                collectInlineFragment(parameters, result, mergedFieldByNormalizedField, (InlineFragment) selection, possibleObjects, level, parent);
            } else if (selection instanceof FragmentSpread) {
                collectFragmentSpread(parameters, result, mergedFieldByNormalizedField, (FragmentSpread) selection, possibleObjects, level, parent);
            }
        }
    }

    private void collectFragmentSpread(FieldCollectorNormalizedQueryParams parameters,
                                       Table<String, GraphQLObjectType, NormalizedField> result,
                                       ImmutableListMultimap.Builder<NormalizedField, Field> mergedFieldByNormalizedField,
                                       FragmentSpread fragmentSpread,
                                       Set<GraphQLObjectType> possibleObjects,
                                       int level,
                                       NormalizedField parent) {
        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), fragmentSpread.getDirectives())) {
            return;
        }
        FragmentDefinition fragmentDefinition = assertNotNull(parameters.getFragmentsByName().get(fragmentSpread.getName()));

        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), fragmentDefinition.getDirectives())) {
            return;
        }
        GraphQLCompositeType newCondition = (GraphQLCompositeType) parameters.getGraphQLSchema().getType(fragmentDefinition.getTypeCondition().getName());
        Set<GraphQLObjectType> newConditions = narrowDownPossibleObjects(possibleObjects, newCondition, parameters.getGraphQLSchema());
        collectFields(parameters, fragmentDefinition.getSelectionSet(), result, mergedFieldByNormalizedField, newConditions, level, parent);
    }

    private void collectInlineFragment(FieldCollectorNormalizedQueryParams parameters,
                                       Table<String, GraphQLObjectType, NormalizedField> result,
                                       ImmutableListMultimap.Builder<NormalizedField, Field> mergedFieldByNormalizedField,
                                       InlineFragment inlineFragment,
                                       Set<GraphQLObjectType> possibleObjects,
                                       int level, NormalizedField parent) {
        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), inlineFragment.getDirectives())) {
            return;
        }
        Set<GraphQLObjectType> newPossibleObjects = possibleObjects;

        if (inlineFragment.getTypeCondition() != null) {
            GraphQLCompositeType newCondition = (GraphQLCompositeType) parameters.getGraphQLSchema().getType(inlineFragment.getTypeCondition().getName());
            newPossibleObjects = narrowDownPossibleObjects(possibleObjects, newCondition, parameters.getGraphQLSchema());

        }
        collectFields(parameters, inlineFragment.getSelectionSet(), result, mergedFieldByNormalizedField, newPossibleObjects, level, parent);
    }

    private void collectField(FieldCollectorNormalizedQueryParams parameters,
                              Table<String, GraphQLObjectType, NormalizedField> result,
                              ImmutableListMultimap.Builder<NormalizedField, Field> fieldsByNormalizedField,
                              Field field,
                              Set<GraphQLObjectType> objectTypes,
                              int level,
                              NormalizedField parent) {
        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), field.getDirectives())) {
            return;
        }
        String name = field.getResultKey();
        Map<GraphQLObjectType, NormalizedField> existingNormalizedFields = result.row(name);

        for (GraphQLObjectType objectType : objectTypes) {
            if (existingNormalizedFields.containsKey(objectType)) {
                NormalizedField normalizedField = existingNormalizedFields.get(objectType);
                fieldsByNormalizedField.put(normalizedField, field);
            } else {
                GraphQLFieldDefinition fieldDefinition;
                GraphQLSchema schema = parameters.getGraphQLSchema();
                // get field definition while considering special fields
                if (field.getName().equals(schema.getIntrospectionTypenameFieldDefinition().getName())) {
                    fieldDefinition = schema.getIntrospectionTypenameFieldDefinition();
                } else {
                    if (field.getName().equals(schema.getIntrospectionSchemaFieldDefinition().getName())) {
                        fieldDefinition = schema.getIntrospectionSchemaFieldDefinition();
                    } else if (field.getName().equals(schema.getIntrospectionTypeFieldDefinition().getName())) {
                        fieldDefinition = schema.getIntrospectionTypeFieldDefinition();
                    } else {
                        fieldDefinition = assertNotNull(objectType.getFieldDefinition(field.getName()), () -> String.format("no field with name %s found in object %s", field.getName(), objectType.getName()));
                    }
                }

                Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDefinition.getArguments(), field.getArguments(), parameters.getCoercedVariableValues());
                Map<String, NormalizedInputValue> normalizedArgumentValues = null;
                if (parameters.getNormalizedVariableValues() != null) {
                    normalizedArgumentValues = valuesResolver.getNormalizedArgumentValues(fieldDefinition.getArguments(), field.getArguments(), parameters.getCoercedVariableValues(), parameters.getNormalizedVariableValues());
                }
                NormalizedField normalizedField = NormalizedField.newQueryExecutionField()
                        .alias(field.getAlias())
                        .arguments(argumentValues)
                        .normalizedArguments(normalizedArgumentValues)
                        .objectType(objectType)
                        .fieldDefinition(fieldDefinition)
                        .level(level)
                        .parent(parent)
                        .build();
                System.out.println("new " + normalizedField);
                existingNormalizedFields.put(objectType, normalizedField);
                fieldsByNormalizedField.put(normalizedField, field);
            }
        }
    }

    private Set<GraphQLObjectType> narrowDownPossibleObjects(Set<GraphQLObjectType> currentOnes,
                                                             GraphQLCompositeType typeCondition,
                                                             GraphQLSchema graphQLSchema) {

        List<GraphQLObjectType> resolvedTypeCondition = resolvePossibleObjects(typeCondition, graphQLSchema);
        if (currentOnes.size() == 0) {
            return new LinkedHashSet<>(resolvedTypeCondition);
        }

        Set<GraphQLObjectType> result = new LinkedHashSet<>(currentOnes);
        result.retainAll(resolvedTypeCondition);
        return result;
    }

    private List<GraphQLObjectType> resolvePossibleObjects(GraphQLCompositeType type, GraphQLSchema graphQLSchema) {
        if (type instanceof GraphQLObjectType) {
            return Collections.singletonList((GraphQLObjectType) type);
        } else if (type instanceof GraphQLInterfaceType) {
            return graphQLSchema.getImplementations((GraphQLInterfaceType) type);
        } else if (type instanceof GraphQLUnionType) {
            List types = ((GraphQLUnionType) type).getTypes();
            return new ArrayList<GraphQLObjectType>(types);
        } else {
            return Assert.assertShouldNeverHappen();
        }

    }

}
