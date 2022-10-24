package graphql.schema.diffing.ana;

import graphql.ExperimentalApi;

import java.util.Map;

@ExperimentalApi
public class EditOperationAnalysisResult {
    private final Map<String, SchemaDifference.ObjectDifference> objectChanges;
    private final Map<String, SchemaDifference.InterfaceDifference> interfaceChanges;
    private final Map<String, SchemaDifference.UnionDifference> unionChanges;
    private final Map<String, SchemaDifference.EnumDifference> enumChanges;
    private final Map<String, SchemaDifference.InputObjectDifference> inputObjectChanges;
    private final Map<String, SchemaDifference.ScalarDifference> scalarChanges;

    public EditOperationAnalysisResult(Map<String, SchemaDifference.ObjectDifference> objectChanges,
                                       Map<String, SchemaDifference.InterfaceDifference> interfaceChanges,
                                       Map<String, SchemaDifference.UnionDifference> unionChanges,
                                       Map<String, SchemaDifference.EnumDifference> enumChanges,
                                       Map<String, SchemaDifference.InputObjectDifference> inputObjectChanges,
                                       Map<String, SchemaDifference.ScalarDifference> scalarChanges) {
        this.objectChanges = objectChanges;
        this.interfaceChanges = interfaceChanges;
        this.unionChanges = unionChanges;
        this.enumChanges = enumChanges;
        this.inputObjectChanges = inputObjectChanges;
        this.scalarChanges = scalarChanges;
    }

    public Map<String, SchemaDifference.ObjectDifference> getObjectChanges() {
        return objectChanges;
    }

    public Map<String, SchemaDifference.InterfaceDifference> getInterfaceChanges() {
        return interfaceChanges;
    }

    public Map<String, SchemaDifference.UnionDifference> getUnionChanges() {
        return unionChanges;
    }

    public Map<String, SchemaDifference.EnumDifference> getEnumChanges() {
        return enumChanges;
    }

    public Map<String, SchemaDifference.InputObjectDifference> getInputObjectChanges() {
        return inputObjectChanges;
    }

    public Map<String, SchemaDifference.ScalarDifference> getScalarChanges() {
        return scalarChanges;
    }
}
