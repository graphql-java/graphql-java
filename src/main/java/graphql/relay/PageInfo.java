package graphql.relay;

import graphql.PublicApi;

/**
 * represents a page in relay.
 */
@PublicApi
public interface PageInfo {

    /**
     * @return cursor to the first edge, or null if this page is empty.
     */
    ConnectionCursor getStartCursor();

    /**
     * @return cursor to the last edge, or null if this page is empty.
     */
    ConnectionCursor getEndCursor();

    /**
     * @return true if and only if this page is not the first page. only meaningful when you gave {@code last} argument.
     */
    boolean isHasPreviousPage();

    /**
     * @return true if and only if this page is not the last page. only meaningful when you gave {@code first} argument.
     */
    boolean isHasNextPage();
}
