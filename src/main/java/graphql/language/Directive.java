package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>Directive class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class Directive extends AbstractNode {
    private String name;
    private final List<Argument> arguments = new ArrayList<Argument>();

    /**
     * <p>Constructor for Directive.</p>
     */
    public Directive() {

    }

    /**
     * <p>Constructor for Directive.</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public Directive(String name) {
        this.name = name;
    }

    /**
     * <p>Constructor for Directive.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param arguments a {@link java.util.List} object.
     */
    public Directive(String name, List<Argument> arguments) {
        this.name = name;
        this.arguments.addAll(arguments);
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


    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        return new ArrayList<Node>(arguments);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Directive directive = (Directive) o;

        return !(name != null ? !name.equals(directive.name) : directive.name != null);

    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Directive{" +
                "name='" + name + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}
