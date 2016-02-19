package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>IntValue class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class IntValue extends AbstractNode implements Value {

    private int value;

    /**
     * <p>Constructor for IntValue.</p>
     *
     * @param value a int.
     */
    public IntValue(int value) {
        this.value = value;
    }

    /**
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a int.
     */
    public int getValue() {
        return value;
    }

    /**
     * <p>Setter for the field <code>value</code>.</p>
     *
     * @param value a int.
     */
    public void setValue(int value) {
        this.value = value;
    }

    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IntValue intValue = (IntValue) o;

        return value == intValue.value;

    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "IntValue{" +
                "value=" + value +
                '}';
    }
}
