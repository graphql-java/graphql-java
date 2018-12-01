package graphql.language;


import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;
import static graphql.language.AstBreadcrumb.Location;

@PublicApi
public class AstZipper {

    private final Node curNode;
    // reverse: the breadCrumbs start from curNode upwards
    private final List<AstBreadcrumb> breadcrumbs;


    public AstZipper(Node curNode, List<AstBreadcrumb> breadcrumbs) {
        this.curNode = curNode;
        this.breadcrumbs = assertNotNull(breadcrumbs);
    }

    public Node getCurNode() {
        return curNode;
    }

    public List<AstBreadcrumb> getBreadcrumbs() {
        return new ArrayList<>(breadcrumbs);
    }

    public Node getParent() {
        return breadcrumbs.get(0).getNode();
    }

    public static AstZipper rootZipper(Node rootNode) {
        return new AstZipper(rootNode, new ArrayList<>());
    }


    public AstZipper modifyNode(Function<Node, Node> transform) {
        return new AstZipper(transform.apply(curNode), breadcrumbs);
    }

    public AstZipper withNewNode(Node newNode) {
        return new AstZipper(newNode, breadcrumbs);
    }

    public AstZipper changeLocation(Location newLocationInParent) {
        // validate position
        List<AstBreadcrumb> newBreadcrumbs = new ArrayList<>(breadcrumbs);
        AstBreadcrumb lastBreadcrumb = newBreadcrumbs.get(newBreadcrumbs.size() - 1);
        newBreadcrumbs.set(newBreadcrumbs.size() - 1, new AstBreadcrumb(lastBreadcrumb.getNode(), newLocationInParent));
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
            ChildrenContainer newChildren = breadcrumb.getNode().getNamedChildren();
            final Node newChild = curNode;
            newChildren = newChildren.transform(builder -> {
                Location location = breadcrumb.getLocation();
                builder.replaceChild(location.getName(), location.getIndex(), newChild);
            });
            curNode = breadcrumb.getNode().withNewChildren(newChildren);
        }
        return curNode;
    }


}

