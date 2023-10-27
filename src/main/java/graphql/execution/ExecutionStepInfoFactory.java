package graphql.execution;

import graphql.Internal;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLOutputType;

@Internal
public class ExecutionStepInfoFactory {

    public ExecutionStepInfo newExecutionStepInfoForListElement(ExecutionStepInfo executionInfo, ResultPath indexedPath) {
        GraphQLList fieldType = (GraphQLList) executionInfo.getUnwrappedNonNullType();
        GraphQLOutputType typeInList = (GraphQLOutputType) fieldType.getWrappedType();
        return executionInfo.transform(builder -> builder
                .parentInfo(executionInfo)
                .type(typeInList)
                .path(indexedPath));
    }

}
