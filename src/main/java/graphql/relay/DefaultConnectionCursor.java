package graphql.relay;

import graphql.Assert;
import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@PublicApi
@NullMarked
public class DefaultConnectionCursor implements ConnectionCursor {

    private final String value;

    public DefaultConnectionCursor(String value) {
        Assert.assertTrue(!value.isEmpty(), "connection value cannot be null or empty");
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultConnectionCursor that = (DefaultConnectionCursor) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
