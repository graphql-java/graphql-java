package graphql.execution.nextgen.result;

import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.List;

public class RootExecutionResultNode extends ObjectExecutionResultNode {

    public RootExecutionResultNode(List<ExecutionResultNode> children) {
        super(null, children);
    }

    @Override
    public FetchedValueAnalysis getFetchedValueAnalysis() {
        throw new RuntimeException("Root node");
    }

    @Override
    public ObjectExecutionResultNode withNewChildren(List<ExecutionResultNode> children) {
        return new RootExecutionResultNode(children);
    }
}
