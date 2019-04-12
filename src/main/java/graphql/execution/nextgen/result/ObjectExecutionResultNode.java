package graphql.execution.nextgen.result;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
public class ObjectExecutionResultNode extends ExecutionResultNode {


    public ObjectExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                     List<ExecutionResultNode> children) {
        this(fetchedValueAnalysis, children, Collections.emptyList());

    }

    public ObjectExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                     List<ExecutionResultNode> children,
                                     List<GraphQLError> errors) {
        this(fetchedValueAnalysis, children, errors, null);
    }

    protected ObjectExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                        List<ExecutionResultNode> children,
                                        List<GraphQLError> errors,
                                        Object context) {
        super(fetchedValueAnalysis, ResultNodesUtil.newNullableException(fetchedValueAnalysis, children), children, errors, context);
    }


    @Override
    public ObjectExecutionResultNode withNewChildren(List<ExecutionResultNode> children) {
        return new ObjectExecutionResultNode(getFetchedValueAnalysis(), children, getErrors(), getContext());
    }

    @Override
    public ExecutionResultNode withNewFetchedValueAnalysis(FetchedValueAnalysis fetchedValueAnalysis) {
        return new ObjectExecutionResultNode(fetchedValueAnalysis, getChildren(), getErrors(), getContext());
    }

    @Override
    public ExecutionResultNode withNewErrors(List<GraphQLError> errors) {
        return new ObjectExecutionResultNode(getFetchedValueAnalysis(), getChildren(), new ArrayList<>(errors), getContext());
    }

    @Override
    public ExecutionResultNode withNewContext(Object context) {
        return new ObjectExecutionResultNode(getFetchedValueAnalysis(), getChildren(), getErrors(), context);
    }
}
