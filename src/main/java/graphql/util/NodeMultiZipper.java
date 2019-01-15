package graphql.util;

import graphql.Assert;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@PublicApi
public class NodeMultiZipper<T> {

    private final T commonRoot;
    private final List<NodeZipper<T>> zippers;
    private final NodeAdapter<T> nodeAdapter;

    public NodeMultiZipper(T commonRoot, List<NodeZipper<T>> zippers, NodeAdapter<T> nodeAdapter) {
        this.commonRoot = assertNotNull(commonRoot);
        this.zippers = new ArrayList<>(zippers);
        this.nodeAdapter = nodeAdapter;
    }

    public T toRootNode() {
        if (zippers.size() == 0) {
            return commonRoot;
        }

        List<NodeZipper<T>> curZippers = new ArrayList<>(zippers);
        while (curZippers.size() > 1) {

            List<NodeZipper<T>> deepestZippers = getDeepestZippers(curZippers);
            Map<T, List<NodeZipper<T>>> sameParent = zipperWithSameParent(deepestZippers);

            List<NodeZipper<T>> newZippers = new ArrayList<>();
            for (Map.Entry<T, List<NodeZipper<T>>> entry : sameParent.entrySet()) {
                NodeZipper<T> newZipper = moveUp(entry.getKey(), entry.getValue());
                Optional<NodeZipper<T>> zipperToBeReplaced = curZippers.stream().filter(zipper -> zipper.getCurNode() == entry.getKey()).findFirst();
                zipperToBeReplaced.ifPresent(curZippers::remove);
                newZippers.add(newZipper);
            }
            curZippers.removeAll(deepestZippers);
            curZippers.addAll(newZippers);
        }
        assertTrue(curZippers.size() == 1, "unexpected state: all zippers must share the same root node");
        return Assert.assertNotNull(curZippers.get(0).toRoot());
    }

    public T getCommonRoot() {
        return commonRoot;
    }

    public List<NodeZipper<T>> getZippers() {
        return new ArrayList<>(zippers);
    }

    public NodeZipper<T> getZipperForNode(T node) {
        return FpKit.findOneOrNull(zippers, zipper -> zipper.getCurNode() == node);
    }

    public NodeMultiZipper<T> withReplacedZippers(List<NodeZipper<T>> zippers) {
        return new NodeMultiZipper<>(commonRoot, zippers, this.nodeAdapter);
    }


    public NodeMultiZipper<T> withNewZipper(NodeZipper<T> newZipper) {
        List<NodeZipper<T>> newZippers = getZippers();
        newZippers.add(newZipper);
        return new NodeMultiZipper<>(commonRoot, newZippers, this.nodeAdapter);
    }

    public NodeMultiZipper<T> withReplacedZipper(NodeZipper<T> oldZipper, NodeZipper<T> newZipper) {
        int index = zippers.indexOf(oldZipper);
        assertTrue(index >= 0, "oldZipper not found");
        List<NodeZipper<T>> newZippers = new ArrayList<>(zippers);
        newZippers.set(index, newZipper);
        return new NodeMultiZipper<>(commonRoot, newZippers, this.nodeAdapter);
    }


    private List<NodeZipper<T>> getDeepestZippers(List<NodeZipper<T>> zippers) {
        Map<Integer, List<NodeZipper<T>>> grouped = zippers
                .stream()
                .collect(groupingBy(astZipper -> astZipper.getBreadcrumbs().size(), LinkedHashMap::new, mapping(Function.identity(), toList())));

        Integer maxLevel = Collections.max(grouped.keySet());
        return grouped.get(maxLevel);
    }

    private NodeZipper<T> moveUp(T parent, List<NodeZipper<T>> sameParent) {
        assertNotEmpty(sameParent, "expected at least one zipper");
        Map<String, List<T>> childrenMap = nodeAdapter.getNamedChildren(parent);

        for (NodeZipper<T> zipper : sameParent) {
            NodeLocation location = zipper.getBreadcrumbs().get(0).getLocation();
            childrenMap.computeIfAbsent(location.getName(), (key) -> new ArrayList<>());
            List<T> childrenList = childrenMap.get(location.getName());
            if (childrenList.size() > location.getIndex()) {
                childrenList.set(location.getIndex(), zipper.getCurNode());
            } else {
                childrenList.add(zipper.getCurNode());
            }
        }
        T newNode = nodeAdapter.withNewChildren(parent, childrenMap);
        List<Breadcrumb<T>> newBreadcrumbs = sameParent.get(0).getBreadcrumbs().subList(1, sameParent.get(0).getBreadcrumbs().size());
        return new NodeZipper<>(newNode, newBreadcrumbs, this.nodeAdapter);
    }

    private Map<T, List<NodeZipper<T>>> zipperWithSameParent(List<NodeZipper<T>> zippers) {
        return FpKit.groupingBy(zippers, NodeZipper::getParent);
    }
}
