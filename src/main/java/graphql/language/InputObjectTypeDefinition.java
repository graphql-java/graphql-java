package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class InputObjectTypeDefinition extends AbstractNode<InputObjectTypeDefinition> implements TypeDefinition<InputObjectTypeDefinition>, DirectivesContainer<InputObjectTypeDefinition> {

    private final String name;
    private final Description description;
    private final List<Directive> directives;
    private final List<InputValueDefinition> inputValueDefinitions;

    @Internal
    protected InputObjectTypeDefinition(String name,
                              List<Directive> directives,
                              List<InputValueDefinition> inputValueDefinitions,
                              Description description,
                              SourceLocation sourceLocation,
                              List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.description = description;
        this.directives = directives;
        this.inputValueDefinitions = inputValueDefinitions;
    }

    @Override
    public List<Directive> getDirectives() {
        return new ArrayList<>(directives);
    }

    public List<InputValueDefinition> getInputValueDefinitions() {
        return new ArrayList<>(inputValueDefinitions);
    }

    @Override
    public String getName() {
        return name;
    }

    public Description getDescription() {
        return description;
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
                deepCopy(inputValueDefinitions),
                description,
                getSourceLocation(),
                getComments());
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


    public static Builder newInputObjectDefinition() {
        return new Builder();
    }

    public InputObjectTypeDefinition transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private Description description;
        private List<Directive> directives = new ArrayList<>();
        private List<InputValueDefinition> inputValueDefinitions = new ArrayList<>();

        private Builder() {
        }

        private Builder(InputObjectTypeDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = existing.getDirectives();
            this.inputValueDefinitions = existing.getInputValueDefinitions();
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

        public Builder inputValueDefinitions(List<InputValueDefinition> inputValueDefinitions) {
            this.inputValueDefinitions = inputValueDefinitions;
            return this;
        }

        public Builder inputValueDefinition(InputValueDefinition inputValueDefinition) {
            this.inputValueDefinitions.add(inputValueDefinition);
            return this;
        }

        public InputObjectTypeDefinition build() {
            InputObjectTypeDefinition inputObjectTypeDefinition = new InputObjectTypeDefinition(name,
                    directives,
                    inputValueDefinitions,
                    description,
                    sourceLocation,
                    comments);
            return inputObjectTypeDefinition;
        }
    }
}
