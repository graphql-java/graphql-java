package graphql.normalized;

import com.google.common.collect.ImmutableListMultimap;
import graphql.Assert;
import graphql.Internal;
import graphql.execution.MergedField;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldsContainer;

import java.util.List;
import java.util.Map;

@Internal
public class ExecutableNormalizedOperation {
    private final OperationDefinition.Operation operation;
    private final String operationName;
    private final List<ExecutableNormalizedField> topLevelFields;
    private final ImmutableListMultimap<Field, ExecutableNormalizedField> fieldToNormalizedField;
    private final Map<ExecutableNormalizedField, MergedField> normalizedFieldToMergedField;
    private final ImmutableListMultimap<FieldCoordinates, ExecutableNormalizedField> coordinatesToNormalizedFields;

    public ExecutableNormalizedOperation(
            OperationDefinition.Operation operation,
            String operationName,
            List<ExecutableNormalizedField> topLevelFields,
            ImmutableListMultimap<Field, ExecutableNormalizedField> fieldToNormalizedField,
            Map<ExecutableNormalizedField, MergedField> normalizedFieldToMergedField,
            ImmutableListMultimap<FieldCoordinates, ExecutableNormalizedField> coordinatesToNormalizedFields
    ) {
        this.operation = operation;
        this.operationName = operationName;
        this.topLevelFields = topLevelFields;
        this.fieldToNormalizedField = fieldToNormalizedField;
        this.normalizedFieldToMergedField = normalizedFieldToMergedField;
        this.coordinatesToNormalizedFields = coordinatesToNormalizedFields;
    }

    public OperationDefinition.Operation getOperation() {
        return operation;
    }

    public String getOperationName() {
        return operationName;
    }

    public ImmutableListMultimap<FieldCoordinates, ExecutableNormalizedField> getCoordinatesToNormalizedFields() {
        return coordinatesToNormalizedFields;
    }

    public List<ExecutableNormalizedField> getTopLevelFields() {
        return topLevelFields;
    }

    /**
     * This is a multimap: the size of it reflects the all the normalized fields
     *
     * @return an immutable list multi map of field to normalised field
     */
    public ImmutableListMultimap<Field, ExecutableNormalizedField> getFieldToNormalizedField() {
        return fieldToNormalizedField;
    }

    public List<ExecutableNormalizedField> getNormalizedFields(Field field) {
        return fieldToNormalizedField.get(field);
    }

    public Map<ExecutableNormalizedField, MergedField> getNormalizedFieldToMergedField() {
        return normalizedFieldToMergedField;
    }

    public MergedField getMergedField(ExecutableNormalizedField executableNormalizedField) {
        return normalizedFieldToMergedField.get(executableNormalizedField);
    }

    public ExecutableNormalizedField getNormalizedField(MergedField mergedField, GraphQLFieldsContainer fieldsContainer, ResultPath resultPath) {
        List<ExecutableNormalizedField> executableNormalizedFields = fieldToNormalizedField.get(mergedField.getSingleField());
        List<String> keysOnlyPath = resultPath.getKeysOnly();
        for (ExecutableNormalizedField executableNormalizedField : executableNormalizedFields) {
            if (executableNormalizedField.getListOfResultKeys().equals(keysOnlyPath)) {
                if (executableNormalizedField.getObjectTypeNames().contains(fieldsContainer.getName())) {
                    return executableNormalizedField;
                }
            }
        }
        return Assert.assertShouldNeverHappen("normalized field not found");
    }
}
