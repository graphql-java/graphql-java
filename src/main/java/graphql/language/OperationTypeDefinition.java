package graphql.language;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PublicApi
public class OperationTypeDefinition extends AbstractNode<OperationTypeDefinition> implements NamedNode<OperationTypeDefinition> {

    private final String name;
    private final Type type;

    public OperationTypeDefinition(String name, Type type, SourceLocation sourceLocation, List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
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

        OperationTypeDefinition that = (OperationTypeDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public OperationTypeDefinition deepCopy() {
        return new OperationTypeDefinition(name, deepCopy(type), getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "OperationTypeDefinition{" +
                "name='" + name + "'" +
                ", type=" + type +
                "}";
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitOperationTypeDefinition(this, context);
    }

    public static Builder newOperationTypeDefinition() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private Type type;

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

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public OperationTypeDefinition build() {
            OperationTypeDefinition operationTypeDefinition = new OperationTypeDefinition(name, type, sourceLocation, comments);
            return operationTypeDefinition;
        }
    }
}
