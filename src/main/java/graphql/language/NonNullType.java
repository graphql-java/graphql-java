package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class NonNullType extends AbstractNode<NonNullType> implements Type<NonNullType> {

    private final Type type;

    private NonNullType(Type type, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);

        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(type);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;

    }

    @Override
    public NonNullType deepCopy() {
        return new NonNullType(deepCopy(type), getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "NonNullType{" +
                "type=" + type +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitNonNullType(this, context);
    }

    public static Builder newNonNullType() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private Type type;
        private List<Comment> comments = Collections.emptyList();

        private Builder() {
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder type(ListType type) {
            this.type = type;
            return this;
        }

        public Builder type(TypeName type) {
            this.type = type;
            return this;
        }

        public Builder type(Type type) {
            if (!(type instanceof ListType) && !(type instanceof TypeName)) {
                throw new IllegalArgumentException("unexpected type");
            }
            this.type = type;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public NonNullType build() {
            NonNullType nonNullType = new NonNullType(type, sourceLocation, comments);
            return nonNullType;
        }
    }
}
