package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>NonNullType class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class NonNullType extends AbstractNode implements Type {

    private Type type;

    /**
     * <p>Constructor for NonNullType.</p>
     */
    public NonNullType() {
    }

    /**
     * <p>Constructor for NonNullType.</p>
     *
     * @param type a {@link graphql.language.Type} object.
     */
    public NonNullType(Type type) {
        this.type = type;
    }

    /**
     * <p>Getter for the field <code>type</code>.</p>
     *
     * @return a {@link graphql.language.Type} object.
     */
    public Type getType() {
        return type;
    }

    /**
     * <p>Setter for the field <code>type</code>.</p>
     *
     * @param type a {@link graphql.language.ListType} object.
     */
    public void setType(ListType type) {
        this.type = type;
    }

    /**
     * <p>Setter for the field <code>type</code>.</p>
     *
     * @param type a {@link graphql.language.TypeName} object.
     */
    public void setType(TypeName type) {
        this.type = type;
    }

    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(type);
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
        return "NonNullType{" +
                "type=" + type +
                '}';
    }
}
