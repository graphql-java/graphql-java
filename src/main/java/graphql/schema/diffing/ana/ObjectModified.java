package graphql.schema.diffing.ana;

import java.util.ArrayList;
import java.util.List;

public class ObjectModified implements SchemaChange.ObjectChange {
    private final String name;

    private final List<ObjectModifiedDetails> objectModifiedDetails = new ArrayList<>();

    public interface ObjectModifiedDetails {

    }

    public static class AddedInterfaceToObjectDetail implements ObjectModifiedDetails {
        private final String name;

        public AddedInterfaceToObjectDetail(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
    public static class RemovedInterfaceToObjectDetail implements ObjectModifiedDetails {
        private final String name;

        public RemovedInterfaceToObjectDetail(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
    public static class FieldRenamed implements ObjectModifiedDetails {
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
    public static class FieldAdded implements ObjectModifiedDetails {
        private final String name;

        public FieldAdded(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }


    public ObjectModified(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    // this will be mutated

    public List<ObjectModifiedDetails> getObjectModifiedDetails() {
        return objectModifiedDetails;
    }
}
