package graphql.normalized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import graphql.Assert;
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
import static graphql.collect.ImmutableKit.map;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
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
            for (String objectType : realTopLevel.getObjectTypeNames()) {
                FieldCoordinates coordinates = FieldCoordinates.coordinates(objectType, realTopLevel.getFieldName());
                coordinatesToNormalizedFields.put(coordinates, realTopLevel);
            }
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

        CollectFieldResult fieldsWithoutChildren = collectFromMergedField(fieldCollectorNormalizedQueryParams, field, mergedField, curLevel + 1);
        List<NormalizedField> realChildren = new ArrayList<>();
        for (NormalizedField fieldWithoutChildren : fieldsWithoutChildren.children) {

            ImmutableList<Field> mergedFieldForChild = fieldsWithoutChildren.normalizedFieldToAstFields.get(fieldWithoutChildren);
            NormalizedField realChild = buildFieldWithChildren(fieldWithoutChildren, mergedFieldForChild, fieldCollectorNormalizedQueryParams, fieldNormalizedField, normalizedFieldToMergedField, coordinatesToNormalizedFields, curLevel + 1);
            fixUpParentReference(realChild);

            normalizedFieldToMergedField.put(realChild, MergedField.newMergedField(mergedFieldForChild).build());
            for (String objectType : realChild.getObjectTypeNames()) {
                FieldCoordinates coordinates = FieldCoordinates.coordinates(objectType, realChild.getFieldName());
                coordinatesToNormalizedFields.put(coordinates, realChild);
            }

            realChildren.add(realChild);

            updateByAstFieldMap(realChild, mergedFieldForChild, fieldNormalizedField);
        }
        return field.transform(builder -> builder.children(realChildren));
    }

    private void updateByAstFieldMap(NormalizedField normalizedField,
                                     ImmutableList<Field> mergedField,
                                     ImmutableListMultimap.Builder<Field, NormalizedField> fieldToNormalizedField) {
        for (Field astField : mergedField) {
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


    public CollectFieldResult collectFromMergedField(FieldCollectorNormalizedQueryParams parameters,
                                                     NormalizedField normalizedField,
                                                     ImmutableList<Field> mergedField,
                                                     int level) {
        GraphQLUnmodifiedType fieldType = unwrapAll(normalizedField.getType(parameters.getGraphQLSchema()));
        // if not composite we don't have any selectionSet because it is a Scalar or enum
        if (!(fieldType instanceof GraphQLCompositeType)) {
            return new CollectFieldResult(Collections.emptyList(), ImmutableListMultimap.of());
        }

        Multimap<String, NormalizedField> subFields = LinkedHashMultimap.create();
        ImmutableListMultimap.Builder<NormalizedField, Field> mergedFieldByNormalizedField = ImmutableListMultimap.builder();
        Set<GraphQLObjectType> possibleObjects = resolvePossibleObjects((GraphQLCompositeType) fieldType, parameters.getGraphQLSchema());
        for (Field field : mergedField) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            this.collectFromSelectionSet(parameters,
                    field.getSelectionSet(),
                    subFields,
                    mergedFieldByNormalizedField,
                    possibleObjects,
                    level,
                    normalizedField);
        }
        return new CollectFieldResult(new LinkedHashSet<>(subFields.values()), mergedFieldByNormalizedField.build());
    }

    public CollectFieldResult collectFromOperation(FieldCollectorNormalizedQueryParams parameters,
                                                   OperationDefinition operationDefinition,
                                                   GraphQLObjectType rootType) {

        Multimap<String, NormalizedField> subFields = LinkedHashMultimap.create();
        ImmutableListMultimap.Builder<NormalizedField, Field> normalizedFieldToAstFields = ImmutableListMultimap.builder();
        Set<GraphQLObjectType> possibleObjects = new LinkedHashSet<>();
        possibleObjects.add(rootType);
        this.collectFromSelectionSet(parameters, operationDefinition.getSelectionSet(), subFields, normalizedFieldToAstFields, possibleObjects, 1, null);
        return new CollectFieldResult(subFields.values(), normalizedFieldToAstFields.build());
    }


    private void collectFromSelectionSet(FieldCollectorNormalizedQueryParams parameters,
                                         SelectionSet selectionSet,
                                         Multimap<String, NormalizedField> result,
                                         ImmutableListMultimap.Builder<NormalizedField, Field> mergedFieldByNormalizedField,
                                         Set<GraphQLObjectType> possibleObjects,
                                         int level,
                                         NormalizedField parent) {

        for (Selection<?> selection : selectionSet.getSelections()) {
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
                                       Multimap<String, NormalizedField> result,
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
        collectFromSelectionSet(parameters, fragmentDefinition.getSelectionSet(), result, mergedFieldByNormalizedField, newConditions, level, parent);
    }

    private void collectInlineFragment(FieldCollectorNormalizedQueryParams parameters,
                                       Multimap<String, NormalizedField> result,
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
        collectFromSelectionSet(parameters, inlineFragment.getSelectionSet(), result, mergedFieldByNormalizedField, newPossibleObjects, level, parent);
    }

    private void collectField(FieldCollectorNormalizedQueryParams parameters,
                              Multimap<String, NormalizedField> result,
                              ImmutableListMultimap.Builder<NormalizedField, Field> fieldsByNormalizedField,
                              Field field,
                              Set<GraphQLObjectType> objectTypes,
                              int level,
                              NormalizedField parent) {
        if (!conditionalNodes.shouldInclude(parameters.getCoercedVariableValues(), field.getDirectives())) {
            return;
        }
        // this means there is actually no possible type for this field and we are done
        if (objectTypes.size() == 0) {
            return;
        }
        String resultKey = field.getResultKey();
        String fieldName = field.getName();
        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(parameters.getGraphQLSchema(), objectTypes.iterator().next(), fieldName);

        if (result.containsKey(resultKey)) {
            Collection<NormalizedField> existingNFs = result.get(resultKey);
            NormalizedField matchingNF = findMatchingNF(parameters.getGraphQLSchema(), existingNFs, fieldDefinition, field.getArguments());
            if (matchingNF != null) {
                matchingNF.addObjectTypeNames(map(objectTypes, GraphQLObjectType::getName));
                fieldsByNormalizedField.put(matchingNF, field);
                return;
            }
        }
        // this means we have no existing NF
        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDefinition.getArguments(), field.getArguments(), parameters.getCoercedVariableValues());
        Map<String, NormalizedInputValue> normalizedArgumentValues = null;
        if (parameters.getNormalizedVariableValues() != null) {
            normalizedArgumentValues = valuesResolver.getNormalizedArgumentValues(fieldDefinition.getArguments(), field.getArguments(), parameters.getCoercedVariableValues(), parameters.getNormalizedVariableValues());
        }
        ImmutableList<String> objectTypeNames = map(objectTypes, GraphQLObjectType::getName);
        NormalizedField normalizedField = NormalizedField.newQueryExecutionField()
                .alias(field.getAlias())
                .resolvedArguments(argumentValues)
                .normalizedArguments(normalizedArgumentValues)
                .astArguments(field.getArguments())
                .objectTypeNames(objectTypeNames)
                .fieldName(fieldName)
                .level(level)
                .parent(parent)
                .build();

        result.put(resultKey, normalizedField);
        fieldsByNormalizedField.put(normalizedField, field);
    }

    private NormalizedField findMatchingNF(GraphQLSchema schema, Collection<NormalizedField> normalizedFields, GraphQLFieldDefinition fieldDefinition, List<Argument> arguments) {
        for (NormalizedField nf : normalizedFields) {
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
            return Assert.assertShouldNeverHappen();
        }

    }

}
