package graphql.language;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>FloatValue class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class FloatValue extends AbstractNode implements Value {

    private BigDecimal value;

    /**
     * <p>Constructor for FloatValue.</p>
     *
     * @param value a {@link java.math.BigDecimal} object.
     */
    public FloatValue(BigDecimal value) {
        this.value = value;
    }

    /**
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a {@link java.math.BigDecimal} object.
     */
    public BigDecimal getValue() {
        return value;
    }

    /**
     * <p>Setter for the field <code>value</code>.</p>
     *
     * @param value a {@link java.math.BigDecimal} object.
     */
    public void setValue(BigDecimal value) {
        this.value = value;
    }


    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "FloatValue{" +
                "value=" + value +
                '}';
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FloatValue that = (FloatValue) o;

        return !(value != null ? !value.equals(that.value) : that.value != null);

    }

}
