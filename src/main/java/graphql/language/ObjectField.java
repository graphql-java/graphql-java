package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class ObjectField extends AbstractNode {

    private String name;
    private Value value;

    public ObjectField(String name, Value value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public Value getValue() {
        return value;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(value);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectField that = (ObjectField) o;

        return !(name != null ? !name.equals(that.name) : that.name != null);

    }

    @Override
    public String toString() {
        return "ObjectField{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}
