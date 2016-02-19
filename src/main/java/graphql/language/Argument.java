package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>Argument class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class Argument extends AbstractNode {

    private String name;
    private Value value;

    /**
     * <p>Constructor for Argument.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param value a {@link graphql.language.Value} object.
     */
    public Argument(String name, Value value) {
        this.name = name;
        this.value = value;
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
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a {@link graphql.language.Value} object.
     */
    public Value getValue() {
        return value;
    }

    /**
     * <p>Setter for the field <code>value</code>.</p>
     *
     * @param value a {@link graphql.language.Value} object.
     */
    public void setValue(Value value) {
        this.value = value;
    }


    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(value);
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Argument argument = (Argument) o;

        return !(name != null ? !name.equals(argument.name) : argument.name != null);

    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Argument{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }

}
