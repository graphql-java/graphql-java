package graphql.execution.nextgen.depgraph;


import graphql.Internal;
import graphql.execution.ConditionalNodes;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


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
            this.collectFields(parameters, field.getSelectionSet(), visitedFragments, subFields, new LinkedHashSet<>());
        }
        List<MergedFieldWTC> result = new ArrayList<>();
        subFields.values().forEach(setMergedFieldWTCMap -> {
            result.addAll(setMergedFieldWTCMap.values());
        });
        return result;
    }

    public List<MergedFieldWTC> collectFromOperation(FieldCollectorParameters parameters, OperationDefinition operationDefinition) {
        Map<String, Map<Set<String>, MergedFieldWTC>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        this.collectFields(parameters, operationDefinition.getSelectionSet(), visitedFragments, subFields, new LinkedHashSet<>());
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
                               Set<String> typeConditions) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectField(parameters, fields, (Field) selection, typeConditions);
            } else if (selection instanceof InlineFragment) {
                collectInlineFragment(parameters, visitedFragments, fields, (InlineFragment) selection, typeConditions);
            } else if (selection instanceof FragmentSpread) {
                collectFragmentSpread(parameters, visitedFragments, fields, (FragmentSpread) selection, typeConditions);
            }
        }
    }

    private void collectFragmentSpread(FieldCollectorParameters parameters,
                                       List<String> visitedFragments,
                                       Map<String, Map<Set<String>, MergedFieldWTC>> fields,
                                       FragmentSpread fragmentSpread,
                                       Set<String> typeConditions) {
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
        collectFields(parameters, fragmentDefinition.getSelectionSet(), visitedFragments, fields, newConditions);
    }

    private void collectInlineFragment(FieldCollectorParameters parameters,
                                       List<String> visitedFragments,
                                       Map<String, Map<Set<String>, MergedFieldWTC>> fields,
                                       InlineFragment inlineFragment,
                                       Set<String> typeConditions) {
        if (!conditionalNodes.shouldInclude(parameters.getVariables(), inlineFragment.getDirectives())) {
            return;
        }
        Set<String> newConditions = new LinkedHashSet<>(typeConditions);
        if (inlineFragment.getTypeCondition() != null) {
            newConditions.add(inlineFragment.getTypeCondition().getName());
        }
        collectFields(parameters, inlineFragment.getSelectionSet(), visitedFragments, fields, newConditions);
    }

    private void collectField(FieldCollectorParameters parameters,
                              Map<String, Map<Set<String>, MergedFieldWTC>> fields,
                              Field field,
                              Set<String> typeConditions) {
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
            existingFieldWTC.put(typeConditions, MergedFieldWTC.newMergedFieldWTC(field).build());
        }
    }

    private String getFieldEntryKey(Field field) {
        if (field.getAlias() != null) {
            return field.getAlias();
        } else {
            return field.getName();
        }
    }


}
