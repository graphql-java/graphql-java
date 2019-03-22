package graphql.execution.nextgen.result;

import graphql.GraphQLError;
import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RootExecutionResultNode extends ObjectExecutionResultNode {

    private final List<GraphQLError> errors;

    public RootExecutionResultNode(List<ExecutionResultNode> children, List<GraphQLError> errors) {
        super(null, children);
        this.errors = new ArrayList<>(errors);
    }

    public RootExecutionResultNode(List<ExecutionResultNode> children) {
        super(null, children);
        this.errors = Collections.emptyList();
    }

    @Override
    public FetchedValueAnalysis getFetchedValueAnalysis() {
        throw new RuntimeException("Root node");
    }

    @Override
    public ObjectExecutionResultNode withNewChildren(List<ExecutionResultNode> children) {
        return new RootExecutionResultNode(children);
    }

    public List<GraphQLError> getErrors() {
        return new ArrayList<>(errors);
    }
}
