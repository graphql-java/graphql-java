package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class VariableDefinition extends AbstractNode {

    private String name;
    private Type type;
    private Value defaultValue;

    public VariableDefinition() {

    }


    public VariableDefinition(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public VariableDefinition(String name, Type type, Value defaultValue) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public Value getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Value defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(type);
        if (defaultValue != null) result.add(defaultValue);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VariableDefinition that = (VariableDefinition) o;

        return !(name != null ? !name.equals(that.name) : that.name != null);

    }


    @Override
    public String toString() {
        return "VariableDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", defaultValue=" + defaultValue +
                '}';
    }
}
