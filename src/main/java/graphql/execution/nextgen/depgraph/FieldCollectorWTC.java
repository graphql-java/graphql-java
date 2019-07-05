package graphql.execution.nextgen.depgraph;


import graphql.Assert;
import graphql.Internal;
import graphql.execution.ConditionalNodes;
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


@Internal
public class FieldCollectorWTC {

    private final ConditionalNodes conditionalNodes = new ConditionalNodes();

    public List<MergedFieldWTC> collectFields(FieldCollectorParameters parameters, MergedFieldWTC mergedField) {
        Map<String, Map<GraphQLObjectType, MergedFieldWTC>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        GraphQLUnmodifiedType fieldType = GraphQLTypeUtil.unwrapAll(mergedField.getFieldDefinition().getType());
        Set<GraphQLObjectType> typeConditions = new LinkedHashSet<>();
        if (fieldType instanceof GraphQLCompositeType) {
            typeConditions.addAll(resolveType((GraphQLCompositeType) fieldType, parameters.getGraphQLSchema()));
        }
        for (Field field : mergedField.getFields()) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            this.collectFields(parameters,
                    field.getSelectionSet(),
                    visitedFragments,
                    subFields,
                    typeConditions,
                    mergedField.getFieldDefinition().getType());
        }
        List<MergedFieldWTC> result = new ArrayList<>();
        subFields.values().forEach(setMergedFieldWTCMap -> {
            result.addAll(setMergedFieldWTCMap.values());
        });
        return result;
    }

    public List<MergedFieldWTC> collectFromOperation(FieldCollectorParameters parameters, OperationDefinition operationDefinition, GraphQLObjectType rootType) {
        Map<String, Map<GraphQLObjectType, MergedFieldWTC>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        Set<GraphQLObjectType> typeConditions = new LinkedHashSet<>();
        typeConditions.add(rootType);
        this.collectFields(parameters, operationDefinition.getSelectionSet(), visitedFragments, subFields, typeConditions, rootType);
        List<MergedFieldWTC> result = new ArrayList<>();
        subFields.values().forEach(setMergedFieldWTCMap -> {
            result.addAll(setMergedFieldWTCMap.values());
        });
        return result;
    }


    private void collectFields(FieldCollectorParameters parameters,
                               SelectionSet selectionSet,
                               List<String> visitedFragments,
                               Map<String, Map<GraphQLObjectType, MergedFieldWTC>> fields,
                               Set<GraphQLObjectType> typeConditions,
                               GraphQLOutputType parentType) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectField(parameters, fields, (Field) selection, typeConditions, parentType);
            } else if (selection instanceof InlineFragment) {
                collectInlineFragment(parameters, visitedFragments, fields, (InlineFragment) selection, typeConditions, parentType);
            } else if (selection instanceof FragmentSpread) {
                collectFragmentSpread(parameters, visitedFragments, fields, (FragmentSpread) selection, typeConditions, parentType);
            }
        }
    }

    private void collectFragmentSpread(FieldCollectorParameters parameters,
                                       List<String> visitedFragments,
                                       Map<String, Map<GraphQLObjectType, MergedFieldWTC>> fields,
                                       FragmentSpread fragmentSpread,
                                       Set<GraphQLObjectType> typeConditions,
                                       GraphQLOutputType parentType) {
        if (visitedFragments.contains(fragmentSpread.getName())) {
            return;
        }
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), fragmentSpread.getDirectives())) {
            return;
        }
        visitedFragments.add(fragmentSpread.getName());
        FragmentDefinition fragmentDefinition = parameters.getFragmentsByName().get(fragmentSpread.getName());

        if (!conditionalNodes.shouldInclude(parameters.getVariables(), fragmentDefinition.getDirectives())) {
            return;
        }
        GraphQLCompositeType newCondition = (GraphQLCompositeType) parameters.getGraphQLSchema().getType(fragmentDefinition.getTypeCondition().getName());
        Set<GraphQLObjectType> newConditions = getPossibleObjectTypes(typeConditions, newCondition, parameters.getGraphQLSchema());
        GraphQLCompositeType newParentType = (GraphQLCompositeType)
                Assert.assertNotNull(parameters.getGraphQLSchema().getType(fragmentDefinition.getTypeCondition().getName()));
        collectFields(parameters, fragmentDefinition.getSelectionSet(), visitedFragments, fields, newConditions, newParentType);
    }

    private void collectInlineFragment(FieldCollectorParameters parameters,
                                       List<String> visitedFragments,
                                       Map<String, Map<GraphQLObjectType, MergedFieldWTC>> fields,
                                       InlineFragment inlineFragment,
                                       Set<GraphQLObjectType> typeConditions,
                                       GraphQLOutputType parentType) {
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), inlineFragment.getDirectives())) {
            return;
        }
        Set<GraphQLObjectType> newConditions = typeConditions;
        if (inlineFragment.getTypeCondition() != null) {
            GraphQLCompositeType newCondition = (GraphQLCompositeType) parameters.getGraphQLSchema().getType(inlineFragment.getTypeCondition().getName());
            newConditions = getPossibleObjectTypes(typeConditions, newCondition, parameters.getGraphQLSchema());
            parentType = (GraphQLCompositeType)
                    Assert.assertNotNull(parameters.getGraphQLSchema().getType(inlineFragment.getTypeCondition().getName()));

        }
        collectFields(parameters, inlineFragment.getSelectionSet(), visitedFragments, fields, newConditions, parentType);
    }

    private void collectField(FieldCollectorParameters parameters,
                              Map<String, Map<GraphQLObjectType, MergedFieldWTC>> fields,
                              Field field,
                              Set<GraphQLObjectType> objectTypes,
                              GraphQLOutputType parentType) {
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), field.getDirectives())) {
            return;
        }
        String name = getFieldEntryKey(field);
        fields.computeIfAbsent(name, ignored -> new LinkedHashMap<>());
        Map<GraphQLObjectType, MergedFieldWTC> existingFieldWTC = fields.get(name);
        for (GraphQLObjectType objectType : objectTypes) {
            if (existingFieldWTC.containsKey(objectType)) {
                MergedFieldWTC mergedFieldWTC = existingFieldWTC.get(objectType);
                existingFieldWTC.put(objectType, mergedFieldWTC.transform(builder -> builder.addField(field)));
            } else {
                GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) GraphQLTypeUtil.unwrapAll(parentType);
                MergedFieldWTC newFieldWTC = MergedFieldWTC.newMergedFieldWTC(field)
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

    private Set<GraphQLObjectType> getPossibleObjectTypes(Set<GraphQLObjectType> currentOnes, GraphQLCompositeType typeCondition, GraphQLSchema graphQLSchema) {

        List<GraphQLObjectType> resolvedTypeCondition = resolveType(typeCondition, graphQLSchema);
        if (currentOnes.size() == 0) {
            return new LinkedHashSet<>(resolvedTypeCondition);
        }

        Set<GraphQLObjectType> result = new LinkedHashSet<>(currentOnes);
        result.retainAll(resolvedTypeCondition);
        return result;
    }

    private List<GraphQLObjectType> resolveType(GraphQLCompositeType type, GraphQLSchema graphQLSchema) {
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

    private List<GraphQLObjectType> getImplicitObjects(GraphQLSchema graphQLSchema, MergedFieldWTC mergedFieldWTC) {
        List<GraphQLObjectType> result = new ArrayList<>();
        GraphQLFieldsContainer fieldsContainer = mergedFieldWTC.getFieldsContainer();
        if (fieldsContainer instanceof GraphQLInterfaceType) {
            result.addAll(graphQLSchema.getImplementations((GraphQLInterfaceType) fieldsContainer));
        } else if (fieldsContainer instanceof GraphQLObjectType) {
            result.add((GraphQLObjectType) fieldsContainer);
        } else {
            return Assert.assertShouldNeverHappen();
        }
        return result;
    }


}
