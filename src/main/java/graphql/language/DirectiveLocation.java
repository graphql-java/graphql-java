package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;

// This should probably be an enum... but the grammar
// doesn't enforce the names. These are the current names:
//    QUERY
//    MUTATION
//    FIELD
//    FRAGMENT_DEFINITION
//    FRAGMENT_SPREAD
//    INLINE_FRAGMENT
public class DirectiveLocation extends AbstractNode<DirectiveLocation> {
    private final String name;

    public DirectiveLocation(String name) {
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

        DirectiveLocation that = (DirectiveLocation) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public DirectiveLocation deepCopy() {
        return new DirectiveLocation(name);
    }

    @Override
    public String toString() {
        return "DirectiveLocation{" +
                "name='" + name + "'" +
                "}";
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitDirectiveLocation(this, context);
    }
}
