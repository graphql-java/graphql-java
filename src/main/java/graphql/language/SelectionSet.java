package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>SelectionSet class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class SelectionSet extends AbstractNode{

    private final List<Selection> selections = new ArrayList<Selection>();

    /**
     * <p>Getter for the field <code>selections</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<Selection> getSelections() {
        return selections;
    }

    /**
     * <p>Constructor for SelectionSet.</p>
     */
    public SelectionSet() {
    }

    /**
     * <p>Constructor for SelectionSet.</p>
     *
     * @param selections a {@link java.util.List} object.
     */
    public SelectionSet(List<Selection> selections) {
        this.selections.addAll(selections);
    }

    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<Node>();
        result.addAll(selections);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SelectionSet that = (SelectionSet) o;

        return true;

    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "SelectionSet{" +
                "selections=" + selections +
                '}';
    }
}
