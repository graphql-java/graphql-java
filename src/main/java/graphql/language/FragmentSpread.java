package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>FragmentSpread class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class FragmentSpread extends AbstractNode implements Selection {

    private String name;
    private List<Directive> directives = new ArrayList<Directive>();

    /**
     * <p>Constructor for FragmentSpread.</p>
     */
    public FragmentSpread() {
    }

    /**
     * <p>Constructor for FragmentSpread.</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public FragmentSpread(String name) {
        this.name = name;
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

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FragmentSpread that = (FragmentSpread) o;

        return !(name != null ? !name.equals(that.name) : that.name != null);

    }


    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<Node>();
        result.addAll(directives);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "FragmentSpread{" +
                "name='" + name + '\'' +
                ", directives=" + directives +
                '}';
    }
}
