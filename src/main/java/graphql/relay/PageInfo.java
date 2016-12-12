package graphql.relay;

/**
 * represents a page in relay.
 */
public interface PageInfo {

    ConnectionCursor getStartCursor();

    ConnectionCursor getEndCursor();

    boolean isHasPreviousPage();

    boolean isHasNextPage();

}
