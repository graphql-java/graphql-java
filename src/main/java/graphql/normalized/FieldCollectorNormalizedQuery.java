package graphql.normalized;


import graphql.Assert;
import graphql.Internal;
import graphql.execution.ConditionalNodes;
import graphql.execution.MergedField;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertTrue;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;


// special version of FieldCollector to track all possible types per field
@Internal
public class FieldCollectorNormalizedQuery {

    private final ConditionalNodes conditionalNodes = new ConditionalNodes();

    public List<NormalizedQueryField> collectFields(FieldCollectorNormalizedQueryParams parameters, NormalizedQueryField normalizedQueryField) {
        GraphQLUnmodifiedType fieldType = GraphQLTypeUtil.unwrapAll(normalizedQueryField.getFieldDefinition().getType());
        // if not composite we don't have any selectionSet because it is a Scalar or enum
        if (!(fieldType instanceof GraphQLCompositeType)) {
            return Collections.emptyList();
        }

        Map<String, Map<GraphQLObjectType, NormalizedQueryField>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        Set<GraphQLObjectType> possibleObjects
                = new LinkedHashSet<>(resolvePossibleObjects((GraphQLCompositeType) fieldType, parameters.getGraphQLSchema()));
        for (Field field : normalizedQueryField.getMergedField().getFields()) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            this.collectFields(parameters,
                    field.getSelectionSet(),
                    visitedFragments,
                    subFields,
                    possibleObjects,
                    normalizedQueryField.getFieldDefinition().getType());
        }
        List<NormalizedQueryField> result = new ArrayList<>();
        subFields.values().forEach(setMergedFieldWTCMap -> {
            result.addAll(setMergedFieldWTCMap.values());
        });
        return result;
    }

    public List<NormalizedQueryField> collectFromOperation(FieldCollectorNormalizedQueryParams parameters,
                                                           OperationDefinition operationDefinition,
                                                           GraphQLObjectType rootType) {
        Map<String, Map<GraphQLObjectType, NormalizedQueryField>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        Set<GraphQLObjectType> possibleObjects = new LinkedHashSet<>();
        possibleObjects.add(rootType);
        this.collectFields(parameters, operationDefinition.getSelectionSet(), visitedFragments, subFields, possibleObjects, rootType);
        List<NormalizedQueryField> result = new ArrayList<>();
        subFields.values().forEach(setMergedFieldWTCMap -> {
            result.addAll(setMergedFieldWTCMap.values());
        });
        return result;
    }


    private void collectFields(FieldCollectorNormalizedQueryParams parameters,
                               SelectionSet selectionSet,
                               List<String> visitedFragments,
                               Map<String, Map<GraphQLObjectType, NormalizedQueryField>> result,
                               Set<GraphQLObjectType> possibleObjects,
                               GraphQLOutputType parentType) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectField(parameters, result, (Field) selection, possibleObjects, parentType);
            } else if (selection instanceof InlineFragment) {
                collectInlineFragment(parameters, visitedFragments, result, (InlineFragment) selection, possibleObjects, parentType);
            } else if (selection instanceof FragmentSpread) {
                collectFragmentSpread(parameters, visitedFragments, result, (FragmentSpread) selection, possibleObjects, parentType);
            }
        }
    }

    private void collectFragmentSpread(FieldCollectorNormalizedQueryParams parameters,
                                       List<String> visitedFragments,
                                       Map<String, Map<GraphQLObjectType, NormalizedQueryField>> result,
                                       FragmentSpread fragmentSpread,
                                       Set<GraphQLObjectType> possibleObjects,
                                       GraphQLOutputType parentType) {
        if (visitedFragments.contains(fragmentSpread.getName())) {
            return;
        }
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), fragmentSpread.getDirectives())) {
            return;
        }
        visitedFragments.add(fragmentSpread.getName());
        FragmentDefinition fragmentDefinition = Assert.assertNotNull(parameters.getFragmentsByName().get(fragmentSpread.getName()));

        if (!conditionalNodes.shouldInclude(parameters.getVariables(), fragmentDefinition.getDirectives())) {
            return;
        }
        GraphQLCompositeType newCondition = (GraphQLCompositeType) parameters.getGraphQLSchema().getType(fragmentDefinition.getTypeCondition().getName());
        Set<GraphQLObjectType> newConditions = narrowDownPossibleObjects(possibleObjects, newCondition, parameters.getGraphQLSchema());
        GraphQLCompositeType newParentType = (GraphQLCompositeType)
                Assert.assertNotNull(parameters.getGraphQLSchema().getType(fragmentDefinition.getTypeCondition().getName()));
        collectFields(parameters, fragmentDefinition.getSelectionSet(), visitedFragments, result, newConditions, newParentType);
    }

    private void collectInlineFragment(FieldCollectorNormalizedQueryParams parameters,
                                       List<String> visitedFragments,
                                       Map<String, Map<GraphQLObjectType, NormalizedQueryField>> result,
                                       InlineFragment inlineFragment,
                                       Set<GraphQLObjectType> possibleObjects,
                                       GraphQLOutputType parentType) {
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), inlineFragment.getDirectives())) {
            return;
        }
        Set<GraphQLObjectType> newPossibleOjects = possibleObjects;
        GraphQLOutputType newParentType = parentType;

        if (inlineFragment.getTypeCondition() != null) {
            GraphQLCompositeType newCondition = (GraphQLCompositeType) parameters.getGraphQLSchema().getType(inlineFragment.getTypeCondition().getName());
            newPossibleOjects = narrowDownPossibleObjects(possibleObjects, newCondition, parameters.getGraphQLSchema());
            newParentType = (GraphQLCompositeType)
                    Assert.assertNotNull(parameters.getGraphQLSchema().getType(inlineFragment.getTypeCondition().getName()));

        }
        collectFields(parameters, inlineFragment.getSelectionSet(), visitedFragments, result, newPossibleOjects, newParentType);
    }

    private void collectField(FieldCollectorNormalizedQueryParams parameters,
                              Map<String, Map<GraphQLObjectType, NormalizedQueryField>> result,
                              Field field,
                              Set<GraphQLObjectType> objectTypes,
                              GraphQLOutputType parentType) {
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), field.getDirectives())) {
            return;
        }
        String name = getFieldEntryKey(field);
        result.computeIfAbsent(name, ignored -> new LinkedHashMap<>());
        Map<GraphQLObjectType, NormalizedQueryField> existingFieldWTC = result.get(name);
        for (GraphQLObjectType objectType : objectTypes) {
            if (existingFieldWTC.containsKey(objectType)) {
                NormalizedQueryField normalizedQueryField = existingFieldWTC.get(objectType);
                existingFieldWTC.put(objectType, normalizedQueryField.transform(builder -> {
                    MergedField mergedField = normalizedQueryField.getMergedField().transform(mergedFieldBuilder -> mergedFieldBuilder.addField(field));
                    builder.mergedField(mergedField);
                }));
            } else {
                GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) GraphQLTypeUtil.unwrapAll(parentType);
                NormalizedQueryField newFieldWTC = NormalizedQueryField.newQueryExecutionField(field)
                        .objectType(objectType)
                        .fieldDefinition(getFieldDefinition(fieldsContainer, field.getName()))
                        .fieldsContainer(fieldsContainer)
                        .parentType(parentType)
                        .build();
                existingFieldWTC.put(objectType, newFieldWTC);
            }
        }
    }


    private GraphQLFieldDefinition getFieldDefinition(GraphQLCompositeType parentType, String fieldName) {

        if (fieldName.equals(TypeNameMetaFieldDef.getName())) {
            return TypeNameMetaFieldDef;
        }
        assertTrue(parentType instanceof GraphQLFieldsContainer, "should not happen : parent type must be an object or interface %s", parentType);
        GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) parentType;
        GraphQLFieldDefinition fieldDefinition = fieldsContainer.getFieldDefinition(fieldName);
        Assert.assertTrue(fieldDefinition != null, "Unknown field '%s'", fieldName);
        return fieldDefinition;
    }


    private String getFieldEntryKey(Field field) {
        if (field.getAlias() != null) {
            return field.getAlias();
        } else {
            return field.getName();
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
