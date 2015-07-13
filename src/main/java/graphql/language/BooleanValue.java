package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class BooleanValue implements Value {

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BooleanValue that = (BooleanValue) o;

        return value == that.value;

    }

    @Override
    public int hashCode() {
        return (value ? 1 : 0);
    }

    @Override
    public String toString() {
        return "BooleanValue{" +
                "value=" + value +
                '}';
    }
}
