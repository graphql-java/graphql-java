package graphql.execution.nextgen.result;

import com.google.common.collect.ImmutableList;
import graphql.Assert;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.NonNullableFieldWasNullException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static graphql.Assert.assertNotNull;

/**
 * @deprecated Jan 2022 - We have decided to deprecate the NextGen engine, and it will be removed in a future release.
 */
@Deprecated
@Internal
public abstract class ExecutionResultNode {

    private final ExecutionStepInfo executionStepInfo;
    private final ResolvedValue resolvedValue;
    private final NonNullableFieldWasNullException nonNullableFieldWasNullException;
    private final ImmutableList<ExecutionResultNode> children;
    private final ImmutableList<GraphQLError> errors;

    /*
     * we are trusting here the the children list is not modified on the outside (no defensive copy)
     */
    protected ExecutionResultNode(ExecutionStepInfo executionStepInfo,
                                  ResolvedValue resolvedValue,
                                  NonNullableFieldWasNullException nonNullableFieldWasNullException,
                                  List<ExecutionResultNode> children,
                                  List<GraphQLError> errors) {
        this.resolvedValue = resolvedValue;
        this.executionStepInfo = executionStepInfo;
        this.nonNullableFieldWasNullException = nonNullableFieldWasNullException;
        this.children = ImmutableList.copyOf(assertNotNull(children));
        children.forEach(Assert::assertNotNull);
        this.errors = ImmutableList.copyOf(errors);
    }

    public List<GraphQLError> getErrors() {
        return new ArrayList<>(errors);
    }

    /*
     * can be null for the RootExecutionResultNode
     */
    public ResolvedValue getResolvedValue() {
        return resolvedValue;
    }

    public MergedField getMergedField() {
        return executionStepInfo.getField();
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo;
    }

    public NonNullableFieldWasNullException getNonNullableFieldWasNullException() {
        return nonNullableFieldWasNullException;
    }

    public List<ExecutionResultNode> getChildren() {
        return this.children;
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

    public abstract ExecutionResultNode withNewResolvedValue(ResolvedValue resolvedValue);

    public abstract ExecutionResultNode withNewExecutionStepInfo(ExecutionStepInfo executionStepInfo);


    /**
     * Creates a new ExecutionResultNode of the same specific type with the new error collection
     *
     * @param errors the new errors for this result node
     *
     * @return a new ExecutionResultNode with the new errors
     */
    public abstract ExecutionResultNode withNewErrors(List<GraphQLError> errors);


    @Override
    public String toString() {
        return "ExecutionResultNode{" +
                "executionStepInfo=" + executionStepInfo +
                ", resolvedValue=" + resolvedValue +
                ", nonNullableFieldWasNullException=" + nonNullableFieldWasNullException +
                ", children=" + children +
                ", errors=" + errors +
                '}';
    }
}
