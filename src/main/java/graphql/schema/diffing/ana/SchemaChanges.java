package graphql.schema.diffing.ana;

public class SchemaChanges {

    /**
     * Type means Object, Interface, Union, InputObject, Scalar, Enum
     */
    public static class ObjectAdded implements SchemaChange.ObjectChange {
        private String name;

        public ObjectAdded(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class InterfaceAdded implements SchemaChange.InterfaceChange {
        private String name;

        public InterfaceAdded(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class ScalarAdded implements SchemaChange.ScalarChange {
        private String name;

        public ScalarAdded(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class UnionAdded implements SchemaChange.UnionChange {
        private String name;

        public UnionAdded(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class InputObjectAdded implements SchemaChange.InputObjectChange {
        private String name;

        public InputObjectAdded(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class EnumAdded implements SchemaChange.EnumChange {
        private String name;

        public EnumAdded(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class FieldAdded implements SchemaChange {

        private final String name;
        private final String fieldsContainer;

        public FieldAdded(String name, String fieldsContainer) {
            this.name = name;
            this.fieldsContainer = fieldsContainer;
        }

        public String getName() {
            return name;
        }

        public String getFieldsContainer() {
            return fieldsContainer;
        }
    }

    public static class FieldModified implements SchemaChange {

        private final String name;
        private final String fieldsContainer;

        public FieldModified(String name, String fieldsContainer) {
            this.name = name;
            this.fieldsContainer = fieldsContainer;
        }

        public String getName() {
            return name;
        }


        public String getFieldsContainer() {
            return fieldsContainer;
        }

    }


    public static class ObjectRemoved implements SchemaChange.ObjectChange {
        private String name;

        public ObjectRemoved(String name) {
            this.name = name;
        }

    }

    public static class InterfaceRemoved implements SchemaChange.InterfaceChange {
        private String name;

        public InterfaceRemoved(String name) {
            this.name = name;
        }

    }

    public static class UnionRemoved implements SchemaChange.UnionChange {
        private String name;

        public UnionRemoved(String name) {
            this.name = name;
        }

    }

    public static class ScalarRemoved implements SchemaChange.ScalarChange {
        private String name;

        public ScalarRemoved(String name) {
            this.name = name;
        }

    }

    public static class InputObjectRemoved implements SchemaChange.InputObjectChange {
        private String name;

        public InputObjectRemoved(String name) {
            this.name = name;
        }

    }
    public static class EnumRemoved implements SchemaChange.EnumChange {
        private String name;

        public EnumRemoved(String name) {
            this.name = name;
        }

    }


}
