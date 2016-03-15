package graphql.relay;


public class PageInfo {
    private ConnectionCursor startCursor;
    private ConnectionCursor endCursor;
    private boolean hasPreviousPage;
    private boolean hasNextPage;

    public ConnectionCursor getStartCursor() {
        return startCursor;
    }

    public void setStartCursor(ConnectionCursor startCursor) {
        this.startCursor = startCursor;
    }

    public ConnectionCursor getEndCursor() {
        return endCursor;
    }

    public void setEndCursor(ConnectionCursor endCursor) {
        this.endCursor = endCursor;
    }

    public boolean isHasPreviousPage() {
        return hasPreviousPage;
    }

    public void setHasPreviousPage(boolean hasPreviousPage) {
        this.hasPreviousPage = hasPreviousPage;
    }

    public boolean isHasNextPage() {
        return hasNextPage;
    }

    public void setHasNextPage(boolean hasNextPage) {
        this.hasNextPage = hasNextPage;
    }
}
