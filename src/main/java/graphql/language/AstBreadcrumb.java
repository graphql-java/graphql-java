package graphql.language;

import graphql.PublicApi;

import java.util.Objects;

@PublicApi
public class AstBreadcrumb {

    private final Node node;
    private final NodeLocation location;

    public AstBreadcrumb(Node node, NodeLocation location) {
        this.node = node;
        this.location = location;
    }

    public Node getNode() {
        return node;
    }

    public NodeLocation getLocation() {
        return location;
    }


    @Override
    public String toString() {
        return "AstBreadcrumb{" +
                "node=" + node +
                ", location=" + location +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AstBreadcrumb that = (AstBreadcrumb) o;
        return Objects.equals(node, that.node) &&
                Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, location);
    }
}

