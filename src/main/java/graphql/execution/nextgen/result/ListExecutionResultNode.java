package graphql.execution.nextgen.result;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
public class ListExecutionResultNode extends ExecutionResultNode {

    public ListExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                   List<ExecutionResultNode> children) {
        this(fetchedValueAnalysis, children, Collections.emptyList());

    }

    public ListExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                   List<ExecutionResultNode> children,
                                   List<GraphQLError> errors) {
        super(fetchedValueAnalysis, ResultNodesUtil.newNullableException(fetchedValueAnalysis, children), children, errors);
    }

    @Override
    public ExecutionResultNode withNewChildren(List<ExecutionResultNode> children) {
        return new ListExecutionResultNode(getFetchedValueAnalysis(), children, getErrors());
    }

    @Override
    public ExecutionResultNode withNewFetchedValueAnalysis(FetchedValueAnalysis fetchedValueAnalysis) {
        return new ListExecutionResultNode(fetchedValueAnalysis, getChildren(), getErrors());
    }

    @Override
    public ExecutionResultNode withNewErrors(List<GraphQLError> errors) {
        return new ListExecutionResultNode(getFetchedValueAnalysis(), getChildren(), new ArrayList<>(errors));
    }
}
