package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class IntValue extends AbstractNode implements Value {

    private int value;

    public IntValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
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

        IntValue intValue = (IntValue) o;

        return value == intValue.value;

    }


    @Override
    public String toString() {
        return "IntValue{" +
                "value=" + value +
                '}';
    }
}
