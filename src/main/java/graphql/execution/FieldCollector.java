package graphql.execution;


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
import graphql.schema.SchemaUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.execution.TypeFromAST.getTypeFromAST;

/**
 * A field collector can iterate over field selection sets and build out the sub fields that have been selected,
 * expanding named and inline fragments as it goes.s
 */
public class FieldCollector {

    private ConditionalNodes conditionalNodes;

    private SchemaUtil schemaUtil = new SchemaUtil();

    public FieldCollector() {
        conditionalNodes = new ConditionalNodes();
    }


    /**
     * Given a list of fields this will collect the sub-field selections and return it as a map
     *
     * @param executionContext the {@link ExecutionContext} in play
     * @param objectType       the graphql object type in context
     * @param fields           the list of fields to collect for
     *
     * @return a map of the sub field selections
     */
    public Map<String, List<Field>> collectFields(ExecutionContext executionContext, GraphQLObjectType objectType, List<Field> fields) {
        Map<String, List<Field>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        for (Field field : fields) {
            if (field.getSelectionSet() == null) {
                continue;
            }
            this.collectFields(executionContext, objectType, field.getSelectionSet(), visitedFragments, subFields);
        }
        return subFields;
    }

    /**
     * Given a selection set this will collect the sub-field selections and return it as a map
     *
     * @param executionContext the {@link ExecutionContext} in play
     * @param objectType       the graphql object type in context
     * @param selectionSet     the selection set to collect on
     *
     * @return a map of the sub field selections
     */
    public Map<String, List<Field>> collectFields(ExecutionContext executionContext, GraphQLObjectType objectType, SelectionSet selectionSet) {
        Map<String, List<Field>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        this.collectFields(executionContext, objectType, selectionSet, visitedFragments, subFields);
        return subFields;
    }


    private void collectFields(ExecutionContext executionContext, GraphQLObjectType type, SelectionSet selectionSet, List<String> visitedFragments, Map<String, List<Field>> fields) {

        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                collectField(executionContext, fields, (Field) selection);
            } else if (selection instanceof InlineFragment) {
                collectInlineFragment(executionContext, type, visitedFragments, fields, (InlineFragment) selection);
            } else if (selection instanceof FragmentSpread) {
                collectFragmentSpread(executionContext, type, visitedFragments, fields, (FragmentSpread) selection);
            }
        }
    }

    private void collectFragmentSpread(ExecutionContext executionContext, GraphQLObjectType type, List<String> visitedFragments, Map<String, List<Field>> fields, FragmentSpread fragmentSpread) {
        if (visitedFragments.contains(fragmentSpread.getName())) {
            return;
        }
        if (!conditionalNodes.shouldInclude(executionContext, fragmentSpread.getDirectives())) {
            return;
        }
        visitedFragments.add(fragmentSpread.getName());
        FragmentDefinition fragmentDefinition = executionContext.getFragment(fragmentSpread.getName());

        if (!conditionalNodes.shouldInclude(executionContext, fragmentDefinition.getDirectives())) {
            return;
        }
        if (!doesFragmentConditionMatch(executionContext, fragmentDefinition, type)) {
            return;
        }
        collectFields(executionContext, type, fragmentDefinition.getSelectionSet(), visitedFragments, fields);
    }

    private void collectInlineFragment(ExecutionContext executionContext, GraphQLObjectType type, List<String> visitedFragments, Map<String, List<Field>> fields, InlineFragment inlineFragment) {
        if (!conditionalNodes.shouldInclude(executionContext, inlineFragment.getDirectives()) ||
                !doesFragmentConditionMatch(executionContext, inlineFragment, type)) {
            return;
        }
        collectFields(executionContext, type, inlineFragment.getSelectionSet(), visitedFragments, fields);
    }

    private void collectField(ExecutionContext executionContext, Map<String, List<Field>> fields, Field field) {
        if (!conditionalNodes.shouldInclude(executionContext, field.getDirectives())) {
            return;
        }
        String name = getFieldEntryKey(field);
        if (!fields.containsKey(name)) {
            fields.put(name, new ArrayList<>());
        }
        fields.get(name).add(field);
    }

    private String getFieldEntryKey(Field field) {
        if (field.getAlias() != null) return field.getAlias();
        else return field.getName();
    }


    private boolean doesFragmentConditionMatch(ExecutionContext executionContext, InlineFragment inlineFragment, GraphQLObjectType type) {
        if (inlineFragment.getTypeCondition() == null) {
            return true;
        }
        GraphQLType conditionType;
        conditionType = getTypeFromAST(executionContext.getGraphQLSchema(), inlineFragment.getTypeCondition());
        return checkTypeCondition(executionContext, type, conditionType);
    }

    private boolean doesFragmentConditionMatch(ExecutionContext executionContext, FragmentDefinition fragmentDefinition, GraphQLObjectType type) {
        GraphQLType conditionType;
        conditionType = getTypeFromAST(executionContext.getGraphQLSchema(), fragmentDefinition.getTypeCondition());
        return checkTypeCondition(executionContext, type, conditionType);
    }

    private boolean checkTypeCondition(ExecutionContext executionContext, GraphQLObjectType type, GraphQLType conditionType) {
        if (conditionType.equals(type)) {
            return true;
        }

        if (conditionType instanceof GraphQLInterfaceType) {
            List<GraphQLObjectType> implementations = schemaUtil.findImplementations(executionContext.getGraphQLSchema(), (GraphQLInterfaceType) conditionType);
            return implementations.contains(type);
        } else if (conditionType instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) conditionType).getTypes().contains(type);
        }
        return false;
    }


}
