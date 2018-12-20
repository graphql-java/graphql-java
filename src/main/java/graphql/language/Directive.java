package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;
import static graphql.language.NodeUtil.argumentsByName;

@PublicApi
public class Directive extends AbstractNode<Directive> implements NamedNode<Directive> {
    private final String name;
    private final List<Argument> arguments = new ArrayList<>();

    public static final String CHILD_ARGUMENTS = "arguments";

    @Internal
    protected Directive(String name, List<Argument> arguments, SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars) {
        super(sourceLocation, comments, ignoredChars);
        this.name = name;
        this.arguments.addAll(arguments);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name      of the directive
     * @param arguments of the directive
     */
    public Directive(String name, List<Argument> arguments) {
        this(name, arguments, null, new ArrayList<>(), IgnoredChars.EMPTY);
    }


    /**
     * alternative to using a Builder for convenience
     *
     * @param name of the directive
     */
    public Directive(String name) {
        this(name, new ArrayList<>(), null, new ArrayList<>(), IgnoredChars.EMPTY);
    }

    public List<Argument> getArguments() {
        return new ArrayList<>(arguments);
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
    public NodeChildrenContainer getNamedChildren() {
        return newNodeChildrenContainer()
                .children(CHILD_ARGUMENTS, arguments)
                .build();
    }

    @Override
    public Directive withNewChildren(NodeChildrenContainer newChildren) {
        return transform(builder -> builder
                .arguments(newChildren.getChildren(CHILD_ARGUMENTS))
        );
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Directive that = (Directive) o;

        return NodeUtil.isEqualTo(this.name, that.name);

    }

    @Override
    public Directive deepCopy() {
        return new Directive(name, deepCopy(arguments), getSourceLocation(), getComments(), getIgnoredChars());
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

    public Directive transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private List<Argument> arguments = new ArrayList<>();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;

        private Builder() {
        }

        private Builder(Directive existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.arguments = existing.getArguments();
            this.ignoredChars = existing.getIgnoredChars();
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

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public Directive build() {
            Directive directive = new Directive(name, arguments, sourceLocation, comments, ignoredChars);
            return directive;
        }
    }
}
