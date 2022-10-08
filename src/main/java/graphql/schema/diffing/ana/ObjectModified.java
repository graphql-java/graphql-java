package graphql.schema.diffing.ana;

import java.util.ArrayList;
import java.util.List;

public class ObjectModified implements SchemaChange.ObjectChange {
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
    public static class FieldRenamed implements ObjectChangeDetail {
        private final String oldName;
        private final String newName;

        public FieldRenamed(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        public String getNewName() {
            return newName;
        }

        public String getOldName() {
            return oldName;
        }
    }


    public ObjectModified(String name) {
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
