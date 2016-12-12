package graphql.relay;

public class DefaultEdge implements Edge {

    public DefaultEdge(Object node, DefaultConnectionCursor cursor) {
        this.node = node;
        this.cursor = cursor;
    }

    private Object node;
    private ConnectionCursor cursor;

    @Override
    public Object getNode() {
        return node;
    }

    public void setNode(Object node) {
        this.node = node;
    }

    @Override
    public ConnectionCursor getCursor() {
        return cursor;
    }

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
