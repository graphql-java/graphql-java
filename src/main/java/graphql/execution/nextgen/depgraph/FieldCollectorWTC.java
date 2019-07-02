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
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLTypeUtil;

import java.util.ArrayList;
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
        Map<String, Map<Set<String>, MergedFieldWTC>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        for (Field field : mergedField.getFields()) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            this.collectFields(parameters,
                    field.getSelectionSet(),
                    visitedFragments,
                    subFields,
                    new LinkedHashSet<>(),
                    mergedField.getFieldDefinition().getType());
        }
        List<MergedFieldWTC> result = new ArrayList<>();
        subFields.values().forEach(setMergedFieldWTCMap -> {
            result.addAll(setMergedFieldWTCMap.values());
        });
        return result;
    }

    public List<MergedFieldWTC> collectFromOperation(FieldCollectorParameters parameters, OperationDefinition operationDefinition, GraphQLCompositeType rootType) {
        Map<String, Map<Set<String>, MergedFieldWTC>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        this.collectFields(parameters, operationDefinition.getSelectionSet(), visitedFragments, subFields, new LinkedHashSet<>(), rootType);
        List<MergedFieldWTC> result = new ArrayList<>();
        subFields.values().forEach(setMergedFieldWTCMap -> {
            result.addAll(setMergedFieldWTCMap.values());
        });
        return result;
    }


    private void collectFields(FieldCollectorParameters parameters,
                               SelectionSet selectionSet,
                               List<String> visitedFragments,
                               Map<String, Map<Set<String>, MergedFieldWTC>> fields,
                               Set<String> typeConditions,
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
                                       Map<String, Map<Set<String>, MergedFieldWTC>> fields,
                                       FragmentSpread fragmentSpread,
                                       Set<String> typeConditions,
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
        Set<String> newConditions = new LinkedHashSet<>(typeConditions);
        newConditions.add(fragmentDefinition.getTypeCondition().getName());
        GraphQLCompositeType newParentType = (GraphQLCompositeType)
                Assert.assertNotNull(parameters.getGraphQLSchema().getType(fragmentDefinition.getTypeCondition().getName()));
        collectFields(parameters, fragmentDefinition.getSelectionSet(), visitedFragments, fields, newConditions, newParentType);
    }

    private void collectInlineFragment(FieldCollectorParameters parameters,
                                       List<String> visitedFragments,
                                       Map<String, Map<Set<String>, MergedFieldWTC>> fields,
                                       InlineFragment inlineFragment,
                                       Set<String> typeConditions,
                                       GraphQLOutputType parentType) {
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), inlineFragment.getDirectives())) {
            return;
        }
        Set<String> newConditions = new LinkedHashSet<>(typeConditions);
        if (inlineFragment.getTypeCondition() != null) {
            newConditions.add(inlineFragment.getTypeCondition().getName());
            parentType = (GraphQLCompositeType)
                    Assert.assertNotNull(parameters.getGraphQLSchema().getType(inlineFragment.getTypeCondition().getName()));

        }
        collectFields(parameters, inlineFragment.getSelectionSet(), visitedFragments, fields, newConditions, parentType);
    }

    private void collectField(FieldCollectorParameters parameters,
                              Map<String, Map<Set<String>, MergedFieldWTC>> fields,
                              Field field,
                              Set<String> typeConditions,
                              GraphQLOutputType parentType) {
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), field.getDirectives())) {
            return;
        }
        String name = getFieldEntryKey(field);
        fields.computeIfAbsent(name, ignored -> new LinkedHashMap<>());
        Map<Set<String>, MergedFieldWTC> existingFieldWTC = fields.get(name);
        if (existingFieldWTC.containsKey(typeConditions)) {
            MergedFieldWTC mergedFieldWTC = existingFieldWTC.get(typeConditions);
            existingFieldWTC.put(typeConditions, mergedFieldWTC.transform(builder -> builder.addField(field)));
        } else {
            GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) GraphQLTypeUtil.unwrapAll(parentType);
            MergedFieldWTC newFieldWTC = MergedFieldWTC.newMergedFieldWTC(field)
                    .typeConditions(new ArrayList<>(typeConditions))
                    .fieldDefinition(getFieldDefinition(fieldsContainer, field.getName()))
                    .fieldsContainer(fieldsContainer)
                    .parentType(parentType)
                    .build();
            existingFieldWTC.put(typeConditions, newFieldWTC);
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


}
