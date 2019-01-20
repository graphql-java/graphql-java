package graphql.execution.nextgen.result;

import graphql.PublicApi;
import graphql.util.NodeAdapter;
import graphql.util.NodeLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.execution.nextgen.result.ResultNodesUtil.index;
import static graphql.execution.nextgen.result.ResultNodesUtil.key;

@PublicApi
public class ResultNodeAdapter implements NodeAdapter<ExecutionResultNode> {

    public static final ResultNodeAdapter RESULT_NODE_ADAPTER = new ResultNodeAdapter();

    private ResultNodeAdapter() {

    }

    @Override
    public Map<String, List<ExecutionResultNode>> getNamedChildren(ExecutionResultNode node) {
        return node.getNamedChildren();
    }

    @Override
    public ExecutionResultNode withNewChildren(ExecutionResultNode node, Map<String, List<ExecutionResultNode>> newChildren) {
        Map<NodeLocation, ExecutionResultNode> adaptedChildren = new LinkedHashMap<>();
        if (newChildren.size() == 1) {
            String key = newChildren.keySet().iterator().next();
            if (key == null) {
                List<ExecutionResultNode> list = newChildren.get(null);
                for (int i = 0; i < list.size(); i++) {
                    adaptedChildren.put(index(i), list.get(i));
                }
            } else {
                newChildren.forEach((name, list) -> {
                    adaptedChildren.put(key(name), list.get(0));
                });
            }
        } else {
            newChildren.forEach((name, list) -> {
                adaptedChildren.put(key(name), list.get(0));
            });
        }
        return node.withNewChildren(adaptedChildren);
    }

    @Override
    public ExecutionResultNode removeChild(ExecutionResultNode node, NodeLocation location) {
        throw new UnsupportedOperationException();
    }
}
