package graphql.relay;

public class DefaultPageInfo implements PageInfo {

    private ConnectionCursor startCursor;
    private ConnectionCursor endCursor;
    private boolean hasPreviousPage;
    private boolean hasNextPage;

    @Override
    public ConnectionCursor getStartCursor() {
        return startCursor;
    }

    public void setStartCursor(ConnectionCursor startCursor) {
        this.startCursor = startCursor;
    }

    @Override
    public ConnectionCursor getEndCursor() {
        return endCursor;
    }

    public void setEndCursor(ConnectionCursor endCursor) {
        this.endCursor = endCursor;
    }

    @Override
    public boolean isHasPreviousPage() {
        return hasPreviousPage;
    }

    public void setHasPreviousPage(boolean hasPreviousPage) {
        this.hasPreviousPage = hasPreviousPage;
    }

    @Override
    public boolean isHasNextPage() {
        return hasNextPage;
    }

    public void setHasNextPage(boolean hasNextPage) {
        this.hasNextPage = hasNextPage;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultPageInfo{");
        sb.append("startCursor=").append(startCursor);
        sb.append(", endCursor=").append(endCursor);
        sb.append(", hasPreviousPage=").append(hasPreviousPage);
        sb.append(", hasNextPage=").append(hasNextPage);
        sb.append('}');
        return sb.toString();
    }
}
