package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class ListType extends AbstractNode<ListType> implements Type<ListType> {

    private final Type type;

    public ListType(Type type, SourceLocation sourceLocation, List<Comment> comments) {
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
    public ListType deepCopy() {
        return new ListType(deepCopy(type), getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "ListType{" +
                "type=" + type +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitListType(this, context);
    }

    public static Builder newListType() {
        return new Builder();
    }

    public static Builder newListType(Type type) {
        return new Builder().type(type
        );
    }

    public static final class Builder implements NodeBuilder {
        private Type type;
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();

        private Builder() {
        }


        public Builder type(Type type) {
            this.type = type;
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

        public ListType build() {
            ListType listType = new ListType(type, sourceLocation, comments);
            return listType;
        }
    }
}
