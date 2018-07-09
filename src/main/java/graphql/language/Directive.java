package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.argumentsByName;

public class Directive extends AbstractNode<Directive> implements NamedNode<Directive> {
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

    @Override
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

    public static Builder newDirective() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private List<Argument> arguments = new ArrayList<>();

        private Builder() {
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder arguments(List<Argument> arguments) {
            this.arguments = arguments;
            return this;
        }

        public Directive build() {
            Directive directive = new Directive(name, arguments);
            directive.setSourceLocation(sourceLocation);
            directive.setComments(comments);
            return directive;
        }
    }
}
