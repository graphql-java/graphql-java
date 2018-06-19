package graphql.language;


import graphql.Internal;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;

@Internal
public class OperationTypeDefinition extends AbstractNode<OperationTypeDefinition> implements NamedNode<OperationTypeDefinition> {
    private final String name;
    private Type type;

    public OperationTypeDefinition() {
        this(null);
    }

    public OperationTypeDefinition(String name) {
        this(name, null);
    }

    public OperationTypeDefinition(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

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
        return new OperationTypeDefinition(name, deepCopy(type));
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
}
