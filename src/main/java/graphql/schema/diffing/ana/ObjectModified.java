package graphql.schema.diffing.ana;

import graphql.ExperimentalApi;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.List;

@ExperimentalApi
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

    public static class FieldTypeModified implements ObjectModifiedDetails {
        private final String name;
        private final String oldType;
        private final String newType;

        public FieldTypeModified(String name, String oldType, String newType) {
            this.name = name;
            this.oldType = oldType;
            this.newType = newType;
        }

        public String getName() {
            return name;
        }

        public String getOldType() {
            return oldType;
        }

        public String getNewType() {
            return newType;
        }
    }

    public static class ArgumentRemoved implements ObjectModifiedDetails {

        private final String fieldName;
        private final String argumentName;

        public ArgumentRemoved(String fieldName, String argumentName) {
            this.fieldName = fieldName;
            this.argumentName = argumentName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getArgumentName() {
            return argumentName;
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

    public <T extends ObjectModifiedDetails> List<T> getObjectModifiedDetails(Class<? extends T> clazz) {
        return (List) FpKit.filterList(objectModifiedDetails, clazz::isInstance);
    }
}
