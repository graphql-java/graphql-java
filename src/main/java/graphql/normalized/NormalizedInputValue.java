package graphql.normalized;

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
}
