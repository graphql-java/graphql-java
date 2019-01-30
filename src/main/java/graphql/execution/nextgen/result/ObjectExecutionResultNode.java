package graphql.execution.nextgen.result;

import graphql.Internal;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.nextgen.FetchedValueAnalysis;
import graphql.util.NodeLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Internal
public class ObjectExecutionResultNode extends ExecutionResultNode {

    private Map<String, ExecutionResultNode> children;

    public ObjectExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                     Map<String, ExecutionResultNode> children) {
        super(fetchedValueAnalysis, ResultNodesUtil.newNullableException(fetchedValueAnalysis, children.values()));
        this.children = children;
    }

    public ObjectExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                     List<NamedResultNode> children) {
        super(fetchedValueAnalysis, ResultNodesUtil.newNullableException(fetchedValueAnalysis, children));
        this.children = ResultNodesUtil.namedNodesToMap(children);
    }

    @Override
    public List<ExecutionResultNode> getChildren() {
        return new ArrayList<>(children.values());
    }

    @Override
    public Map<String, List<ExecutionResultNode>> getNamedChildren() {
        Map<String, List<ExecutionResultNode>> result = new LinkedHashMap<>();
        children.forEach((key, node) -> result.put(key, Arrays.asList(node)));
        return result;
    }

    @Override
    public ExecutionResultNode withChild(ExecutionResultNode child, NodeLocation position) {
        LinkedHashMap<String, ExecutionResultNode> newChildren = new LinkedHashMap<>(this.children);
        newChildren.put(position.getName(), child);
        return new ObjectExecutionResultNode(getFetchedValueAnalysis(), newChildren);
    }

    @Override
    public ExecutionResultNode withNewChildren(Map<NodeLocation, ExecutionResultNode> children) {
        LinkedHashMap<String, ExecutionResultNode> mergedChildren = new LinkedHashMap<>(this.children);
        children.entrySet().stream().forEach(entry -> mergedChildren.put(entry.getKey().getName(), entry.getValue()));
        return new ObjectExecutionResultNode(getFetchedValueAnalysis(), mergedChildren);
    }

    public Map<String, ExecutionResultNode> getChildrenMap() {
        return new LinkedHashMap<>(children);
    }

    public Optional<NonNullableFieldWasNullException> getChildrenNonNullableException() {
        return children.values().stream()
                .filter(executionResultNode -> executionResultNode.getNonNullableFieldWasNullException() != null)
                .map(ExecutionResultNode::getNonNullableFieldWasNullException)
                .findFirst();
    }

    public ObjectExecutionResultNode withChildren(List<NamedResultNode> children) {
        return new ObjectExecutionResultNode(getFetchedValueAnalysis(), children);
    }


}
