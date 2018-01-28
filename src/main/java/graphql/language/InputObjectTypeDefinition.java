package graphql.language;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

public class InputObjectTypeDefinition extends AbstractNode<InputObjectTypeDefinition> implements TypeDefinition<InputObjectTypeDefinition> {
    private final String name;
    private Description description;
    private final List<Directive> directives;
    private final List<InputValueDefinition> inputValueDefinitions;

    public InputObjectTypeDefinition(String name) {
        this(name, new ArrayList<>(), new ArrayList<>());
    }

    public InputObjectTypeDefinition(String name, List<Directive> directives, List<InputValueDefinition> inputValueDefinitions) {
        this.name = name;
        this.directives = directives;
        this.inputValueDefinitions = inputValueDefinitions;
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

    public List<InputValueDefinition> getInputValueDefinitions() {
        return inputValueDefinitions;
    }

    @Override
    public String getName() {
        return name;
    }

    public Description getDescription() {
        return description;
    }

    public void setDescription(Description description) {
        this.description = description;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        result.addAll(inputValueDefinitions);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InputObjectTypeDefinition that = (InputObjectTypeDefinition) o;

         return NodeUtil.isEqualTo(this.name,that.name) ;
            }

    @Override
    public InputObjectTypeDefinition deepCopy() {
        return new InputObjectTypeDefinition(name,
                deepCopy(directives),
                deepCopy(inputValueDefinitions)
        );
    }

    @Override
    public String toString() {
        return "InputObjectTypeDefinition{" +
                "name='" + name + '\'' +
                ", directives=" + directives +
                ", inputValueDefinitions=" + inputValueDefinitions +
                '}';
    }

    @Override
    public <U> Object accept(U data, NodeVisitor<U> visitor) {
        return visitor.visit(this, data);
    }
}
