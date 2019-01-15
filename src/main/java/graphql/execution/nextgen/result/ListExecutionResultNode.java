package graphql.execution.nextgen.result;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.nextgen.FetchedValueAnalysis;
import graphql.util.NodeLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Internal
public class ListExecutionResultNode extends ExecutionResultNode {

    private final List<ExecutionResultNode> children;

    public ListExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                   List<ExecutionResultNode> children) {
        super(fetchedValueAnalysis, ResultNodesUtil.newNullableException(fetchedValueAnalysis, children));
        this.children = Assert.assertNotNull(children);
        children.forEach(Assert::assertNotNull);
    }

    public Optional<NonNullableFieldWasNullException> getChildNonNullableException() {
        return children.stream()
                .filter(executionResultNode -> executionResultNode.getNonNullableFieldWasNullException() != null)
                .map(ExecutionResultNode::getNonNullableFieldWasNullException)
                .findFirst();
    }

    @Override
    public List<ExecutionResultNode> getChildren() {
        return children;
    }

    @Override
    public Map<String, List<ExecutionResultNode>> getNamedChildren() {
        Map<String, List<ExecutionResultNode>> result = new LinkedHashMap<>();
        result.put(null, children);
        return result;
    }

    @Override
    public ExecutionResultNode withChild(ExecutionResultNode child, NodeLocation position) {
        List<ExecutionResultNode> newChildren = new ArrayList<>(this.children);
        newChildren.set(position.getIndex(), child);
        return new ListExecutionResultNode(getFetchedValueAnalysis(), newChildren);
    }

    @Override
    public ExecutionResultNode withNewChildren(Map<NodeLocation, ExecutionResultNode> newChildren) {
        List<ExecutionResultNode> mergedChildren = new ArrayList<>(this.children);
        newChildren.entrySet().forEach(entry -> mergedChildren.set(entry.getKey().getIndex(), entry.getValue()));

        return new ListExecutionResultNode(getFetchedValueAnalysis(), mergedChildren);
    }
}
