package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class SelectionSet implements  Node{

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SelectionSet that = (SelectionSet) o;

        return !(selections != null ? !selections.equals(that.selections) : that.selections != null);

    }

    @Override
    public int hashCode() {
        return selections != null ? selections.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "SelectionSet{" +
                "selections=" + selections +
                '}';
    }
}
