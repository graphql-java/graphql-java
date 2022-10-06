package graphql.schema.diffing.ana;

public class SchemaChanges {

    public interface SchemaChange {

    }

    /**
     * Type means Object, Interface, Union, InputObject, Scalar, Enum
     */

    public static class ObjectAdded implements SchemaChange {
        private String name;

        public ObjectAdded(String name) {
            this.name = name;
        }
    }

    public static class FieldAdded implements SchemaChange {

        private String name;
        private String fieldsContainer;

        public FieldAdded(String name, String fieldsContainer) {
            this.name = name;
            this.fieldsContainer = fieldsContainer;
        }
    }

    public static class FieldChanged implements SchemaChange {

        private final String name;
        private final String fieldsContainer;

        public FieldChanged(String name, String fieldsContainer) {
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

    public static class ObjectChanged implements SchemaChange {

    }

    public static class ObjectRemoved implements SchemaChange {
        private String name;

        public ObjectRemoved(String name) {
            this.name = name;
        }


    }




    public static class FieldRemoved {

    }

    public static class InputFieldChanged {

    }

    public static class InputFieldAdded {

    }

    public static class InputFieldRemoved {

    }

    public static class DirectiveChanged {

    }

    public static class DirectiveAdded {

    }

    public static class DirectiveRemoved {

    }

}
