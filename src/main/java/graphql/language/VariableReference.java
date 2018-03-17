package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;

public class VariableReference extends AbstractNode<VariableReference> implements Value<VariableReference> {

    private String name;

    public VariableReference(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VariableReference that = (VariableReference) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public VariableReference deepCopy() {
        return new VariableReference(name);
    }

    @Override
    public String toString() {
        return "VariableReference{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitVariableReference(this, context);
    }
}
