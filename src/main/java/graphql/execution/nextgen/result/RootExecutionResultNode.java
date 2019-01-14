package graphql.execution.nextgen.result;

import graphql.execution.nextgen.FetchedValueAnalysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RootExecutionResultNode extends ObjectExecutionResultNode {

    public RootExecutionResultNode(Map<String, ExecutionResultNode> children) {
        super(null, children);
    }

    public RootExecutionResultNode(List<NamedResultNode> children) {
        super(null, children);
    }

    @Override
    public FetchedValueAnalysis getFetchedValueAnalysis() {
        throw new RuntimeException("Root node");
    }

    @Override
    public ExecutionResultNode withNewChildren(Map<ExecutionResultNodePosition, ExecutionResultNode> children) {
        LinkedHashMap<String, ExecutionResultNode> mergedChildren = new LinkedHashMap<>(getChildrenMap());
        children.entrySet().stream().forEach(entry -> mergedChildren.put(entry.getKey().getKey(), entry.getValue()));
        return new RootExecutionResultNode(mergedChildren);
    }

    @Override
    public ExecutionResultNode withChild(ExecutionResultNode child, ExecutionResultNodePosition position) {
        LinkedHashMap<String, ExecutionResultNode> newChildren = new LinkedHashMap<>(getChildrenMap());
        newChildren.put(position.getKey(), child);
        return new RootExecutionResultNode(newChildren);
    }
}
