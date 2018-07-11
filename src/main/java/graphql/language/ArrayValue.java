package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class ArrayValue extends AbstractNode<ArrayValue> implements Value<ArrayValue> {

    private final List<Value> values = new ArrayList<>();

    private ArrayValue(List<Value> values, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.values.addAll(values);
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param values
     */
    public ArrayValue(List<Value> values) {
        super(null, new ArrayList<>());
        this.values.addAll(values);
    }

    public List<Value> getValues() {
        return new ArrayList<>(values);
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<>(values);
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;
    }

    @Override
    public String toString() {
        return "ArrayValue{" +
                "values=" + values +
                '}';
    }

    @Override
    public ArrayValue deepCopy() {
        return new ArrayValue(deepCopy(values), getSourceLocation(), getComments());
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitArrayValue(this, context);
    }

    public static Builder newArrayValue() {
        return new Builder();
    }


    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Value> values = new ArrayList<>();
        private List<Comment> comments = Collections.emptyList();

        private Builder() {
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder values(List<Value> values) {
            this.values = values;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public ArrayValue build() {
            ArrayValue arrayValue = new ArrayValue(values, sourceLocation, comments);
            return arrayValue;
        }
    }
}
