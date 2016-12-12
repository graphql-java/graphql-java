package graphql.relay;

/**
 * represents an edge in relay.
 */
public interface Edge {

    Object getNode();

    ConnectionCursor getCursor();

}
