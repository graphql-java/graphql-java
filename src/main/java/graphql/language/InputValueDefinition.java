package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class InputValueDefinition extends AbstractNode<InputValueDefinition> implements DirectivesContainer<InputValueDefinition> {
    private final String name;
    private final Type type;
    private final Value defaultValue;
    private final Description description;
    private final List<Directive> directives;


    @Internal
    protected InputValueDefinition(String name,
                                 Type type,
                                 Value defaultValue,
                                 List<Directive> directives,
                                 Description description,
                                 SourceLocation sourceLocation,
                                 List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.directives = directives;
        this.description = description;

    }

    /**
     * alternative to using a Builder for convenience
     */
    public InputValueDefinition(String name,
                                Type type) {
        this(name, type, null, new ArrayList<>(), null, null, new ArrayList<>());

    }

    /**
     * alternative to using a Builder for convenience
     */

    public InputValueDefinition(String name,
                                Type type,
                                Value defaultValue) {
        this(name, type, defaultValue, new ArrayList<>(), null, null, new ArrayList<>());

    }

    public Type getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    public Description getDescription() {
        return description;
    }

    public Value getDefaultValue() {
        return defaultValue;
    }

    public List<Directive> getDirectives() {
        return new ArrayList<>(directives);
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
                deepCopy(directives),
                description,
                getSourceLocation(),
                getComments());
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
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitInputValueDefinition(this, context);
    }

    public static Builder newInputValueDefinition() {
        return new Builder();
    }

    public InputValueDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private Type type;
        private Value defaultValue;
        private Description description;
        private List<Directive> directives = new ArrayList<>();

        private Builder() {
        }

        private Builder(InputValueDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.type = existing.getType();
            this.defaultValue = existing.getDefaultValue();
            this.description = existing.getDescription();
            this.directives = existing.getDirectives();
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

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder defaultValue(Value defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder description(Description description) {
            this.description = description;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder directive(Directive directive) {
            this.directives.add(directive);
            return this;
        }

        public InputValueDefinition build() {
            InputValueDefinition inputValueDefinition = new InputValueDefinition(name,
                    type,
                    defaultValue,
                    directives,
                    description,
                    sourceLocation,
                    comments);
            return inputValueDefinition;
        }
    }
}
