package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>StringValue class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class StringValue extends AbstractNode implements Value {

    private String value;

    /**
     * <p>Constructor for StringValue.</p>
     *
     * @param value a {@link java.lang.String} object.
     */
    public StringValue(String value) {
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

    /**
     * <p>Setter for the field <code>value</code>.</p>
     *
     * @param value a {@link java.lang.String} object.
     */
    public void setValue(String value) {
        this.value = value;
    }

    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "StringValue{" +
                "value='" + value + '\'' +
                '}';
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringValue that = (StringValue) o;

        return !(value != null ? !value.equals(that.value) : that.value != null);

    }

}
