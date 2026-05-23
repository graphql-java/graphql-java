package graphql.language;


import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.emptyMap;
import static graphql.language.NodeChildrenContainer.newNodeChildrenContainer;
import static graphql.language.NodeUtil.nodeByName;

@PublicApi
@NullMarked
public class Directive extends AbstractNode<Directive> implements NamedNode<Directive> {
    private final String name;
    private final ImmutableList<Argument> arguments;

    public static final String CHILD_ARGUMENTS = "arguments";

    @Internal
    protected Directive(String name, List<Argument> arguments, @Nullable SourceLocation sourceLocation, List<Comment> comments, IgnoredChars ignoredChars, Map<String, String> additionalData) {
        super(sourceLocation, comments, ignoredChars, additionalData);
        this.name = name;
        this.arguments = ImmutableList.copyOf(arguments);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name      of the directive
     * @param arguments of the directive
     */
    public Directive(String name, List<Argument> arguments) {
        this(name, arguments, null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }


    /**
     * alternative to using a Builder for convenience
     *
     * @param name of the directive
     */
    public Directive(String name) {
        this(name, emptyList(), null, emptyList(), IgnoredChars.EMPTY, emptyMap());
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public Map<String, Argument> getArgumentsByName() {
        // the spec says that args MUST be unique within context
        return nodeByName(arguments);
    }

    public @Nullable Argument getArgument(String argumentName) {
        return NodeUtil.findNodeByName(arguments, argumentName);
    }

    @Override
    public String getName() {
        return name;
    }


    @Override
    public List<Node> getChildren() {
        return ImmutableList.copyOf(arguments);
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
    public boolean isEqualTo(@Nullable Node o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Directive that = (Directive) o;

        return Objects.equals(this.name, that.name);

    }

    @Override
    public Directive deepCopy() {
        return new Directive(name, assertNotNull(deepCopy(arguments), "arguments cannot be null"), getSourceLocation(), getComments(), getIgnoredChars(), getAdditionalData());
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

    @NullUnmarked
    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private String name;
        private ImmutableList<Argument> arguments = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(Directive existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.arguments = ImmutableList.copyOf(existing.getArguments());
            this.ignoredChars = existing.getIgnoredChars();
            this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = ImmutableList.copyOf(comments);
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder arguments(List<Argument> arguments) {
            this.arguments = ImmutableList.copyOf(arguments);
            return this;
        }

        public Builder argument(Argument argument) {
            this.arguments = ImmutableKit.addToList(arguments,argument);
            return this;
        }

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public Builder additionalData(Map<String, String> additionalData) {
            this.additionalData = assertNotNull(additionalData);
            return this;
        }

        public Builder additionalData(String key, String value) {
            this.additionalData.put(key, value);
            return this;
        }


        public Directive build() {
            return new Directive(name, arguments, sourceLocation, comments, ignoredChars, additionalData);
        }
    }
}
