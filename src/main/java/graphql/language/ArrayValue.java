package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class ArrayValue extends AbstractNode implements Value {

    private List<Value> values = new ArrayList<>();

    public ArrayValue() {
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
        return new ArrayList<Node>(values);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArrayValue that = (ArrayValue) o;

        return !(values != null ? !values.equals(that.values) : that.values != null);

    }

    @Override
    public int hashCode() {
        return values != null ? values.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ArrayValue{" +
                "values=" + values +
                '}';
    }
}
