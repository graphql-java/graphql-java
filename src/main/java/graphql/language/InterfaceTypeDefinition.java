package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class InterfaceTypeDefinition extends AbstractNode<InterfaceTypeDefinition> implements TypeDefinition<InterfaceTypeDefinition>, DirectivesContainer<InterfaceTypeDefinition> {

    private final String name;
    private final Description description;
    private final List<FieldDefinition> definitions;
    private final List<Directive> directives;

    InterfaceTypeDefinition(String name,
                            List<FieldDefinition> definitions,
                            List<Directive> directives,
                            Description description,
                            SourceLocation sourceLocation,
                            List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.definitions = definitions;
        this.directives = directives;
        this.description = description;
    }

    public List<FieldDefinition> getFieldDefinitions() {
        return definitions;
    }

    @Override
    public List<Directive> getDirectives() {
        return directives;
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
        result.addAll(definitions);
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InterfaceTypeDefinition that = (InterfaceTypeDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public InterfaceTypeDefinition deepCopy() {
        return new InterfaceTypeDefinition(name,
                deepCopy(definitions),
                deepCopy(directives),
                description,
                getSourceLocation(),
                getComments()
        );
    }

    @Override
    public String toString() {
        return "InterfaceTypeDefinition{" +
                "name='" + name + '\'' +
                ", fieldDefinitions=" + definitions +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitInterfaceTypeDefinition(this, context);
    }


    public static Builder newInterfaceTypeDefinition() {
        return new Builder();
    }


    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private Description description;
        private List<FieldDefinition> definitions = new ArrayList<>();
        private List<Directive> directives = new ArrayList<>();

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

        public Builder definitions(List<FieldDefinition> definitions) {
            this.definitions = definitions;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public InterfaceTypeDefinition build() {
            InterfaceTypeDefinition interfaceTypeDefinition = new InterfaceTypeDefinition(name,
                    definitions,
                    directives,
                    description,
                    sourceLocation,
                    comments);
            return interfaceTypeDefinition;
        }
    }
}
