package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class ArrayValue extends AbstractNode<ArrayValue> implements Value<ArrayValue> {

    private List<Value> values = new ArrayList<>();

    public ArrayValue() {
        this(new ArrayList<>());
    }

    public ArrayValue(List<Value> values) {
        this.values = values;
    }

    public List<Value> getValues() {
        return values;
    }

    public void setValues(List<Value> values) {
        this.values = values;
    }


    @Override
    public List<Node> getChildren() {
        return new ArrayList<>(values);
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;
    }

    @Override
    public String toString() {
        return "ArrayValue{" +
                "values=" + values +
                '}';
    }

    @Override
    public ArrayValue deepCopy() {
        return new ArrayValue(deepCopy(values));
    }

    @Override
    public <U> Object accept(U data, NodeVisitor<U> visitor) {
        return visitor.visit(this, data);
    }
}
