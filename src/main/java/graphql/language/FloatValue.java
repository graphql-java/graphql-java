package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class FloatValue extends AbstractNode<FloatValue> implements Value<FloatValue> {

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

    @Override
    public FloatValue deepCopy() {
        return new FloatValue(value);
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitFloatValue(this, context);
    }
}
