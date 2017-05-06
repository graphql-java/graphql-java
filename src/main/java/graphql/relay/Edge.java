package graphql.relay;

/**
 * represents an edge in relay.
 */
public interface Edge<T> {

    T getNode();

    ConnectionCursor getCursor();

}
