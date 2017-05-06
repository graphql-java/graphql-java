package graphql.relay;

public class DefaultEdge<T> implements Edge<T> {

    public DefaultEdge(T node, ConnectionCursor cursor) {
        if (node == null) {
            throw new IllegalArgumentException("node cannot be null");
        }
        if (cursor == null) {
            throw new IllegalArgumentException("cursor cannot be null");
        }
        this.node = node;
        this.cursor = cursor;
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
     * @deprecated prefer {@link #DefaultEdge(Object, ConnectionCursor)} and avoid mutation.
     * @param  node node
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
     * @deprecated prefer {@link #DefaultEdge(Object, ConnectionCursor)} and avoid mutation.
     * @param cursor cursor
     */
    @Deprecated
    public void setCursor(ConnectionCursor cursor) {
        this.cursor = cursor;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultEdge{");
        sb.append("node=").append(node);
        sb.append(", cursor=").append(cursor);
        sb.append('}');
        return sb.toString();
    }
}
