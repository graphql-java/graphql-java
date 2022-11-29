package graphql.schema.diffing.ana;

import graphql.Internal;

import java.util.Map;

@Internal
public class EditOperationAnalysisResult {
    private final Map<String, SchemaDifference.ObjectDifference> objectDifferences;
    private final Map<String, SchemaDifference.InterfaceDifference> interfaceDifferences;
    private final Map<String, SchemaDifference.UnionDifference> unionDifferences;
    private final Map<String, SchemaDifference.EnumDifference> enumDifferences;
    private final Map<String, SchemaDifference.InputObjectDifference> inputObjectDifferences;
    private final Map<String, SchemaDifference.ScalarDifference> scalarDifferences;

    private final Map<String, SchemaDifference.DirectiveDifference> directiveDifferences;

    public EditOperationAnalysisResult(Map<String, SchemaDifference.ObjectDifference> objectChanges,
                                       Map<String, SchemaDifference.InterfaceDifference> interfaceDifferences,
                                       Map<String, SchemaDifference.UnionDifference> unionDifferences,
                                       Map<String, SchemaDifference.EnumDifference> enumDifferences,
                                       Map<String, SchemaDifference.InputObjectDifference> inputObjectDifferences,
                                       Map<String, SchemaDifference.ScalarDifference> scalarDifferences,
                                       Map<String, SchemaDifference.DirectiveDifference> directiveDifferences) {
        this.objectDifferences = objectChanges;
        this.interfaceDifferences = interfaceDifferences;
        this.unionDifferences = unionDifferences;
        this.enumDifferences = enumDifferences;
        this.inputObjectDifferences = inputObjectDifferences;
        this.scalarDifferences = scalarDifferences;
        this.directiveDifferences = directiveDifferences;
    }

    public Map<String, SchemaDifference.ObjectDifference> getObjectDifferences() {
        return objectDifferences;
    }

    public Map<String, SchemaDifference.InterfaceDifference> getInterfaceDifferences() {
        return interfaceDifferences;
    }

    public Map<String, SchemaDifference.UnionDifference> getUnionDifferences() {
        return unionDifferences;
    }

    public Map<String, SchemaDifference.EnumDifference> getEnumDifferences() {
        return enumDifferences;
    }

    public Map<String, SchemaDifference.InputObjectDifference> getInputObjectDifferences() {
        return inputObjectDifferences;
    }

    public Map<String, SchemaDifference.ScalarDifference> getScalarDifferences() {
        return scalarDifferences;
    }

    public Map<String, SchemaDifference.DirectiveDifference> getDirectiveDifferences() {
        return directiveDifferences;
    }
}
