package graphql.execution;


import graphql.Internal;
import graphql.execution.conditional.ConditionalNodes;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.execution.MergedSelectionSet.newMergedSelectionSet;
import static graphql.execution.TypeFromAST.getTypeFromAST;

/**
 * A field collector can iterate over field selection sets and build out the sub fields that have been selected,
 * expanding named and inline fragments as it goes.
 */
@Internal
public class FieldCollector {

    private final ConditionalNodes conditionalNodes = new ConditionalNodes();

    public MergedSelectionSet collectFields(FieldCollectorParameters parameters, MergedField mergedField) {
        Map<String, MergedField> subFields = new LinkedHashMap<>();
        Set<String> visitedFragments = new LinkedHashSet<>();
        for (Field field : mergedField.getFields()) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            this.collectFields(parameters, field.getSelectionSet(), visitedFragments, subFields);
        }
        return newMergedSelectionSet().subFields(subFields).build();
    }

    /**
     * Given a selection set this will collect the sub-field selections and return it as a map
     *
     * @param parameters   the parameters to this method
     * @param selectionSet the selection set to collect on
     *
     * @return a map of the sub field selections
     */
    public MergedSelectionSet collectFields(FieldCollectorParameters parameters, SelectionSet selectionSet) {
        Map<String, MergedField> subFields = new LinkedHashMap<>();
        Set<String> visitedFragments = new LinkedHashSet<>();
        this.collectFields(parameters, selectionSet, visitedFragments, subFields);
        return newMergedSelectionSet().subFields(subFields).build();
    }


    private void collectFields(FieldCollectorParameters parameters, SelectionSet selectionSet, Set<String> visitedFragments, Map<String, MergedField> fields) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectField(parameters, fields, (Field) selection);
            } else if (selection instanceof InlineFragment) {
                collectInlineFragment(parameters, visitedFragments, fields, (InlineFragment) selection);
            } else if (selection instanceof FragmentSpread) {
                collectFragmentSpread(parameters, visitedFragments, fields, (FragmentSpread) selection);
            }
        }
    }

    private void collectFragmentSpread(FieldCollectorParameters parameters, Set<String> visitedFragments, Map<String, MergedField> fields, FragmentSpread fragmentSpread) {
        if (visitedFragments.contains(fragmentSpread.getName())) {
            return;
        }
        if (!conditionalNodes.shouldInclude(fragmentSpread,
                parameters.getVariables(),
                parameters.getGraphQLSchema(),
                parameters.getGraphQLContext())) {
            return;
        }
        visitedFragments.add(fragmentSpread.getName());
        FragmentDefinition fragmentDefinition = parameters.getFragmentsByName().get(fragmentSpread.getName());

        if (!conditionalNodes.shouldInclude(fragmentDefinition,
                parameters.getVariables(),
                parameters.getGraphQLSchema(),
                parameters.getGraphQLContext())) {
            return;
        }
        if (!doesFragmentConditionMatch(parameters, fragmentDefinition)) {
            return;
        }
        collectFields(parameters, fragmentDefinition.getSelectionSet(), visitedFragments, fields);
    }

    private void collectInlineFragment(FieldCollectorParameters parameters, Set<String> visitedFragments, Map<String, MergedField> fields, InlineFragment inlineFragment) {
        if (!conditionalNodes.shouldInclude(inlineFragment,
                parameters.getVariables(),
                parameters.getGraphQLSchema(),
                parameters.getGraphQLContext()) ||
                !doesFragmentConditionMatch(parameters, inlineFragment)) {
            return;
        }
        collectFields(parameters, inlineFragment.getSelectionSet(), visitedFragments, fields);
    }

    private void collectField(FieldCollectorParameters parameters, Map<String, MergedField> fields, Field field) {
        if (!conditionalNodes.shouldInclude(field,
                parameters.getVariables(),
                parameters.getGraphQLSchema(),
                parameters.getGraphQLContext())) {
            return;
        }
        String name = field.getResultKey();
        if (fields.containsKey(name)) {
            MergedField curFields = fields.get(name);
            fields.put(name, curFields.transform(builder -> builder.addField(field)));
        } else {
            fields.put(name, MergedField.newMergedField(field).build());
        }
    }

    private boolean doesFragmentConditionMatch(FieldCollectorParameters parameters, InlineFragment inlineFragment) {
        if (inlineFragment.getTypeCondition() == null) {
            return true;
        }
        GraphQLType conditionType;
        conditionType = getTypeFromAST(parameters.getGraphQLSchema(), inlineFragment.getTypeCondition());
        return checkTypeCondition(parameters, conditionType);
    }

    private boolean doesFragmentConditionMatch(FieldCollectorParameters parameters, FragmentDefinition fragmentDefinition) {
        GraphQLType conditionType;
        conditionType = getTypeFromAST(parameters.getGraphQLSchema(), fragmentDefinition.getTypeCondition());
        return checkTypeCondition(parameters, conditionType);
    }

    private boolean checkTypeCondition(FieldCollectorParameters parameters, GraphQLType conditionType) {
        GraphQLObjectType type = parameters.getObjectType();
        if (conditionType.equals(type)) {
            return true;
        }

        if (conditionType instanceof GraphQLInterfaceType) {
            List<GraphQLObjectType> implementations = parameters.getGraphQLSchema().getImplementations((GraphQLInterfaceType) conditionType);
            return implementations.contains(type);
        } else if (conditionType instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) conditionType).getTypes().contains(type);
        }
        return false;
    }


}
