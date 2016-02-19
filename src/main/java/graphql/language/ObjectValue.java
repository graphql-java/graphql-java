package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>ObjectValue class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ObjectValue extends AbstractNode implements Value {

    private List<ObjectField> objectFields = new ArrayList<>();

    /**
     * <p>Constructor for ObjectValue.</p>
     */
    public ObjectValue() {
    }

    /**
     * <p>Constructor for ObjectValue.</p>
     *
     * @param objectFields a {@link java.util.List} object.
     */
    public ObjectValue(List<ObjectField> objectFields) {
        this.objectFields.addAll(objectFields);
    }

    /**
     * <p>Getter for the field <code>objectFields</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<ObjectField> getObjectFields() {
        return objectFields;
    }

    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(objectFields);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectValue that = (ObjectValue) o;

        return true;

    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ObjectValue{" +
                "objectFields=" + objectFields +
                '}';
    }
}
