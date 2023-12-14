package graphql.normalized.incremental;

import graphql.ExperimentalApi;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Holds the value of the 'label' argument of a defer directive.
 * <p>
 * 'value' can be 'null', as 'label' is not a required argument on the '@defer' directive.
 */
@ExperimentalApi
public class DeferLabel {
    private final String value;

    public DeferLabel(@Nullable String value) {
        this.value = value;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DeferLabel) {
            return Objects.equals(this.value, ((DeferLabel) obj).value);
        }

        return false;
    }
}
