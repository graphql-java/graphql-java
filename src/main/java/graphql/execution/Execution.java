package graphql.execution;


import graphql.language.*;
import graphql.schema.*;

import java.util.*;

public class Execution {

    public Execution() {

    }

    public ExecutionResult execute(GraphQLSchema graphQLSchema, Object root, Document document, String operationName, Map<String, String> args) {
        ExecutionContext executionContext = buildExecutionContext(graphQLSchema, root, document, operationName, args);
        return executeOperation(executionContext, root, executionContext.getOperationDefinition());
    }

    private ExecutionContext buildExecutionContext(GraphQLSchema graphQLSchema, Object root, Document document, String operationName, Map<String, String> args) {
        Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        Map<String, OperationDefinition> operationsByName = new LinkedHashMap<>();

        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition) {
                OperationDefinition operationDefinition = (OperationDefinition) definition;
                operationsByName.put(operationDefinition.getName(), operationDefinition);
            }
            if (definition instanceof FragmentDefinition) {
                FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
                fragmentsByName.put(fragmentDefinition.getName(), fragmentDefinition);
            }
        }
        if (operationName != null && operationsByName.size() > 1) {
            throw new RuntimeException("missing operation name");
        }
        OperationDefinition operation;
        if (operationName == null) {
            operation = operationsByName.values().iterator().next();
        } else {
            operation = operationsByName.get(operationName);
        }

        if (operation == null) {
            throw new RuntimeException();
        }

        ExecutionContext executionContext = new ExecutionContext();
        executionContext.setGraphQLSchema(graphQLSchema);
        executionContext.setOperationDefinition(operation);
        executionContext.setRoot(root);
        executionContext.setFragmentsByName(fragmentsByName);
        //TODO: Variables
        return executionContext;
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

    private ExecutionResult executeOperation(
            ExecutionContext executionContext,
            Object root,
            OperationDefinition operationDefinition) {
        GraphQLObjectType operationRootType = getOperationRootType(executionContext.getGraphQLSchema(), executionContext.getOperationDefinition());

        Map<String, List<Field>> fields = new LinkedHashMap<>();
        collectFields(executionContext, operationRootType, operationDefinition.getSelectionSet(), new ArrayList<String>(), fields);


        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            throw new RuntimeException("not yet");
        }
        Object result = executeFields(executionContext, operationRootType, root, fields);
        return new ExecutionResult(result);
    }

    private Map<String, Object> executeFields(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields) {
        Map<String, Object> results = new LinkedHashMap<>();
        for (String fieldName : fields.keySet()) {
            List<Field> fieldList = fields.get(fieldName);
            Object resolvedResult = resolveField(executionContext, parentType, source, fieldList);
            if (resolvedResult == null) continue;
            results.put(fieldName, resolvedResult);
        }
        return results;
    }

    private Object resolveField(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, List<Field> fields) {
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, fields.get(0));
        if (fieldDef == null) return null;
        Object resolvedValue;
        resolvedValue = fieldDef.getResolveValue().resolve(source, null);


        return completeValue(executionContext, fieldDef.getType(), fields, resolvedValue);
    }

    private Object completeValue(ExecutionContext executionContext, GraphQLType fieldType, List<Field> fields, Object result) {
        if (fieldType instanceof GraphQLNonNull) {
            GraphQLNonNull graphQLNonNull = (GraphQLNonNull) fieldType;
            Object completed = completeValue(executionContext, graphQLNonNull.getWrappedType(), fields, result);
            //TODO: Check not null
            return completed;

        } else if (fieldType instanceof GraphQLList) {
            return completeValueForList(executionContext, (GraphQLList) fieldType, fields, (List<Object>) result);
        } else if (fieldType instanceof GraphQLScalarType) {
            return completeValueForScalar((GraphQLScalarType) fieldType, result);
        } else if (fieldType instanceof GraphQLEnumType) {
            return completeValueForEnum((GraphQLEnumType) fieldType, result);
        }

        Map<String, List<Field>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        for (Field field : fields) {
            if (field.getSelectionSet() == null) continue;
            collectFields(executionContext, (GraphQLObjectType) fieldType, field.getSelectionSet(), visitedFragments, subFields);
        }
        return executeFields(executionContext, (GraphQLObjectType) fieldType, result, subFields);
    }

    private Object completeValueForEnum(GraphQLEnumType fieldType, Object result) {
        GraphQLEnumType graphQLEnumType = fieldType;
        return graphQLEnumType.getCoercing().coerce(result);
    }

    private Object completeValueForScalar(GraphQLScalarType fieldType, Object result) {
        GraphQLScalarType graphQLScalarType = fieldType;
        return graphQLScalarType.getCoercing().coerce(result);
    }

    private Object completeValueForList(ExecutionContext executionContext, GraphQLList fieldType, List<Field> fields, List<Object> result) {
        GraphQLList graphQLList = fieldType;
        List<Object> items = result;
        List<Object> completedResults = new ArrayList<>();
        for (Object item : items) {
            completedResults.add(completeValue(executionContext, graphQLList.getWrappedType(), fields, item));
        }
        return completedResults;
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
        // check directive values
        return true;
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
