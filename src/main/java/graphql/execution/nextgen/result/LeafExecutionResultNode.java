package graphql.execution.nextgen.result;

import graphql.Assert;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
public class LeafExecutionResultNode extends ExecutionResultNode {

    public LeafExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                   NonNullableFieldWasNullException nonNullableFieldWasNullException) {
        this(fetchedValueAnalysis, nonNullableFieldWasNullException, Collections.emptyList());
    }

    public LeafExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                   NonNullableFieldWasNullException nonNullableFieldWasNullException,
                                   List<GraphQLError> errors) {
        this(fetchedValueAnalysis, nonNullableFieldWasNullException, errors, null);
    }

    private LeafExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                    NonNullableFieldWasNullException nonNullableFieldWasNullException,
                                    List<GraphQLError> errors,
                                    Object context) {
        super(fetchedValueAnalysis, nonNullableFieldWasNullException, Collections.emptyList(), errors, context);
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
        return new LeafExecutionResultNode(fetchedValueAnalysis, getNonNullableFieldWasNullException(), getErrors(), getContext());
    }

    @Override
    public ExecutionResultNode withNewErrors(List<GraphQLError> errors) {
        return new LeafExecutionResultNode(getFetchedValueAnalysis(), getNonNullableFieldWasNullException(), new ArrayList<>(errors), getContext());
    }

    @Override
    public ExecutionResultNode withNewContext(Object context) {
        return new LeafExecutionResultNode(getFetchedValueAnalysis(), getNonNullableFieldWasNullException(), getErrors(), context);
    }
}