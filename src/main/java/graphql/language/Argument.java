package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class Argument extends AbstractNode<Argument> implements NamedNode<Argument> {

    private final String name;
    private final Value value;

    private Argument(String name, Value value, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.value = value;
    }

    /**
     * alternative to using a Builder for convenience
     *
     * @param name
     * @param value
     */
    public Argument(String name, Value value) {
        super(null, new ArrayList<>());
        this.name = name;
        this.value = value;
    }

    @Override
    public String getName() {
        return name;
    }

    public Value getValue() {
        return value;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(value);
        return result;
    }


    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Argument that = (Argument) o;

        return NodeUtil.isEqualTo(this.name, that.name);

    }

    @Override
    public Argument deepCopy() {
        return new Argument(name, deepCopy(value), getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "Argument{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitArgument(this, context);
    }

    public static Builder newArgument() {
        return new Builder();
    }

    public static Builder newArgument(String name, Value value) {
        return new Builder().name(name).value(value);
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private String name;
        private Value value;
        private List<Comment> comments = Collections.emptyList();

        private Builder() {
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder value(Value value) {
            this.value = value;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Argument build() {
            Argument argument = new Argument(name, value, sourceLocation, comments);
            return argument;
        }
    }
}
