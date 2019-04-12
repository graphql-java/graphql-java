package graphql.execution.nextgen.result;

import graphql.Assert;
import graphql.GraphQLError;
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
    private final List<GraphQLError> errors;
    private final Object context;

    protected ExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                  NonNullableFieldWasNullException nonNullableFieldWasNullException,
                                  List<ExecutionResultNode> children,
                                  List<GraphQLError> errors,
                                  Object context
    ) {
        this.fetchedValueAnalysis = fetchedValueAnalysis;
        this.nonNullableFieldWasNullException = nonNullableFieldWasNullException;
        this.children = assertNotNull(children);
        this.context = context;
        children.forEach(Assert::assertNotNull);
        this.errors = new ArrayList<>(errors);
    }

    public List<GraphQLError> getErrors() {
        return new ArrayList<>(errors);
    }

    /**
     * Each node has a context object associated with it
     *
     * @return the context associated with this node
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext() {
        return (T) context;
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

    public Optional<NonNullableFieldWasNullException> getChildNonNullableException() {
        return children.stream()
                .filter(executionResultNode -> executionResultNode.getNonNullableFieldWasNullException() != null)
                .map(ExecutionResultNode::getNonNullableFieldWasNullException)
                .findFirst();
    }

    /**
     * Creates a new ExecutionResultNode of the same specific type with the new set of result children
     *
     * @param children the new children for this result node
     *
     * @return a new ExecutionResultNode with the new result children
     */
    public abstract ExecutionResultNode withNewChildren(List<ExecutionResultNode> children);

    /**
     * Creates a new ExecutionResultNode of the same specific type with the new {@link graphql.execution.nextgen.FetchedValueAnalysis}
     *
     * @param fetchedValueAnalysis the {@link graphql.execution.nextgen.FetchedValueAnalysis} for this result node
     *
     * @return a new ExecutionResultNode with the new {@link graphql.execution.nextgen.FetchedValueAnalysis}
     */
    public abstract ExecutionResultNode withNewFetchedValueAnalysis(FetchedValueAnalysis fetchedValueAnalysis);

    /**
     * Creates a new ExecutionResultNode of the same specific type with the new error collection
     *
     * @param errors the new errors for this result node
     *
     * @return a new ExecutionResultNode with the new errors
     */
    public abstract ExecutionResultNode withNewErrors(List<GraphQLError> errors);

    /**
     * Creates a new ExecutionResultNode of the same specific type with a new context associated with it
     *
     * @param context the new context for this result node
     *
     * @return a new ExecutionResultNode with the new context
     */
    public abstract ExecutionResultNode withNewContext(Object context);


    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "fva=" + fetchedValueAnalysis +
                ", children=" + children +
                ", errors=" + errors +
                ", ctx=" + context +
                ", nonNullableEx=" + nonNullableFieldWasNullException +
                '}';
    }
}
