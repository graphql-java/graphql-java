package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;

public class SelectionSet extends AbstractNode<SelectionSet> {

    private final List<Selection> selections = new ArrayList<>();

    public List<Selection> getSelections() {
        return selections;
    }

    public SelectionSet() {
    }

    public SelectionSet(List<Selection> selections) {
        this.selections.addAll(selections);
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(selections);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SelectionSet that = (SelectionSet) o;

        return true;

    }

    @Override
    public SelectionSet deepCopy() {
        return new SelectionSet(deepCopy(selections));
    }

    @Override
    public String toString() {
        return "SelectionSet{" +
                "selections=" + selections +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitSelectionSet(this, context);
    }
}
