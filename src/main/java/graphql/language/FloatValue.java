package graphql.language;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class FloatValue extends AbstractNode implements Value {

    private BigDecimal value;

    public FloatValue(BigDecimal value) {
        this.value = value;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }


    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public String toString() {
        return "FloatValue{" +
                "value=" + value +
                '}';
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FloatValue that = (FloatValue) o;

        return !(value != null ? !value.equals(that.value) : that.value != null);

    }

}
