package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>TypeName class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class TypeName extends AbstractNode implements Type {

    private String name;

    /**
     * <p>Constructor for TypeName.</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public TypeName(String name) {
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

        TypeName namedType = (TypeName) o;

        if (name != null ? !name.equals(namedType.name) : namedType.name != null) return false;

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "TypeName{" +
                "name='" + name + '\'' +
                '}';
    }
}
