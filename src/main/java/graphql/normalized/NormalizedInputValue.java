package graphql.normalized;

import java.util.Objects;

/**
 * A value with type information.
 */
public class NormalizedInputValue {
    private final String typeName;
    private final Object value;

    public NormalizedInputValue(String typeName, Object value) {
        this.typeName = typeName;
        this.value = value;
    }

    /**
     * This can be a wrapped type: e.g. [String!]!
     *
     * @return
     */
    public String getTypeName() {
        return typeName;
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


    public boolean isList() {
        return typeName.startsWith("[");
    }

    public String getUnwrappedTypeName() {
        String result = unwrapNonNull(typeName);
        while (result.startsWith("[")) {
            result = result.substring(1, result.length() - 2);
            result = unwrapNonNull(result);
        }
        return result;
    }

    private String unwrapNonNull(String string) {
        return string.endsWith("!") ? string.substring(0, string.length() - 2) : string;
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
        return Objects.equals(typeName, that.typeName) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeName, value);
    }

    @Override
    public String toString() {
        return "NormalizedInputValue{" +
                "typeName='" + typeName + '\'' +
                ", value=" + value +
                '}';
    }
}
