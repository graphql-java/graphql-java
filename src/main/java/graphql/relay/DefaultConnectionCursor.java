package graphql.relay;

public class DefaultConnectionCursor implements ConnectionCursor {

    private final String value;

    public DefaultConnectionCursor(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("connection value cannot be null or empty");
        }
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultConnectionCursor that = (DefaultConnectionCursor) o;
        if (value != null ? !value.equals(that.value) : that.value != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }

    @Override
    public String toString() {
        return value;
    }
}
