package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class TypeName extends AbstractNode<TypeName> implements Type<TypeName> {

    private final String name;

    @Internal
    protected TypeName(String name, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name
     */
    public TypeName(String name) {
        super(null, new ArrayList<>());
        this.name = name;
    }


    public String getName() {
        return name;
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeName that = (TypeName) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public TypeName deepCopy() {
        return new TypeName(name, getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "TypeName{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitTypeName(this, context);
    }

    public static Builder newTypeName() {
        return new Builder();
    }

    public static Builder newTypeName(String name) {
        return new Builder().name(name);
    }

    public TypeName transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }


    public static final class Builder implements NodeBuilder {
        private String name;
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();

        private Builder() {
        }

        private Builder(TypeName existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
        }


        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public TypeName build() {
            TypeName typeName = new TypeName(name, sourceLocation, comments);
            return typeName;
        }
    }
}
