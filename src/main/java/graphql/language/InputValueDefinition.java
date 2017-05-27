package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class InputValueDefinition extends AbstractNode {
    private String name;
    private Type type;
    private Value defaultValue;
    private List<Directive> directives = new ArrayList<>();

    public InputValueDefinition(String name) {
        this(name, null);
    }

    public InputValueDefinition(String name, Type type) {
        this(name, type, null);
    }
    public InputValueDefinition(String name, Type type, Value defaultValue) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public Value getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Value defaultValue) {
        this.defaultValue = defaultValue;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(type);
        result.add(defaultValue);
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InputValueDefinition that = (InputValueDefinition) o;

        if ( null == name ) {
            if ( null != that.name ) return false;
        } else if ( !name.equals(that.name) ) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return "InputValueDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", defaultValue=" + defaultValue +
                ", directives=" + directives +
                '}';
    }
}
