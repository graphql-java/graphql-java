package graphql.relay;

import java.util.List;

/**
 * represents a connection in relay.
 */
public interface Connection {

    List<Edge> getEdges();

    PageInfo getPageInfo();

}
