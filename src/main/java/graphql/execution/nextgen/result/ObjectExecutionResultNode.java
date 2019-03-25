package graphql.execution.nextgen.result;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.nextgen.FetchedValueAnalysis;

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
        super(fetchedValueAnalysis, ResultNodesUtil.newNullableException(fetchedValueAnalysis, children), children, errors);
    }


    @Override
    public ObjectExecutionResultNode withNewChildren(List<ExecutionResultNode> children) {
        return new ObjectExecutionResultNode(getFetchedValueAnalysis(), children, getErrors());
    }

    @Override
    public ExecutionResultNode withNewFetchedValueAnalysis(FetchedValueAnalysis fetchedValueAnalysis) {
        return new ObjectExecutionResultNode(fetchedValueAnalysis, getChildren(), getErrors());
    }
}
