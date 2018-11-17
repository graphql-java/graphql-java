package graphql.execution2.result;

import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution2.FetchedValueAnalysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ObjectExecutionResultNode extends ExecutionResultNode {

    private Map<String, ExecutionResultNode> children;

    public ObjectExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                     Map<String, ExecutionResultNode> children) {
        super(fetchedValueAnalysis, ResultNodesUtil.newNullableException(fetchedValueAnalysis, children.values()));
        this.children = children;
    }

    @Override
    public List<ExecutionResultNode> getChildren() {
        return new ArrayList<>(children.values());
    }

    @Override
    public ExecutionResultNode withChild(ExecutionResultNode child, ExecutionResultNodePosition position) {
        LinkedHashMap<String, ExecutionResultNode> newChildren = new LinkedHashMap<>(this.children);
        newChildren.put(position.getKey(), child);
        return new ObjectExecutionResultNode(getFetchedValueAnalysis(), newChildren);
    }

    @Override
    public ExecutionResultNode withNewChildren(Map<ExecutionResultNodePosition, ExecutionResultNode> children) {
        LinkedHashMap<String, ExecutionResultNode> mergedChildren = new LinkedHashMap<>(this.children);
        children.entrySet().stream().forEach(entry -> mergedChildren.put(entry.getKey().getKey(), entry.getValue()));
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

    public ObjectExecutionResultNode withChildren(Map<String, ExecutionResultNode> children) {
        return new ObjectExecutionResultNode(getFetchedValueAnalysis(), children);
    }

    public static class UnresolvedObjectResultNode extends ObjectExecutionResultNode {

        public UnresolvedObjectResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
            super(fetchedValueAnalysis, Collections.emptyMap());
        }

        @Override
        public String toString() {
            return "UnresolvedObjectResultNode{" +
                    "fetchedValueAnalysis=" + getFetchedValueAnalysis() +
                    '}';
        }
    }

    public static class RootExecutionResultNode extends ObjectExecutionResultNode {

        public RootExecutionResultNode(Map<String, ExecutionResultNode> children) {
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
            return new ObjectExecutionResultNode.RootExecutionResultNode(mergedChildren);
        }

        @Override
        public ExecutionResultNode withChild(ExecutionResultNode child, ExecutionResultNodePosition position) {
            LinkedHashMap<String, ExecutionResultNode> newChildren = new LinkedHashMap<>(getChildrenMap());
            newChildren.put(position.getKey(), child);
            return new ObjectExecutionResultNode.RootExecutionResultNode(newChildren);
        }
    }

}
