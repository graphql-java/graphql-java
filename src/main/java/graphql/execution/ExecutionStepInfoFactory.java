package graphql.execution;

import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;

import java.util.List;
import java.util.Map;

@Internal
public class ExecutionStepInfoFactory {


    ValuesResolver valuesResolver = new ValuesResolver();


    public ExecutionStepInfo newExecutionStepInfoForSubField(ExecutionContext executionContext, MergedFields mergedFields, ExecutionStepInfo parentInfo) {
        GraphQLObjectType parentType = (GraphQLObjectType) parentInfo.getUnwrappedNonNullType();
        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(executionContext.getGraphQLSchema(), parentType, mergedFields.getName());
        GraphQLOutputType fieldType = fieldDefinition.getType();
        List<Argument> fieldArgs = mergedFields.getArguments();
        GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(codeRegistry, fieldDefinition.getArguments(), fieldArgs, executionContext.getVariables());

        ExecutionPath newPath = parentInfo.getPath().segment(mkNameForPath(mergedFields));

        return parentInfo.transform(builder -> builder
                .parentInfo(parentInfo)
                .type(fieldType)
                .fieldDefinition(fieldDefinition)
                .field(mergedFields)
                .path(newPath)
                .arguments(argumentValues));
    }

    public ExecutionStepInfo newExecutionStepInfoForListElement(ExecutionStepInfo executionInfo, int index) {
        GraphQLList fieldType = (GraphQLList) executionInfo.getUnwrappedNonNullType();
        GraphQLOutputType typeInList = (GraphQLOutputType) fieldType.getWrappedType();
        ExecutionPath indexedPath = executionInfo.getPath().segment(index);
        return executionInfo.transform(builder -> builder
                .parentInfo(executionInfo)
                .type(typeInList)
                .path(indexedPath));
    }

    private static String mkNameForPath(MergedFields currentField) {
        Field field = currentField.getSingleField();
        return field.getAlias() != null ? field.getAlias() : field.getName();
    }
}
