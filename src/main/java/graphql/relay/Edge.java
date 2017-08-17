package graphql.relay;

import graphql.PublicApi;

/**
 * represents an edge in relay.
 */
@PublicApi
public interface Edge<T> {

    T getNode();

    ConnectionCursor getCursor();

}
