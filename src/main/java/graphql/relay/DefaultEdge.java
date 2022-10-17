package graphql.relay;

import graphql.PublicApi;

import java.util.Objects;

import static graphql.Assert.assertNotNull;

@PublicApi
public class DefaultEdge<T> implements Edge<T> {

    private final T node;
    private final ConnectionCursor cursor;

    public DefaultEdge(T node, ConnectionCursor cursor) {
        this.cursor = assertNotNull(cursor, () -> "cursor cannot be null");
        this.node = node;
    }


    @Override
    public T getNode() {
        return node;
    }

    @Override
    public ConnectionCursor getCursor() {
        return cursor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultEdge that = (DefaultEdge) o;
        return Objects.equals(node, that.node) && Objects.equals(cursor, that.cursor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, cursor);
    }

    @Override
    public String toString() {
        return "DefaultEdge{" +
                "node=" + node +
                ", cursor=" + cursor +
                '}';
    }
}
