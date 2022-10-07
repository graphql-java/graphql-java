package graphql.schema.diffing.ana;

import java.util.ArrayList;
import java.util.List;

public class InterfaceChanged implements SchemaChange {
    private final String name;

    private final List<InterfaceChangeDetail> interfaceChangeDetails = new ArrayList<>();

    interface InterfaceChangeDetail {

    }

    public static class AddedInterfaceToInterfaceDetail implements InterfaceChangeDetail {
        private final String name;

        public AddedInterfaceToInterfaceDetail(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
    public static class RemovedInterfaceFromInterfaceDetail implements InterfaceChangeDetail {
        private final String name;

        public RemovedInterfaceFromInterfaceDetail(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }


    public InterfaceChanged(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    // this will be mutated
    public List<InterfaceChangeDetail> getInterfaceChangeDetails() {
        return interfaceChangeDetails;
    }
}
