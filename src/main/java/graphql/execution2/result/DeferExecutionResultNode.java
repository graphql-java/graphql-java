package graphql.execution2.result;

import graphql.Assert;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution2.FetchedValueAnalysis;

import java.util.List;
import java.util.Map;

public class DeferExecutionResultNode extends ExecutionResultNode {

    public DeferExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                    NonNullableFieldWasNullException nonNullableFieldWasNullException) {
        super(fetchedValueAnalysis, nonNullableFieldWasNullException);
    }

    @Override
    public List<ExecutionResultNode> getChildren() {
        return Assert.assertShouldNeverHappen();
    }

    @Override
    public ExecutionResultNode withChild(ExecutionResultNode child, ExecutionResultNodePosition position) {
        return Assert.assertShouldNeverHappen("Not available for leafs");
    }

    @Override
    public ExecutionResultNode withNewChildren(Map<ExecutionResultNodePosition, ExecutionResultNode> children) {
        return Assert.assertShouldNeverHappen();
    }

    public Object getValue() {
        return getFetchedValueAnalysis().getCompletedValue();
    }
}