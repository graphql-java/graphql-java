package graphql.schema.diffing.ana;

import java.util.ArrayList;
import java.util.List;

public class ObjectChanged implements SchemaChange {
    private final String name;

    private final List<ObjectChangeDetail> objectChangeDetails = new ArrayList<>();

    interface ObjectChangeDetail {

    }

    public static class AddedInterfaceObjectChangeDetail implements ObjectChangeDetail {
        private final String name;

        public AddedInterfaceObjectChangeDetail(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }


    public ObjectChanged(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    // this will be mutated
    public List<ObjectChangeDetail> getObjectChangeDetails() {
        return objectChangeDetails;
    }
}
