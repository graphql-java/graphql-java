package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class InputObjectTypeDefinition extends AbstractNode<InputObjectTypeDefinition> implements TypeDefinition<InputObjectTypeDefinition>, DirectivesContainer<InputObjectTypeDefinition> {

    private final String name;
    private final Description description;
    private final List<Directive> directives;
    private final List<InputValueDefinition> inputValueDefinitions;

    InputObjectTypeDefinition(String name,
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

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private Description description;
        private List<Directive> directives = new ArrayList<>();
        private List<InputValueDefinition> inputValueDefinitions = new ArrayList<>();

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

        public Builder description(Description description) {
            this.description = description;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder inputValueDefinitions(List<InputValueDefinition> inputValueDefinitions) {
            this.inputValueDefinitions = inputValueDefinitions;
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
