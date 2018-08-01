package graphql.language;


import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@PublicApi
public class AstZipper {

    private final Node curNode;
    // reverse: the breadCrumbs start from curNode upwards
    private final List<AstBreadcrumb> breadcrumbs;


    public AstZipper(Node curNode, List<AstBreadcrumb> breadcrumbs) {
        this.curNode = curNode;
        this.breadcrumbs = breadcrumbs;
    }

    public static AstZipper rootZipper(Node rootNode) {
        return new AstZipper(rootNode, new ArrayList<>());
    }

    public static class AstBreadcrumb {
        private final Node node;
        private final Location location;

        public AstBreadcrumb(Node node, Location location) {
            this.node = node;
            this.location = location;
        }
    }

    public static class Location {
        private final String childName;
        private final int position;

        public Location(String childName, int position) {
            this.childName = childName;
            this.position = position;
        }
    }


    public AstZipper modifyNode(Function<Node, Node> transform) {
        return new AstZipper(transform.apply(curNode), breadcrumbs);
    }

    public AstZipper changeLocation(Location newLocationInParent) {
        // validate position
        List<AstBreadcrumb> newBreadcrumbs = new ArrayList<>(breadcrumbs);
        AstBreadcrumb lastBreadcrumb = newBreadcrumbs.get(newBreadcrumbs.size() - 1);
        newBreadcrumbs.set(newBreadcrumbs.size() - 1, new AstBreadcrumb(lastBreadcrumb.node, newLocationInParent));
        return new AstZipper(curNode, newBreadcrumbs);
    }

    public AstZipper changeLocation(Location newLocationInParent, List<AstBreadcrumb> newBreadcrumbs) {
        // validate position
        return new AstZipper(curNode, newBreadcrumbs);
    }

    public AstZipper moveUp() {
        return null;
    }

    public Node toRoot() {
        Node curNode = this.curNode;
        for (AstBreadcrumb breadcrumb : breadcrumbs) {
            // just handle replace
            ChildrenContainer newChildren = breadcrumb.node.getNamedChildren();
            final Node newChild = curNode;
            newChildren = newChildren.transform(builder -> {
                Location location = breadcrumb.location;
                builder.replaceChild(location.childName, location.position, newChild);
            });
            curNode = breadcrumb.node.withNewChildren(newChildren);
        }
        return curNode;
    }


}

