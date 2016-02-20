package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>ArrayValue class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ArrayValue extends AbstractNode implements Value {

    private List<Value> values = new ArrayList<Value>();

    /**
     * <p>Constructor for ArrayValue.</p>
     */
    public ArrayValue() {
    }

    /**
     * <p>Constructor for ArrayValue.</p>
     *
     * @param values a {@link java.util.List} object.
     */
    public ArrayValue(List<Value> values) {
        this.values = values;
    }

    /**
     * <p>Getter for the field <code>values</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<Value> getValues() {
        return values;
    }

    /**
     * <p>Setter for the field <code>values</code>.</p>
     *
     * @param values a {@link java.util.List} object.
     */
    public void setValues(List<Value> values) {
        this.values = values;
    }


    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        return new ArrayList<Node>(values);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ArrayValue{" +
                "values=" + values +
                '}';
    }
}
