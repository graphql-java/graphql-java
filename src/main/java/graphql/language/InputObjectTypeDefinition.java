package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;

public class InputObjectTypeDefinition extends AbstractNode<InputObjectTypeDefinition> implements TypeDefinition<InputObjectTypeDefinition>, DirectivesContainer<InputObjectTypeDefinition> {
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

        return NodeUtil.isEqualTo(this.name, that.name);
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
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitInputObjectTypeDefinition(this, context);
    }
}
