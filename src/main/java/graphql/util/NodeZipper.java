package graphql.util;

import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;

@PublicApi
public class NodeZipper<T> {

    private final T curNode;
    private final NodeAdapter<T> nodeAdapter;
    // reverse: the breadCrumbs start from curNode upwards
    private final List<Breadcrumb<T>> breadcrumbs;


    public NodeZipper(T curNode, List<Breadcrumb<T>> breadcrumbs, NodeAdapter<T> nodeAdapter) {
        this.curNode = assertNotNull(curNode);
        this.breadcrumbs = assertNotNull(breadcrumbs);
        this.nodeAdapter = nodeAdapter;
    }

    public T getCurNode() {
        return curNode;
    }

    public List<Breadcrumb<T>> getBreadcrumbs() {
        return new ArrayList<>(breadcrumbs);
    }

    public T getParent() {
        return breadcrumbs.get(0).getNode();
    }

    public static <T> NodeZipper<T> rootZipper(T rootNode, NodeAdapter<T> nodeAdapter) {
        return new NodeZipper<T>(rootNode, new ArrayList<>(), nodeAdapter);
    }

    public NodeZipper<T> modifyNode(Function<T, T> transform) {
        return new NodeZipper<T>(transform.apply(curNode), breadcrumbs, nodeAdapter);
    }

    public NodeZipper<T> withNewNode(T newNode) {
        return new NodeZipper<T>(newNode, breadcrumbs, nodeAdapter);
    }

    public NodeZipper<T> moveUp() {
        T node = getParent();
        List<Breadcrumb<T>> newBreadcrumbs = breadcrumbs.subList(1, breadcrumbs.size());
        return new NodeZipper<>(node, newBreadcrumbs, nodeAdapter);
    }

    public T toRoot() {
        T curNode = this.curNode;
        for (Breadcrumb<T> breadcrumb : breadcrumbs) {
            // just handle replace
            Map<String, List<T>> newChildren = nodeAdapter.getNamedChildren(breadcrumb.getNode());
            final T newChild = curNode;
            NodeLocation location = breadcrumb.getLocation();
            newChildren.get(location.getName()).set(location.getIndex(), newChild);
            curNode = nodeAdapter.withNewChildren(breadcrumb.getNode(), newChildren);
        }
        return curNode;
    }


}
