package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class ObjectValue implements Value {

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
        return new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectValue that = (ObjectValue) o;

        return !(objectFields != null ? !objectFields.equals(that.objectFields) : that.objectFields != null);

    }

    @Override
    public int hashCode() {
        return objectFields != null ? objectFields.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ObjectValue{" +
                "objectFields=" + objectFields +
                '}';
    }
}
