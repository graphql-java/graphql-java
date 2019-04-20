package graphql.execution.nextgen.result;

import graphql.Assert;
import graphql.PublicApi;
import graphql.util.NodeAdapter;
import graphql.util.NodeLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PublicApi
public class ResultNodeAdapter implements NodeAdapter<ExecutionResultNode> {

    public static final ResultNodeAdapter RESULT_NODE_ADAPTER = new ResultNodeAdapter();

    private ResultNodeAdapter() {

    }

    @Override
    public Map<String, List<ExecutionResultNode>> getNamedChildren(ExecutionResultNode node) {
        Map<String, List<ExecutionResultNode>> result = new LinkedHashMap<>();
        result.put(null, node.getChildren());
        return result;
    }

    @Override
    public ExecutionResultNode withNewChildren(ExecutionResultNode node, Map<String, List<ExecutionResultNode>> newChildren) {
        Assert.assertTrue(newChildren.size() == 1);
        List<ExecutionResultNode> childrenList = newChildren.get(null);
        Assert.assertNotNull(childrenList);
        return node.withNewChildren(childrenList);
    }

    @Override
    public ExecutionResultNode removeChild(ExecutionResultNode node, NodeLocation location) {
        throw new UnsupportedOperationException();
    }
}
