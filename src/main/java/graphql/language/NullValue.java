package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class NullValue extends AbstractNode<NullValue> implements Value<NullValue> {

    public static final NullValue Null = new NullValue(null, Collections.emptyList());

    @Internal
    protected NullValue(SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
    }

    @Override
    public List<Node> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;

    }

    @Override
    public NullValue deepCopy() {
        return this;
    }

    @Override
    public String toString() {
        return "NullValue{" +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitNullValue(this, context);
    }

    public static Builder newNullValue() {
        return new Builder();
    }


    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();

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

        public NullValue build() {
            NullValue nullValue = new NullValue(sourceLocation, comments);
            return nullValue;
        }
    }
}
