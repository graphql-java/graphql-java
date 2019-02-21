package graphql.execution.nextgen.result;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.Collections;
import java.util.List;

@Internal
public class LeafExecutionResultNode extends ExecutionResultNode {

    public LeafExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                   NonNullableFieldWasNullException nonNullableFieldWasNullException) {
        super(fetchedValueAnalysis, nonNullableFieldWasNullException, Collections.emptyList());
    }


    public Object getValue() {
        return getFetchedValueAnalysis().getCompletedValue();
    }

    @Override
    public ExecutionResultNode withNewChildren(List<ExecutionResultNode> children) {
        return Assert.assertShouldNeverHappen();
    }

    @Override
    public ExecutionResultNode withNewFetchedValueAnalysis(FetchedValueAnalysis fetchedValueAnalysis) {
        return new LeafExecutionResultNode(fetchedValueAnalysis, getNonNullableFieldWasNullException());
    }
}