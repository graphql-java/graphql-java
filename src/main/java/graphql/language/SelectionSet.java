package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class SelectionSet extends AbstractNode {

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
    public String toString() {
        return "SelectionSet{" +
                "selections=" + selections +
                '}';
    }
}
