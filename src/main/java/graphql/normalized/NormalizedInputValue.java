package graphql.normalized;

import java.util.Objects;

/**
 * A value with type information.
 */
public class NormalizedInputValue {
    private final String type;
    private final Object value;

    public NormalizedInputValue(String type, Object value) {
        this.type = type;
        this.value = value;
    }

    /**
     * This can be a wrapped type: e.g. [String!]!
     *
     * @return
     */
    public String getType() {
        return type;
    }

    /**
     * Depending on the type it returns:
     * Scalar or Enum: the value of the Scalar.
     * InputObject: the value is a map of field-name to NormalizedInputValue
     * List of Scalar/Enum/InputObject (or even List of List ..)
     *
     * @return
     */
    public Object getValue() {
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
        NormalizedInputValue that = (NormalizedInputValue) o;
        return Objects.equals(type, that.type) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return "NormalizedInputValue{" +
                "type='" + type + '\'' +
                ", value=" + value +
                '}';
    }
}
