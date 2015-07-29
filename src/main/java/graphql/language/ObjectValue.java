package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class ObjectValue extends AbstractNode implements Value {

    private List<ObjectField> objectFields = new ArrayList<>();

    public ObjectValue() {
    }

    public ObjectValue(List<ObjectField> objectFields) {
        this.objectFields.addAll(objectFields);
    }

    public List<ObjectField> getObjectFields() {
        return objectFields;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(objectFields);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectValue that = (ObjectValue) o;

        return true;

    }


    @Override
    public String toString() {
        return "ObjectValue{" +
                "objectFields=" + objectFields +
                '}';
    }
}
