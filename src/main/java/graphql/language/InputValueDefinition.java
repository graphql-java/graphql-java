package graphql.language;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

public class InputValueDefinition extends AbstractNode<InputValueDefinition> {
    private final String name;
    private Type type;
    private Value defaultValue;
    private Description description;
    private final List<Directive> directives;

    public InputValueDefinition(String name) {
        this(name, null, null, new ArrayList<>());
    }

    public InputValueDefinition(String name, Type type) {
        this(name, type, null, new ArrayList<>());
    }

    public InputValueDefinition(String name, Type type, Value defaultValue) {
        this(name, type, defaultValue, new ArrayList<>());
    }

    public InputValueDefinition(String name, Type type, Value defaultValue, List<Directive> directives) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.directives = directives;
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

    public Description getDescription() {
        return description;
    }

    public void setDescription(Description description) {
        this.description = description;
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

    public Map<String, Directive> getDirectivesByName() {
        return directivesByName(directives);
    }

    public Directive getDirective(String directiveName) {
        return getDirectivesByName().get(directiveName);
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

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public InputValueDefinition deepCopy() {
        return new InputValueDefinition(name,
                deepCopy(type),
                deepCopy(defaultValue),
                deepCopy(directives)
        );
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

    @Override
    public <U> Object accept(U data, NodeVisitor<U> visitor) {
        return visitor.visit(this, data);
    }
}
