package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>ListType class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ListType extends AbstractNode implements Type {

    private Type type;

    /**
     * <p>Constructor for ListType.</p>
     */
    public ListType() {
    }

    /**
     * <p>Constructor for ListType.</p>
     *
     * @param type a {@link graphql.language.Type} object.
     */
    public ListType(Type type) {
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
     * @param type a {@link graphql.language.Type} object.
     */
    public void setType(Type type) {
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
        return "ListType{" +
                "type=" + type +
                '}';
    }
}
