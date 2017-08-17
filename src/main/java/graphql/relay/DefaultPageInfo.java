package graphql.relay;


import graphql.PublicApi;

@PublicApi
public class DefaultPageInfo implements PageInfo {

    private ConnectionCursor startCursor;
    private ConnectionCursor endCursor;
    private boolean hasPreviousPage;
    private boolean hasNextPage;

    /**
     * @deprecated prefer {@link #DefaultPageInfo(ConnectionCursor, ConnectionCursor, boolean, boolean)}
     */
    @Deprecated
    public DefaultPageInfo() {
    }

    public DefaultPageInfo(ConnectionCursor startCursor, ConnectionCursor endCursor, boolean hasPreviousPage, boolean hasNextPage) {
        this.startCursor = startCursor;
        this.endCursor = endCursor;
        this.hasPreviousPage = hasPreviousPage;
        this.hasNextPage = hasNextPage;
    }

    @Override
    public ConnectionCursor getStartCursor() {
        return startCursor;
    }

    /**
     * @param startCursor startCursor
     *
     * @deprecated prefer {@link #DefaultPageInfo(ConnectionCursor, ConnectionCursor, boolean, boolean)} and avoid mutation
     */
    @Deprecated
    public void setStartCursor(ConnectionCursor startCursor) {
        this.startCursor = startCursor;
    }

    @Override
    public ConnectionCursor getEndCursor() {
        return endCursor;
    }

    /**
     * @param endCursor endCursor
     *
     * @deprecated prefer {@link #DefaultPageInfo(ConnectionCursor, ConnectionCursor, boolean, boolean)} and avoid mutation
     */
    @Deprecated
    public void setEndCursor(ConnectionCursor endCursor) {
        this.endCursor = endCursor;
    }

    @Override
    public boolean isHasPreviousPage() {
        return hasPreviousPage;
    }

    /**
     * @param hasPreviousPage previous page
     *
     * @deprecated prefer {@link #DefaultPageInfo(ConnectionCursor, ConnectionCursor, boolean, boolean)} and avoid mutation
     */
    @Deprecated
    public void setHasPreviousPage(boolean hasPreviousPage) {
        this.hasPreviousPage = hasPreviousPage;
    }

    @Override
    public boolean isHasNextPage() {
        return hasNextPage;
    }

    /**
     * @param hasNextPage has next page
     *
     * @deprecated prefer {@link #DefaultPageInfo(ConnectionCursor, ConnectionCursor, boolean, boolean)}
     */
    @Deprecated
    public void setHasNextPage(boolean hasNextPage) {
        this.hasNextPage = hasNextPage;
    }

    @Override
    public String toString() {
        return "DefaultPageInfo{" +
                " startCursor=" + startCursor +
                ", endCursor=" + endCursor +
                ", hasPreviousPage=" + hasPreviousPage +
                ", hasNextPage=" + hasNextPage +
                '}';
    }
}
