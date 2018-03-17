package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;

public class BooleanValue extends AbstractNode<BooleanValue> implements Value<BooleanValue> {

    private boolean value;

    public BooleanValue(boolean value) {
        this.value = value;
    }

    public boolean isValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }


    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BooleanValue that = (BooleanValue) o;

        return value == that.value;

    }

    @Override
    public BooleanValue deepCopy() {
        return new BooleanValue(value);
    }

    @Override
    public String toString() {
        return "BooleanValue{" +
                "value=" + value +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitBooleanValue(this, context);
    }
}
