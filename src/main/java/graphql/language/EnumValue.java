package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;

public class EnumValue extends AbstractNode<EnumValue> implements Value<EnumValue> {

    private String name;

    public EnumValue(String name) {
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

        EnumValue that = (EnumValue) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public EnumValue deepCopy() {
        return new EnumValue(name);
    }

    @Override
    public String toString() {
        return "EnumValue{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitEnumValue(this, context);
    }
}
