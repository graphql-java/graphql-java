package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class IntValue extends AbstractNode implements Value {

    private final String valueText;

    public IntValue(final String valueText) {
        this.valueText = valueText;
    }

    public int getIntegerValue() {
        return Integer.parseInt(this.valueText);
    }

    public long getLongValue() {
        return Long.parseLong(this.valueText);
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<Node>();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IntValue intValue = (IntValue) o;

        return valueText.equals(intValue.valueText);

    }


    @Override
    public String toString() {
        return "IntValue{" +
                "value=" + valueText +
                '}';
    }
}
