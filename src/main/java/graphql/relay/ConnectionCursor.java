package graphql.relay;


/**
 * <p>ConnectionCursor class.</p>
 *
 * @author Andreas Marek
 * @version v1.3
 */
public class ConnectionCursor {

    private final String value;

    /**
     * <p>Constructor for ConnectionCursor.</p>
     *
     * @param value a {@link java.lang.String} object.
     */
    public ConnectionCursor(String value) {
        this.value = value;
    }

    /**
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getValue() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {


        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConnectionCursor that = (ConnectionCursor) o;

        if (value != null ? !value.equals(that.value) : that.value != null) return false;

        return true;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return value;
    }
}
