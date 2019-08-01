package benchmark.vavr;

import graphql.AssertException;
import graphql.PublicApi;
import graphql.util.Breadcrumb;
import graphql.util.NodeLocation;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Traversable;
import io.vavr.control.Option;

import java.util.function.Predicate;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static java.lang.String.format;

@PublicApi
public class NodeMultiZipper<T> {

    private final T commonRoot;
    private final List<NodeZipper<T>> zippers;
    private final NodeAdapter<T> nodeAdapter;

    public NodeMultiZipper(T commonRoot, List<NodeZipper<T>> zippers, NodeAdapter<T> nodeAdapter) {
        this.commonRoot = assertNotNull(commonRoot);
        this.zippers = List.ofAll(zippers);
        this.nodeAdapter = nodeAdapter;
    }

    /**
     * @return can be null if the root node is marked as deleted
     */
    public T toRootNode() {
        if (zippers.size() == 0) {
            return commonRoot;
        }

        List<NodeZipper<T>> curZippers = List.ofAll(zippers);
        while (curZippers.size() > 1) {

            List<NodeZipper<T>> deepestZippers = getDeepestZippers(curZippers);
            Map<T, List<NodeZipper<T>>> sameParent = zipperWithSameParent(deepestZippers);

            List<NodeZipper<T>> newZippers = List.empty();

            for (Tuple2<T, List<NodeZipper<T>>> entry : sameParent) {
                NodeZipper<T> newZipper = moveUp(entry._1, entry._2);
                Option<NodeZipper<T>> zipperToBeReplaced = curZippers.find(zipper -> zipper.getCurNode() == entry._1());
                if (zipperToBeReplaced.isDefined()) {
                    curZippers = curZippers.remove(zipperToBeReplaced.get());
                }
                newZippers = newZippers.append(newZipper);

            }
            curZippers = curZippers.removeAll(deepestZippers);
            curZippers = curZippers.appendAll(newZippers);
        }
        assertTrue(curZippers.size() == 1, "unexpected state: all zippers must share the same root node");
        return curZippers.get(0).toRoot();
    }

    public T getCommonRoot() {
        return commonRoot;
    }

    public List<NodeZipper<T>> getZippers() {
        return zippers;
    }

    public NodeZipper<T> getZipperForNode(T node) {
        //return FpKit.findOneOrNull(zippers, zipper -> zipper.getCurNode() == node);
        return zippers.find(zipper -> zipper.getCurNode() == node).getOrNull();
    }

    public NodeMultiZipper<T> withReplacedZippers(List<NodeZipper<T>> zippers) {
        return new NodeMultiZipper<>(commonRoot, zippers, this.nodeAdapter);
    }


    public NodeMultiZipper<T> withNewZipper(NodeZipper<T> newZipper) {
        List<NodeZipper<T>> newZippers = getZippers();
        newZippers = newZippers.append(newZipper);

        return new NodeMultiZipper<>(commonRoot, newZippers, this.nodeAdapter);
    }

    public NodeMultiZipper<T> withReplacedZipper(NodeZipper<T> oldZipper, NodeZipper<T> newZipper) {
        int index = zippers.indexOf(oldZipper);
        assertTrue(index >= 0, "oldZipper not found");
        List<NodeZipper<T>> newZippers = zippers;
        newZippers = newZippers.update(index, newZipper);
        return new NodeMultiZipper<>(commonRoot, newZippers, this.nodeAdapter);
    }

    public static <T> int findIndex(List<T> list, Predicate<T> filter) {
        for (int i = 0; i < list.size(); i++) {
            if (filter.test(list.get(i))) {
                return i;
            }
        }
        return -1;
    }


    public NodeMultiZipper<T> withReplacedZipperForNode(T currentNode, T newNode) {
        int index = findIndex(zippers, zipper -> zipper.getCurNode() == currentNode);
        assertTrue(index >= 0, "No current zipper found for provided node");
        NodeZipper<T> newZipper = zippers.get(index).withNewNode(newNode);
        List<NodeZipper<T>> newZippers = zippers;
        newZippers = newZippers.update(index, newZipper);
        return new NodeMultiZipper<>(commonRoot, newZippers, this.nodeAdapter);
    }


