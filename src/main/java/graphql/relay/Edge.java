package graphql.relay;


/**
 * <p>Edge class.</p>
 *
 * @author Andreas Marek
 * @version v1.3
 */
public class Edge {

    /**
     * <p>Constructor for Edge.</p>
     *
     * @param node a {@link java.lang.Object} object.
     * @param cursor a {@link graphql.relay.ConnectionCursor} object.
     */
    public Edge(Object node, ConnectionCursor cursor) {
        this.node = node;
        this.cursor = cursor;
    }

    Object node;
    ConnectionCursor cursor;

    /**
     * <p>Getter for the field <code>node</code>.</p>
     *
     * @return a {@link java.lang.Object} object.
     */
    public Object getNode() {
        return node;
    }

    /**
     * <p>Setter for the field <code>node</code>.</p>
     *
     * @param node a {@link java.lang.Object} object.
     */
    public void setNode(Object node) {
        this.node = node;
    }

    /**
     * <p>Getter for the field <code>cursor</code>.</p>
     *
     * @return a {@link graphql.relay.ConnectionCursor} object.
     */
    public ConnectionCursor getCursor() {
        return cursor;
    }

    /**
     * <p>Setter for the field <code>cursor</code>.</p>
     *
     * @param cursor a {@link graphql.relay.ConnectionCursor} object.
     */
    public void setCursor(ConnectionCursor cursor) {
        this.cursor = cursor;
    }
}
