package benchmark;

import graphql.Assert;
import graphql.util.NodeAdapter;
import graphql.util.NodeLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

public class JNodes {

    public static abstract class JNode {
        private final List<JNode> children;

        public JNode(List<JNode> children) {
            this.children = children;
        }

        public List<JNode> getChildren() {
            return children;
        }

        public abstract JNode withNewChildren(List<JNode> children);

        @Override
        public String toString() {
            return "JNode{" +
                    "children=" + children +
                    '}';
        }
    }


    public static class JLeafNode extends JNode {
        private final Object value;

        public JLeafNode(Object value) {
            super(Collections.emptyList());
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public JNode withNewChildren(List<JNode> children) {
            return Assert.assertShouldNeverHappen();
        }

        @Override
        public String toString() {
            return "JLeafNode{" +
                    "value=" + value +
                    '}';
        }
    }

    public static class JObjectNode extends JNode {
        public JObjectNode(List<JNode> children) {
            super(children);
        }

        @Override
        public JNode withNewChildren(List<JNode> children) {
            return new JObjectNode(children);
        }

    }

    public static class JNodeAdapter implements NodeAdapter<JNode> {

        public static final JNodeAdapter JNODE_ADAPTER = new JNodeAdapter();

        private JNodeAdapter() {

        }

        @Override
        public Map<String, List<JNode>> getNamedChildren(JNode parentNode) {
            Map<String, List<JNode>> result = new LinkedHashMap<>();
            result.put(null, parentNode.getChildren());
            return result;
        }

        @Override
        public JNode withNewChildren(JNode parentNode, Map<String, List<JNode>> newChildren) {
            assertTrue(newChildren.size() == 1);
            List<JNode> childrenList = newChildren.get(null);
            assertNotNull(childrenList);
            return parentNode.withNewChildren(childrenList);
        }

        @Override
        public JNode removeChild(JNode parentNode, NodeLocation location) {
            int index = location.getIndex();
            List<JNode> childrenList = parentNode.getChildren();
            assertTrue(index >= 0 && index < childrenList.size(), "The remove index MUST be within the range of the children");
            childrenList.remove(index);
            return parentNode.withNewChildren(childrenList);
        }
    }
}
