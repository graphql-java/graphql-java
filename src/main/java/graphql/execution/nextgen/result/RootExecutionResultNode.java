package graphql.execution.nextgen.result;

import graphql.GraphQLError;
import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertShouldNeverHappen;

public class RootExecutionResultNode extends ObjectExecutionResultNode {

    public RootExecutionResultNode(List<ExecutionResultNode> children) {
        this(children, Collections.emptyList());
    }

    public RootExecutionResultNode(List<ExecutionResultNode> children, List<GraphQLError> errors) {
        this(children, errors, null);
    }

    private RootExecutionResultNode(List<ExecutionResultNode> children, List<GraphQLError> errors, Object context) {
        super(null, children, errors, context);
    }


    @Override
    public FetchedValueAnalysis getFetchedValueAnalysis() {
        return assertShouldNeverHappen("not supported at root node");
    }

    @Override
    public RootExecutionResultNode withNewChildren(List<ExecutionResultNode> children) {
        return new RootExecutionResultNode(children, getErrors(), getContext());
    }

    @Override
    public RootExecutionResultNode withNewFetchedValueAnalysis(FetchedValueAnalysis fetchedValueAnalysis) {
        return assertShouldNeverHappen("not supported at root node");
    }

    @Override
    public ExecutionResultNode withNewErrors(List<GraphQLError> errors) {
        return new RootExecutionResultNode(getChildren(), new ArrayList<>(errors), getContext());
    }

    @Override
    public ExecutionResultNode withNewContext(Object context) {
        return new RootExecutionResultNode(getChildren(), getErrors(), context);
    }
}
