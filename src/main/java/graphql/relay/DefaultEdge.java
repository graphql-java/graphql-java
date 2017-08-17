package graphql.relay;

import graphql.PublicApi;

import static graphql.Assert.assertNotNull;

@PublicApi
public class DefaultEdge<T> implements Edge<T> {

    public DefaultEdge(T node, ConnectionCursor cursor) {
        this.node = assertNotNull(node, "node cannot be null");
        this.cursor = assertNotNull(cursor, "cursor cannot be null");
    }

    /**
     * @deprecated prefer {@link #DefaultEdge(Object, ConnectionCursor)}
     */
    @Deprecated
    public DefaultEdge() {
    }

    private T node;
    private ConnectionCursor cursor;

    @Override
    public T getNode() {
        return node;
    }

    /**
     * @param node node
     *
     * @deprecated prefer {@link #DefaultEdge(Object, ConnectionCursor)} and avoid mutation.
     */
    @Deprecated
    public void setNode(T node) {
        this.node = node;
    }

    @Override
    public ConnectionCursor getCursor() {
        return cursor;
    }

    /**
     * @param cursor cursor
     *
     * @deprecated prefer {@link #DefaultEdge(Object, ConnectionCursor)} and avoid mutation.
     */
    @Deprecated
    public void setCursor(ConnectionCursor cursor) {
        this.cursor = cursor;
    }

    @Override
    public String toString() {
        return "DefaultEdge{" +
                "node=" + node +
                ", cursor=" + cursor +
                '}';
    }
}
