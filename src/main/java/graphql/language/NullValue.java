package graphql.language;


import java.util.Collections;
import java.util.List;

public class NullValue extends AbstractNode<NullValue> implements Value<NullValue> {

    public static final NullValue Null = new NullValue();

    private NullValue() {
    }

    @Override
    public List<Node> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;

    }

    @Override
    public NullValue deepCopy() {
        return this;
    }

    @Override
    public String toString() {
        return "NullValue{" +
                '}';
    }

    @Override
    public <U> Object accept(U data, NodeVisitor<U> visitor) {
        return visitor.visit(this, data);
    }
}
