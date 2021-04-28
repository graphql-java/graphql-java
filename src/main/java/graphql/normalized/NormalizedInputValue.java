package graphql.normalized;

import java.util.Objects;

public class NormalizedInputValue {
    private final String type;
    private final Object value;

    public NormalizedInputValue(String type, Object value) {
        this.type = type;
        this.value = value;
    }

    // this can be a wrapped type String
    public String getType() {
        return type;
    }

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
