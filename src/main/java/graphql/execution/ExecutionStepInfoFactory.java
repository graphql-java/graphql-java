package graphql.execution;

import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.util.FpKit;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Internal
public class ExecutionStepInfoFactory {

    public ExecutionStepInfo newExecutionStepInfoForSubField(ExecutionContext executionContext, MergedField mergedField, ExecutionStepInfo parentInfo) {
        GraphQLObjectType parentType = (GraphQLObjectType) parentInfo.getUnwrappedNonNullType();
        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(executionContext.getGraphQLSchema(), parentType, mergedField.getName());
        GraphQLOutputType fieldType = fieldDefinition.getType();
        List<Argument> fieldArgs = mergedField.getArguments();
        GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
        Supplier<Map<String, Object>> argumentValuesSupplier = () -> ValuesResolver.getArgumentValues(codeRegistry,
                fieldDefinition.getArguments(),
                fieldArgs,
                executionContext.getCoercedVariables(),
                executionContext.getGraphQLContext(),
                executionContext.getLocale());
        Supplier<Map<String, Object>> argumentValues = FpKit.intraThreadMemoize(argumentValuesSupplier);

        ResultPath newPath = parentInfo.getPath().segment(mergedField.getResultKey());

        return parentInfo.transform(builder -> builder
                .parentInfo(parentInfo)
                .type(fieldType)
                .fieldDefinition(fieldDefinition)
                .fieldContainer(parentType)
                .field(mergedField)
                .path(newPath)
                .arguments(argumentValues));
    }

    public ExecutionStepInfo newExecutionStepInfoForListElement(ExecutionStepInfo executionInfo, ResultPath indexedPath) {
        GraphQLList fieldType = (GraphQLList) executionInfo.getUnwrappedNonNullType();
        GraphQLOutputType typeInList = (GraphQLOutputType) fieldType.getWrappedType();
        return executionInfo.transform(builder -> builder
                .parentInfo(executionInfo)
                .type(typeInList)
                .path(indexedPath));
    }

}
