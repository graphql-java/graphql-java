package graphql.language;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

public class FieldDefinition extends AbstractNode<FieldDefinition> {
    private final String name;
    private Type type;
    private Description description;
    private final List<InputValueDefinition> inputValueDefinitions;
    private final List<Directive> directives;

    public FieldDefinition(String name) {
        this(name, null, new ArrayList<>(), new ArrayList<>());
    }

    public FieldDefinition(String name, Type type) {
        this(name, type, new ArrayList<>(), new ArrayList<>());
    }

    public FieldDefinition(String name, Type type, List<InputValueDefinition> inputValueDefinitions, List<Directive> directives) {
        this.name = name;
        this.type = type;
        this.inputValueDefinitions = inputValueDefinitions;
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

    public List<InputValueDefinition> getInputValueDefinitions() {
        return inputValueDefinitions;
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
        result.addAll(inputValueDefinitions);
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FieldDefinition that = (FieldDefinition) o;

         return NodeUtil.isEqualTo(this.name,that.name) ;
    }

    @Override
    public FieldDefinition deepCopy() {
        return new FieldDefinition(name,
                deepCopy(type),
                deepCopy(inputValueDefinitions),
                deepCopy(directives)
        );
    }

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", inputValueDefinitions=" + inputValueDefinitions +
                ", directives=" + directives +
                '}';
    }

    @Override
    public <U> Object accept(U data, NodeVisitor<U> visitor) {
        return visitor.visit(this, data);
    }
}
