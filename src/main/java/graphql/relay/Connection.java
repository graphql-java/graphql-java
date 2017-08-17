package graphql.relay;

import graphql.PublicApi;

import java.util.List;

/**
 * represents a connection in relay.
 */
@PublicApi
public interface Connection<T> {

    List<Edge<T>> getEdges();

    PageInfo getPageInfo();

}
