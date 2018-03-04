package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.argumentsByName;

public class Directive extends AbstractNode<Directive> {
    private final String name;
    private final List<Argument> arguments = new ArrayList<>();

    public Directive(String name) {
        this(name, Collections.emptyList());
    }

    public Directive(String name, List<Argument> arguments) {
        this.name = name;
        this.arguments.addAll(arguments);
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public Map<String, Argument> getArgumentsByName() {
        // the spec says that args MUST be unique within context
        return argumentsByName(arguments);
    }

    public Argument getArgument(String argumentName) {
        return getArgumentsByName().get(argumentName);
    }

    public String getName() {
        return name;
    }


    @Override
    public List<Node> getChildren() {
        return new ArrayList<>(arguments);
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Directive that = (Directive) o;

        return NodeUtil.isEqualTo(this.name, that.name);

    }

    @Override
    public Directive deepCopy() {
        return new Directive(name, deepCopy(arguments));
    }

    @Override
    public String toString() {
        return "Directive{" +
                "name='" + name + '\'' +
                ", arguments=" + arguments +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitDirective(this, context);
    }
}
