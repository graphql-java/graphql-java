package graphql.util;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * A specific {@link NodeLocation} inside a node. This means  {@link #getNode()} returns a Node which has a child
 * at {@link #getLocation()}
 * <p>
 * A list of Breadcrumbs is used to identify the exact location of a specific node inside a tree.
 *
 * @param <T> the generic type of object
 */
@PublicApi
@NullMarked
public class Breadcrumb<T> {

    private final T node;
    private final NodeLocation location;

    public Breadcrumb(T node, NodeLocation location) {
        this.node = node;
        this.location = location;
    }

    public T getNode() {
        return node;
    }

    public NodeLocation getLocation() {
        return location;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Breadcrumb<?> that = (Breadcrumb<?>) o;
        return Objects.equals(node, that.node) &&
                Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(node);
        result = 31 * result + Objects.hashCode(location);
        return result;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "[", "]")
                .add("" + location)
                .add("" + node)
                .toString();
    }
}
