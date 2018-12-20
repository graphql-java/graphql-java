package graphql.language;

import graphql.PublicApi;

import java.util.Objects;

/**
 * Used in {@link AstBreadcrumb} to identify a specific child of a Node.
 */
@PublicApi
public class NodeLocation {

    private final String name;
    private final int index;

    public NodeLocation(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NodeLocation that = (NodeLocation) o;
        return index == that.index &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, index);
    }
}
