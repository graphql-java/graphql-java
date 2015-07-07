package graphql.execution;


import graphql.language.*;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Execution {

    public Execution() {

    }

    public void execute(GraphQLSchema graphQLSchema, Object root, Document document, String operationName, Map<String, String> args) {
        ExecutionContext executionContext = buildExecutionContext();
        GraphQLObjectType operationRootType = getOperationRootType(graphQLSchema, executionContext.getOperationDefinition());
    }

    private ExecutionContext buildExecutionContext() {
        return null;
    }


    private GraphQLObjectType getOperationRootType(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {
        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            return graphQLSchema.getMutationType();

        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.QUERY) {
            return graphQLSchema.getQueryType();

        } else {
            throw new RuntimeException();
        }
    }


    private GraphQLObjectType getOperationRootType(OperationDefinition operationDefinition, GraphQLSchema graphQLSchema) {
        return null;
    }

    private void executeFields(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields) {
        for (String fieldName : fields.keySet()) {
            List<Field> field = fields.get(fieldName);

        }
    }

    private void resolveField(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, List<Field> fields) {

    }

    private GraphQLFieldDefinition getFieldDef(GraphQLSchema schema, GraphQLObjectType parentType, Field field) {
//
//        if (name == = SchemaMetaFieldDef.name &&
//                schema.getQueryType() == = parentType) {
//            return SchemaMetaFieldDef;
//        } else if (name == = TypeMetaFieldDef.name &&
//                schema.getQueryType() == = parentType) {
//            return TypeMetaFieldDef;
//        } else if (name == = TypeNameMetaFieldDef.name) {
//            return TypeNameMetaFieldDef;
//        }
        return parentType.getFieldDefinition(field.getName());
    }

    private void collectFields(ExecutionContext executionContext, GraphQLObjectType type, SelectionSet selectionSet, List<String> visitedFragments, Map<String, List<Field>> fields) {

        for (Selection selection : selectionSet.getSelections()) {

            if (selection instanceof Field) {
                Field field = (Field) selection;
                if (!shouldIncludeNode(executionContext, field.getDirectives())) {
                    continue;
                }
                String name = getFieldEntryKey(field);
                if (!fields.containsKey(name)) {
                    fields.put(name, new ArrayList<>());
                }

            } else if (selection instanceof FragmentDefinition) {
                FragmentDefinition fragmentDefinition = (FragmentDefinition) selection;
                if (!shouldIncludeNode(executionContext, fragmentDefinition.getDirectives()) || !doesFragmentTypeApply(executionContext, selection, type)) {
                    continue;
                }
                collectFields(executionContext, type, fragmentDefinition.getSelectionSet(), visitedFragments, fields);

            } else if (selection instanceof FragmentSpread) {
                FragmentSpread fragmentSpread = (FragmentSpread) selection;

                if (visitedFragments.contains(fragmentSpread.getName()) ||
                        !shouldIncludeNode(executionContext, fragmentSpread.getDirectives())) {
                    continue;
                }
                visitedFragments.add(fragmentSpread.getName());
                FragmentDefinition fragment = executionContext.getFragment(fragmentSpread.getName());
                if (!shouldIncludeNode(executionContext, fragment.getDirectives()) ||
                        !doesFragmentTypeApply(executionContext, selection, type)) {
                    continue;
                }
                collectFields(
                        executionContext,
                        type,
                        fragment.getSelectionSet(),
                        visitedFragments,
                        fields
                );
            }
        }

    }

    private String getFieldEntryKey(Field field) {
        if (field.getAlias() != null) return field.getAlias();
        else return field.getName();
    }


    private boolean shouldIncludeNode(ExecutionContext executionContext, List<Directive> directives) {
        return true;
    }

    private boolean doesFragmentTypeApply(ExecutionContext executionContext, Selection selection, GraphQLObjectType type) {
        return true;
    }
}
