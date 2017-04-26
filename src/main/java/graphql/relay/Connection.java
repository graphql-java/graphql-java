package graphql.relay;

import java.util.List;

/**
 * represents a connection in relay.
 */
public interface Connection<T> {

    List<Edge<T>> getEdges();

    PageInfo getPageInfo();

}
