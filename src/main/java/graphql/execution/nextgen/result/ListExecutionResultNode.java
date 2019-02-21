package graphql.execution.nextgen.result;

import graphql.Internal;
import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.List;

@Internal
public class ListExecutionResultNode extends ExecutionResultNode {


    public ListExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                   List<ExecutionResultNode> children) {
        super(fetchedValueAnalysis, ResultNodesUtil.newNullableException(fetchedValueAnalysis, children), children);
    }

    @Override
    public ExecutionResultNode withNewChildren(List<ExecutionResultNode> children) {
        return new ListExecutionResultNode(getFetchedValueAnalysis(), children);
    }

    @Override
    public ExecutionResultNode withNewFetchedValueAnalysis(FetchedValueAnalysis fetchedValueAnalysis) {
        return new ListExecutionResultNode(fetchedValueAnalysis, getChildren());
    }
}
