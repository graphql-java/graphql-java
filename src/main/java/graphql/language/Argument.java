package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class Argument extends AbstractNode<Argument> {

    private final String name;
    private final Value value;

    public Argument(String name, Value value) {
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

        Argument that = (Argument) o;

        return isEqualTo(this.name, that.name);

    }

    @Override
    public Argument deepCopy() {
        return new Argument(name, deepCopy(value, Value::deepCopy));
    }

    @Override
    public String toString() {
        return "Argument{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }

}
