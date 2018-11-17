package graphql.execution2;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.List;
import java.util.Map;

public class ExecutionStepInfoFactory {

    private final ExecutionContext executionContext;

    ValuesResolver valuesResolver = new ValuesResolver();

    public ExecutionStepInfoFactory(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public ExecutionStepInfo newExecutionStepInfoForSubField(List<Field> sameFields, ExecutionStepInfo parentInfo) {
        Field field = sameFields.get(0);
        GraphQLObjectType parentType = (GraphQLObjectType) parentInfo.getUnwrappedNonNullType();
        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(executionContext.getGraphQLSchema(), parentType, field.getName());
        GraphQLOutputType fieldType = fieldDefinition.getType();
        List<Argument> fieldArgs = field.getArguments();
        GraphqlFieldVisibility fieldVisibility = executionContext.getGraphQLSchema().getFieldVisibility();
        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldVisibility, fieldDefinition.getArguments(), fieldArgs, executionContext.getVariables());

        ExecutionPath newPath = parentInfo.getPath().segment(mkNameForPath(sameFields));

        return ExecutionStepInfo.newExecutionStepInfo()
                .type(fieldType)
                .fieldDefinition(fieldDefinition)
                .field(field)
                .path(newPath)
                .parentInfo(parentInfo)
                .arguments(argumentValues)
                .build();
    }

    public ExecutionStepInfo newExecutionStepInfoForListElement(ExecutionStepInfo executionInfo, int index) {
        Field field = executionInfo.getField();
        GraphQLFieldDefinition fieldDef = executionInfo.getFieldDefinition();
        GraphQLList fieldType = (GraphQLList) executionInfo.getUnwrappedNonNullType();
        ExecutionPath indexedPath = executionInfo.getPath().segment(index);
        return ExecutionStepInfo.newExecutionStepInfo()
                .parentInfo(executionInfo)
                .type(fieldType.getWrappedType())
                .path(indexedPath)
                .fieldDefinition(fieldDef)
                .field(field)
                .build();
    }

    private static String mkNameForPath(List<Field> currentField) {
        Field field = currentField.get(0);
        return field.getAlias() != null ? field.getAlias() : field.getName();
    }
}
