package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>EnumValue class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class EnumValue extends AbstractNode implements  Value {

    private String name;

    /**
     * <p>Constructor for EnumValue.</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public EnumValue(String name) {
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


    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EnumValue enumValue = (EnumValue) o;

        return !(name != null ? !name.equals(enumValue.name) : enumValue.name != null);

    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "EnumValue{" +
                "name='" + name + '\'' +
                '}';
    }
}
