package graphql.schema.diffing.ana;

import java.util.Map;

public class EditOperationAnalysisResult {
    private final Map<String, SchemaChange.ObjectChange> objectChanges;
    private final Map<String, SchemaChange.InterfaceChange> interfaceChanges;
    private final Map<String, SchemaChange.UnionChange> unionChanges;
    private final Map<String, SchemaChange.EnumChange> enumChanges;
    private final Map<String, SchemaChange.InputObjectChange> inputObjectChanges;
    private final Map<String, SchemaChange.ScalarChange> scalarChanges;

    public EditOperationAnalysisResult(Map<String, SchemaChange.ObjectChange> objectChanges,
                                       Map<String, SchemaChange.InterfaceChange> interfaceChanges,
                                       Map<String, SchemaChange.UnionChange> unionChanges,
                                       Map<String, SchemaChange.EnumChange> enumChanges,
                                       Map<String, SchemaChange.InputObjectChange> inputObjectChanges,
                                       Map<String, SchemaChange.ScalarChange> scalarChanges) {
        this.objectChanges = objectChanges;
        this.interfaceChanges = interfaceChanges;
        this.unionChanges = unionChanges;
        this.enumChanges = enumChanges;
        this.inputObjectChanges = inputObjectChanges;
        this.scalarChanges = scalarChanges;
    }

    public Map<String, SchemaChange.ObjectChange> getObjectChanges() {
        return objectChanges;
    }

    public Map<String, SchemaChange.InterfaceChange> getInterfaceChanges() {
        return interfaceChanges;
    }

    public Map<String, SchemaChange.UnionChange> getUnionChanges() {
        return unionChanges;
    }

    public Map<String, SchemaChange.EnumChange> getEnumChanges() {
        return enumChanges;
    }

    public Map<String, SchemaChange.InputObjectChange> getInputObjectChanges() {
        return inputObjectChanges;
    }

    public Map<String, SchemaChange.ScalarChange> getScalarChanges() {
        return scalarChanges;
    }
}
