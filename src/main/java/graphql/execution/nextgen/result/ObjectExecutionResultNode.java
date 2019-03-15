package graphql.execution.nextgen.result;

import graphql.Internal;
import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.List;

@Internal
public class ObjectExecutionResultNode extends ExecutionResultNode {


    public ObjectExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                     List<ExecutionResultNode> children) {
        super(fetchedValueAnalysis, ResultNodesUtil.newNullableException(fetchedValueAnalysis, children), children);
    }


    @Override
    public ObjectExecutionResultNode withNewChildren(List<ExecutionResultNode> children) {
        return new ObjectExecutionResultNode(getFetchedValueAnalysis(), children);
    }

    @Override
    public ExecutionResultNode withNewFetchedValueAnalysis(FetchedValueAnalysis fetchedValueAnalysis) {
        return new ObjectExecutionResultNode(fetchedValueAnalysis, getChildren());
    }
}
