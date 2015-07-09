package graphql.execution;


import graphql.Directives;
import graphql.language.*;
import graphql.schema.GraphQLObjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.Directives.*;

public class FieldCollector {

    private Resolver resolver;

    public FieldCollector() {
        resolver = new Resolver();
    }


    public void collectFields(ExecutionContext executionContext, GraphQLObjectType type, SelectionSet selectionSet, List<String> visitedFragments, Map<String, List<Field>> fields) {

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

        if (visitedFragments.contains(fragmentSpread.getName()) ||
                !shouldIncludeNode(executionContext, fragmentSpread.getDirectives())) {
            return;
        }
        visitedFragments.add(fragmentSpread.getName());
        FragmentDefinition fragment = executionContext.getFragment(fragmentSpread.getName());
        if (!shouldIncludeNode(executionContext, fragment.getDirectives()) ||
                !doesFragmentTypeApply(executionContext, fragmentSpread, type)) {
            return;
        }
        collectFields(
                executionContext,
                type,
                fragment.getSelectionSet(),
                visitedFragments,
                fields
        );
    }

    private void collectInlineFragment(ExecutionContext executionContext, GraphQLObjectType type, List<String> visitedFragments, Map<String, List<Field>> fields, InlineFragment inlineFragment) {
        if (!shouldIncludeNode(executionContext, inlineFragment.getDirectives()) ||
                !doesFragmentTypeApply(executionContext, inlineFragment, type)) {
            return;
        }
        collectFields(executionContext, type, inlineFragment.getSelectionSet(), visitedFragments, fields);
    }

    private void collectField(ExecutionContext executionContext, Map<String, List<Field>> fields, Field field) {
        if (!shouldIncludeNode(executionContext, field.getDirectives())) {
            return;
        }
        String name = getFieldEntryKey(field);
        if (!fields.containsKey(name)) {
            fields.put(name, new ArrayList<Field>());
        }
        fields.get(name).add(field);
    }

    private String getFieldEntryKey(Field field) {
        if (field.getAlias() != null) return field.getAlias();
        else return field.getName();
    }


    private boolean shouldIncludeNode(ExecutionContext executionContext, List<Directive> directives) {

        Directive skipDirective = findDirective(directives, SkipDirective.getName());
        if (skipDirective != null) {
            Map<String, Object> argumentValues = resolver.getArgumentValues(SkipDirective.getArguments(), skipDirective.getArguments(), executionContext.getVariables());
            return !(Boolean) argumentValues.get("if");
        }


        Directive includeDirective = findDirective(directives, IncludeDirective.getName());
        if (includeDirective != null) {
            Map<String, Object> argumentValues = resolver.getArgumentValues(IncludeDirective.getArguments(), includeDirective.getArguments(), executionContext.getVariables());
            return (Boolean) argumentValues.get("if");
        }

        return true;
    }

    private Directive findDirective(List<Directive> directives, String name) {
        for (Directive directive : directives) {
            if (directive.getName().equals(name)) return directive;
        }
        return null;
    }

    private boolean doesFragmentTypeApply(ExecutionContext executionContext, Selection selection, GraphQLObjectType type) {
//        String typeCondition
//        if(selection instanceof  InlineFragment){
//            typeCondition = ((InlineFragment) selection).getTypeCondition();
//        }else if( selection instanceof FragmentDefinition){
//            typeCondition = ((FragmentDefinition) selection).getTypeCondition();
//        }
//        var conditionalType = typeFromAST(exeContext.schema, fragment.typeCondition);
//        if (conditionalType === type) {
//            return true;
//        }
//        if (conditionalType instanceof GraphQLInterfaceType ||
//                conditionalType instanceof GraphQLUnionType) {
//            return conditionalType.isPossibleType(type);
//        }
//        return false;
        return false;
    }


}
