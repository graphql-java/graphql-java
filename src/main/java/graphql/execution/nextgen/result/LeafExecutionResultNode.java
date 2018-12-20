package graphql.execution.nextgen.result;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.List;
import java.util.Map;

@Internal
public class LeafExecutionResultNode extends ExecutionResultNode {

    public LeafExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
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