package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>InlineFragment class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class InlineFragment extends AbstractNode implements Selection {
    private TypeName typeCondition;
    private List<Directive> directives = new ArrayList<Directive>();
    private SelectionSet selectionSet;

    /**
     * <p>Constructor for InlineFragment.</p>
     */
    public InlineFragment() {

    }

    /**
     * <p>Constructor for InlineFragment.</p>
     *
     * @param typeCondition a {@link graphql.language.TypeName} object.
     */
    public InlineFragment(TypeName typeCondition) {
        this.typeCondition = typeCondition;
    }

    /**
     * <p>Constructor for InlineFragment.</p>
     *
     * @param typeCondition a {@link graphql.language.TypeName} object.
     * @param directives a {@link java.util.List} object.
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public InlineFragment(TypeName typeCondition, List<Directive> directives, SelectionSet selectionSet) {
        this.typeCondition = typeCondition;
        this.directives = directives;
        this.selectionSet = selectionSet;
    }

    /**
     * <p>Constructor for InlineFragment.</p>
     *
     * @param typeCondition a {@link graphql.language.TypeName} object.
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public InlineFragment(TypeName typeCondition, SelectionSet selectionSet) {
        this.typeCondition = typeCondition;
        this.selectionSet = selectionSet;
    }


    /**
     * <p>Getter for the field <code>typeCondition</code>.</p>
     *
     * @return a {@link graphql.language.TypeName} object.
     */
    public TypeName getTypeCondition() {
        return typeCondition;
    }

    /**
     * <p>Setter for the field <code>typeCondition</code>.</p>
     *
     * @param typeCondition a {@link graphql.language.TypeName} object.
     */
    public void setTypeCondition(TypeName typeCondition) {
        this.typeCondition = typeCondition;
    }

    /**
     * <p>Getter for the field <code>directives</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<Directive> getDirectives() {
        return directives;
    }

    /**
     * <p>Setter for the field <code>directives</code>.</p>
     *
     * @param directives a {@link java.util.List} object.
     */
    public void setDirectives(List<Directive> directives) {
        this.directives = directives;
    }

    /**
     * <p>Getter for the field <code>selectionSet</code>.</p>
     *
     * @return a {@link graphql.language.SelectionSet} object.
     */
    public SelectionSet getSelectionSet() {
        return selectionSet;
    }

    /**
     * <p>Setter for the field <code>selectionSet</code>.</p>
     *
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public void setSelectionSet(SelectionSet selectionSet) {
        this.selectionSet = selectionSet;
    }

    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<Node>();
        result.add(typeCondition);
        result.addAll(directives);
        result.add(selectionSet);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;

    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "InlineFragment{" +
                "typeCondition='" + typeCondition + '\'' +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }
}
