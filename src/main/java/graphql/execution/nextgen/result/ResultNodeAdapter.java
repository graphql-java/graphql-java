package graphql.execution.nextgen.result;

import graphql.Internal;
import graphql.util.NodeAdapter;
import graphql.util.NodeLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

/**
 * @deprecated Jan 2022 - We have decided to deprecate the NextGen engine, and it will be removed in a future release.
 */
@Deprecated
@Internal
public class ResultNodeAdapter implements NodeAdapter<ExecutionResultNode> {

    public static final ResultNodeAdapter RESULT_NODE_ADAPTER = new ResultNodeAdapter();

    private ResultNodeAdapter() {

    }

    @Override
    public Map<String, List<ExecutionResultNode>> getNamedChildren(ExecutionResultNode parentNode) {
        return Collections.singletonMap(null, parentNode.getChildren());
    }

    @Override
    public ExecutionResultNode withNewChildren(ExecutionResultNode parentNode, Map<String, List<ExecutionResultNode>> newChildren) {
        assertTrue(newChildren.size() == 1);
        List<ExecutionResultNode> childrenList = newChildren.get(null);
        assertNotNull(childrenList);
        return parentNode.withNewChildren(childrenList);
    }

    @Override
    public ExecutionResultNode removeChild(ExecutionResultNode parentNode, NodeLocation location) {
        int index = location.getIndex();
        List<ExecutionResultNode> childrenList = new ArrayList<>(parentNode.getChildren());
        assertTrue(index >= 0 && index < childrenList.size(), () -> "The remove index MUST be within the range of the children");
        childrenList.remove(index);
        return parentNode.withNewChildren(childrenList);
    }
}
