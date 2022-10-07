package graphql.schema.diffing.ana;

import java.util.ArrayList;
import java.util.List;

public class ObjectChanged implements SchemaChange {
    private final String name;

    private final List<ObjectChangeDetail> objectChangeDetails = new ArrayList<>();

    interface ObjectChangeDetail {

    }

    public static class AddedInterfaceToObjectDetail implements ObjectChangeDetail {
        private final String name;

        public AddedInterfaceToObjectDetail(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
    public static class RemovedInterfaceToObjectDetail implements ObjectChangeDetail {
        private final String name;

        public RemovedInterfaceToObjectDetail(String name) {
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
