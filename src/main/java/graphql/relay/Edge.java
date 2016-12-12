package graphql.relay;


public class Edge {

    public Edge(Object node, DefaultConnectionCursor cursor) {
        this.node = node;
        this.cursor = cursor;
    }

    Object node;
    DefaultConnectionCursor cursor;

    public Object getNode() {
        return node;
    }

    public void setNode(Object node) {
        this.node = node;
    }

    public DefaultConnectionCursor getCursor() {
        return cursor;
    }

    public void setCursor(DefaultConnectionCursor cursor) {
        this.cursor = cursor;
    }
}
