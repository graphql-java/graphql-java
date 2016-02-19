package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>FragmentDefinition class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class FragmentDefinition extends AbstractNode implements Definition {

    private String name;
    private TypeName typeCondition;
    private List<Directive> directives = new ArrayList<>();
    private SelectionSet selectionSet;

    /**
     * <p>Constructor for FragmentDefinition.</p>
     */
    public FragmentDefinition() {

    }

    /**
     * <p>Constructor for FragmentDefinition.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param typeCondition a {@link graphql.language.TypeName} object.
     */
    public FragmentDefinition(String name, TypeName typeCondition) {
        this.name = name;
        this.typeCondition = typeCondition;
    }

    /**
     * <p>Constructor for FragmentDefinition.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param typeCondition a {@link graphql.language.TypeName} object.
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public FragmentDefinition(String name, TypeName typeCondition, SelectionSet selectionSet) {
        this.name = name;
        this.typeCondition = typeCondition;
        this.selectionSet = selectionSet;
    }


    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getName() {
        return name;
    }

    /**
     * <p>Setter for the field <code>name</code>.</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public void setName(String name) {
        this.name = name;
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
        List<Node> result = new ArrayList<>();
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

        FragmentDefinition that = (FragmentDefinition) o;

        return !(name != null ? !name.equals(that.name) : that.name != null);

    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "FragmentDefinition{" +
                "name='" + name + '\'' +
                ", typeCondition='" + typeCondition + '\'' +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }
}
