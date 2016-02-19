package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>ObjectField class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ObjectField extends AbstractNode {

    private String name;
    private Value value;

    /**
     * <p>Constructor for ObjectField.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param value a {@link graphql.language.Value} object.
     */
    public ObjectField(String name, Value value) {
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
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a {@link graphql.language.Value} object.
     */
    public Value getValue() {
        return value;
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

        ObjectField that = (ObjectField) o;

        return !(name != null ? !name.equals(that.name) : that.name != null);

    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ObjectField{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}
