package benchmark;

import benchmark.vavr.NodeAdapter;
import graphql.Assert;
import graphql.util.NodeLocation;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

public class VNodes {

    public static abstract class VNode {
        private final List<VNode> children;

        public VNode(List<VNode> children) {
            this.children = children;
        }

        public List<VNode> getChildren() {
            return children;
        }

        public abstract VNode withNewChildren(List<VNode> children);

        @Override
        public String toString() {
            return "VNode{" +
                    "children=" + children +
                    '}';
        }
    }


    public static class VLeafNode extends VNode {
        private final Object value;

        public VLeafNode(Object value) {
            super(List.empty());
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public VNode withNewChildren(List<VNode> children) {
            return Assert.assertShouldNeverHappen();
        }

        @Override
        public String toString() {
            return "VLeafNode{" +
                    "value=" + value +
                    '}';
        }
    }

    public static class VObjectNode extends VNode {
        public VObjectNode(List<VNode> children) {
            super(children);
        }

        @Override
        public VNode withNewChildren(List<VNode> children) {
            return new VObjectNode(children);
        }

    }

    public static class VNodeAdapter implements NodeAdapter<VNode> {

        public static final VNodeAdapter VNODE_ADAPTER = new VNodeAdapter();

        private VNodeAdapter() {

        }

        @Override
        public Map<String, List<VNode>> getNamedChildren(VNode parentNode) {
            Map<String, List<VNode>> result = LinkedHashMap.empty();
            result = result.put(null, parentNode.getChildren());
            return result;
        }

        @Override
        public VNode withNewChildren(VNode parentNode, Map<String, List<VNode>> newChildren) {
            assertTrue(newChildren.size() == 1);
            List<VNode> childrenList = newChildren.get(null).get();
            assertNotNull(childrenList);
            return parentNode.withNewChildren(childrenList);
        }

        @Override
        public VNode removeChild(VNode parentNode, NodeLocation location) {
            int index = location.getIndex();
            List<VNode> childrenList = parentNode.getChildren();
            assertTrue(index >= 0 && index < childrenList.size(), "The remove index MUST be within the range of the children");
            childrenList = childrenList.removeAt(index);
            return parentNode.withNewChildren(childrenList);
        }
    }
}
