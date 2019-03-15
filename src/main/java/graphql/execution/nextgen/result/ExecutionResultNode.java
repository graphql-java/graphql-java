package graphql.execution.nextgen.result;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.MergedField;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static graphql.Assert.assertNotNull;

@Internal
public abstract class ExecutionResultNode {

    private final FetchedValueAnalysis fetchedValueAnalysis;
    private final NonNullableFieldWasNullException nonNullableFieldWasNullException;
    private final List<ExecutionResultNode> children;

    protected ExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                  NonNullableFieldWasNullException nonNullableFieldWasNullException,
                                  List<ExecutionResultNode> children) {
        this.fetchedValueAnalysis = fetchedValueAnalysis;
        this.nonNullableFieldWasNullException = nonNullableFieldWasNullException;
        this.children = assertNotNull(children);
        children.forEach(Assert::assertNotNull);
    }


    /*
     * can be null for the RootExecutionResultNode
     */
    public FetchedValueAnalysis getFetchedValueAnalysis() {
        return fetchedValueAnalysis;
    }

    public MergedField getMergedField() {
        return fetchedValueAnalysis.getExecutionStepInfo().getField();
    }

    public NonNullableFieldWasNullException getNonNullableFieldWasNullException() {
        return nonNullableFieldWasNullException;
    }

    public List<ExecutionResultNode> getChildren() {
        return new ArrayList<>(this.children);
    }

    public abstract ExecutionResultNode withNewChildren(List<ExecutionResultNode> children);

    public abstract ExecutionResultNode withNewFetchedValueAnalysis(FetchedValueAnalysis fetchedValueAnalysis);

    public Optional<NonNullableFieldWasNullException> getChildNonNullableException() {
        return children.stream()
                .filter(executionResultNode -> executionResultNode.getNonNullableFieldWasNullException() != null)
                .map(ExecutionResultNode::getNonNullableFieldWasNullException)
                .findFirst();
    }


}
