package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>BooleanValue class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class BooleanValue extends AbstractNode implements Value {

    private boolean value;

    /**
     * <p>Constructor for BooleanValue.</p>
     *
     * @param value a boolean.
     */
    public BooleanValue(boolean value) {
        this.value = value;
    }

    /**
     * <p>isValue.</p>
     *
     * @return a boolean.
     */
    public boolean isValue() {
        return value;
    }

    /**
     * <p>Setter for the field <code>value</code>.</p>
     *
     * @param value a boolean.
     */
    public void setValue(boolean value) {
        this.value = value;
    }


    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        return new ArrayList<Node>();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BooleanValue that = (BooleanValue) o;

        return value == that.value;

    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "BooleanValue{" +
                "value=" + value +
                '}';
    }
}
