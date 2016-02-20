package graphql.relay;


/**
 * <p>PageInfo class.</p>
 *
 * @author Andreas Marek
 * @version v1.3
 */
public class PageInfo {
    private ConnectionCursor startCursor;
    private ConnectionCursor endCursor;
    private  boolean hasPreviousPage;
    private  boolean hasNextPage;

    /**
     * <p>Getter for the field <code>startCursor</code>.</p>
     *
     * @return a {@link graphql.relay.ConnectionCursor} object.
     */
    public ConnectionCursor getStartCursor() {
        return startCursor;
    }

    /**
     * <p>Setter for the field <code>startCursor</code>.</p>
     *
     * @param startCursor a {@link graphql.relay.ConnectionCursor} object.
     */
    public void setStartCursor(ConnectionCursor startCursor) {
        this.startCursor = startCursor;
    }

    /**
     * <p>Getter for the field <code>endCursor</code>.</p>
     *
     * @return a {@link graphql.relay.ConnectionCursor} object.
     */
    public ConnectionCursor getEndCursor() {
        return endCursor;
    }

    /**
     * <p>Setter for the field <code>endCursor</code>.</p>
     *
     * @param endCursor a {@link graphql.relay.ConnectionCursor} object.
     */
    public void setEndCursor(ConnectionCursor endCursor) {
        this.endCursor = endCursor;
    }

    /**
     * <p>isHasPreviousPage.</p>
     *
     * @return a boolean.
     */
    public boolean isHasPreviousPage() {
        return hasPreviousPage;
    }

    /**
     * <p>Setter for the field <code>hasPreviousPage</code>.</p>
     *
     * @param hasPreviousPage a boolean.
     */
    public void setHasPreviousPage(boolean hasPreviousPage) {
        this.hasPreviousPage = hasPreviousPage;
    }

    /**
     * <p>isHasNextPage.</p>
     *
     * @return a boolean.
     */
    public boolean isHasNextPage() {
        return hasNextPage;
    }

    /**
     * <p>Setter for the field <code>hasNextPage</code>.</p>
     *
     * @param hasNextPage a boolean.
     */
    public void setHasNextPage(boolean hasNextPage) {
        this.hasNextPage = hasNextPage;
    }
}