    private List<NodeZipper<T>> getDeepestZippers(List<NodeZipper<T>> zippers) {

        //Map<Integer, List<NodeZipper<T>>> grouped = FpKit.groupingBy(zippers, astZipper -> astZipper.getBreadcrumbs().size());

        Map<Integer, List<NodeZipper<T>>> grouped = zippers.groupBy(astZipper -> astZipper.getBreadcrumbs().size());

        Integer maxLevel = grouped.keySet().max().get();
        return grouped.get(maxLevel).get();
    }

    public static <T> Traversable<T> assertNotEmpty(Traversable<T> collection, String format, Object... args) {
        if (collection == null || collection.isEmpty()) {
            throw new AssertException(format(format, args));
        }
        return collection;
    }

    private NodeZipper<T> moveUp(T parent, List<NodeZipper<T>> sameParent) {
        assertNotEmpty(sameParent, "expected at least one zipper");

        Map<String, List<T>> childrenMap = nodeAdapter.getNamedChildren(parent);
        Map<String, Integer> indexCorrection = LinkedHashMap.empty();

        sameParent = sameParent.sorted((zipper1, zipper2) -> {
            int index1 = zipper1.getBreadcrumbs().get(0).getLocation().getIndex();
            int index2 = zipper2.getBreadcrumbs().get(0).getLocation().getIndex();
            if (index1 != index2) {
                return Integer.compare(index1, index2);
            }
            NodeZipper.ModificationType modificationType1 = zipper1.getModificationType();
            NodeZipper.ModificationType modificationType2 = zipper2.getModificationType();

            // same index can never be deleted and changed at the same time

            if (modificationType1 == modificationType2) {
                return 0;
            }

            // always first replacing the node
            if (modificationType1 == NodeZipper.ModificationType.REPLACE) {
                return -1;
            }
            // and then INSERT_BEFORE before INSERT_AFTER
            return modificationType1 == NodeZipper.ModificationType.INSERT_BEFORE ? -1 : 1;

        });

        for (NodeZipper<T> zipper : sameParent) {
            NodeLocation location = zipper.getBreadcrumbs().get(0).getLocation();
            Integer ixDiff = indexCorrection.getOrElse(location.getName(), 0);
            int ix = location.getIndex() + ixDiff;
            String name = location.getName();
            List<T> children = childrenMap.get(name).get();
            switch (zipper.getModificationType()) {
                case REPLACE:
                    children = children.update(ix, zipper.getCurNode());
                    break;
                case DELETE:
                    children = children.removeAt(ix);
                    indexCorrection.put(name, ixDiff - 1);
                    break;
                case INSERT_BEFORE:
                    children = children.insert(ix, zipper.getCurNode());
                    indexCorrection = indexCorrection.put(name, ixDiff + 1);
                    break;
                case INSERT_AFTER:
                    children = children.insert(ix + 1, zipper.getCurNode());
                    indexCorrection = indexCorrection.put(name, ixDiff + 1);
                    break;
            }
            childrenMap = childrenMap.put(name, children);
        }

        T newNode = nodeAdapter.withNewChildren(parent, childrenMap);
        List<Breadcrumb<T>> newBreadcrumbs = sameParent.get(0).getBreadcrumbs().subSequence(1, sameParent.get(0).getBreadcrumbs().size());
        return new NodeZipper<>(newNode, newBreadcrumbs, this.nodeAdapter);
    }

    private Map<T, List<NodeZipper<T>>> zipperWithSameParent(List<NodeZipper<T>> zippers) {
        //return FpKit.groupingBy(zippers, NodeZipper::getParent);
        return zippers.groupBy(NodeZipper::getParent);
    }
}
