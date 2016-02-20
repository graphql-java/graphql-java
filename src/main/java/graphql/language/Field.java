package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>Field class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class Field extends AbstractNode implements Selection {

    private String name;
    private String alias;

    private List<Argument> arguments = new ArrayList<Argument>();
    private List<Directive> directives = new ArrayList<Directive>();
    private SelectionSet selectionSet;

    /**
     * <p>Constructor for Field.</p>
     */
    public Field() {

    }

    /**
     * <p>Constructor for Field.</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public Field(String name) {
        this.name = name;
    }

    /**
     * <p>Constructor for Field.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public Field(String name, SelectionSet selectionSet) {
        this.name = name;
        this.selectionSet = selectionSet;
    }


    /**
     * <p>Constructor for Field.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param arguments a {@link java.util.List} object.
     */
    public Field(String name, List<Argument> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    /**
     * <p>Constructor for Field.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param arguments a {@link java.util.List} object.
     * @param directives a {@link java.util.List} object.
     */
    public Field(String name, List<Argument> arguments, List<Directive> directives) {
        this.name = name;
        this.arguments = arguments;
        this.directives = directives;
    }

    /**
     * <p>Constructor for Field.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param arguments a {@link java.util.List} object.
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public Field(String name, List<Argument> arguments, SelectionSet selectionSet) {
        this.name = name;
        this.arguments = arguments;
        this.selectionSet = selectionSet;
    }


    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<Node>();
        result.addAll(arguments);
        result.addAll(directives);
        if (selectionSet != null) result.add(selectionSet);
        return result;
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
     * <p>Getter for the field <code>alias</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getAlias() {
        return alias;
    }

    /**
     * <p>Setter for the field <code>alias</code>.</p>
     *
     * @param alias a {@link java.lang.String} object.
     */
    public void setAlias(String alias) {
        this.alias = alias;
    }

    /**
     * <p>Getter for the field <code>arguments</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<Argument> getArguments() {
        return arguments;
    }

    /**
     * <p>Setter for the field <code>arguments</code>.</p>
     *
     * @param arguments a {@link java.util.List} object.
     */
    public void setArguments(List<Argument> arguments) {
        this.arguments = arguments;
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
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Field field = (Field) o;

        if (name != null ? !name.equals(field.name) : field.name != null) return false;
        return !(alias != null ? !alias.equals(field.alias) : field.alias != null);

    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Field{" +
                "name='" + name + '\'' +
                ", alias='" + alias + '\'' +
                ", arguments=" + arguments +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }
}
