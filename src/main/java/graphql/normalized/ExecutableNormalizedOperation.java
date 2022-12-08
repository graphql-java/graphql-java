package graphql.normalized;

import com.google.common.collect.ImmutableListMultimap;
import graphql.Assert;
import graphql.Internal;
import graphql.execution.MergedField;
import graphql.execution.ResultPath;
import graphql.execution.directives.QueryDirectives;
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
    private final Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives;
    private final ImmutableListMultimap<FieldCoordinates, ExecutableNormalizedField> coordinatesToNormalizedFields;

    public ExecutableNormalizedOperation(
            OperationDefinition.Operation operation,
            String operationName,
            List<ExecutableNormalizedField> topLevelFields,
            ImmutableListMultimap<Field, ExecutableNormalizedField> fieldToNormalizedField,
            Map<ExecutableNormalizedField, MergedField> normalizedFieldToMergedField,
            Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives,
            ImmutableListMultimap<FieldCoordinates, ExecutableNormalizedField> coordinatesToNormalizedFields
    ) {
        this.operation = operation;
        this.operationName = operationName;
        this.topLevelFields = topLevelFields;
        this.fieldToNormalizedField = fieldToNormalizedField;
        this.normalizedFieldToMergedField = normalizedFieldToMergedField;
        this.normalizedFieldToQueryDirectives = normalizedFieldToQueryDirectives;
        this.coordinatesToNormalizedFields = coordinatesToNormalizedFields;
    }

    /**
     * @return the type of operation
     */
    public OperationDefinition.Operation getOperation() {
        return operation;
    }

    /**
     * @return the name of the operation
     */
    public String getOperationName() {
        return operationName;
    }

    /**
     * @return a multimap {@link FieldCoordinates} to the list of {@link ExecutableNormalizedField} for that co-ordinate
     */
    public ImmutableListMultimap<FieldCoordinates, ExecutableNormalizedField> getCoordinatesToNormalizedFields() {
        return coordinatesToNormalizedFields;
    }

    /**
     * @return the top level {@link ExecutableNormalizedField}s that are involved this operation
     */
    public List<ExecutableNormalizedField> getTopLevelFields() {
        return topLevelFields;
    }

    /**
     * This is a multimap: the size of it reflects all the normalized fields in the normalized operation.
     *
     * @return an immutable multimap of {@link Field} to normalised field
     */
    public ImmutableListMultimap<Field, ExecutableNormalizedField> getFieldToNormalizedField() {
        return fieldToNormalizedField;
    }

    /**
     * This returns the list of executable normalised fields that are associated with the AST {@link Field}
     *
     * @param field the field in question
     *
     * @return a non-null list of associated {@link ExecutableNormalizedField}s
     */
    public List<ExecutableNormalizedField> getNormalizedFields(Field field) {
        return fieldToNormalizedField.get(field);
    }

    /**
     * @return a map of {@link ExecutableNormalizedField} to its {@link MergedField}
     */
    public Map<ExecutableNormalizedField, MergedField> getNormalizedFieldToMergedField() {
        return normalizedFieldToMergedField;
    }

    /**
     * This looks up the {@link MergedField} associated with the given {@link ExecutableNormalizedField}
     *
     * @param executableNormalizedField the executable normalised field in question
     *
     * @return the merged field or null
     */
    public MergedField getMergedField(ExecutableNormalizedField executableNormalizedField) {
        return normalizedFieldToMergedField.get(executableNormalizedField);
    }

    /**
     * @return a map of {@link ExecutableNormalizedField} to its {@link QueryDirectives}
     */
    public Map<ExecutableNormalizedField, QueryDirectives> getNormalizedFieldToQueryDirectives() {
        return normalizedFieldToQueryDirectives;

    }

    /**
     * This looks up the {@link QueryDirectives} associated with the given {@link ExecutableNormalizedField}
     *
     * @param executableNormalizedField the executable normalised field in question
     *
     * @return the fields query directives or null
     */
    public QueryDirectives getQueryDirectives(ExecutableNormalizedField executableNormalizedField) {
        return normalizedFieldToQueryDirectives.get(executableNormalizedField);
    }

    /**
     * This will find a {@link ExecutableNormalizedField} given a merged field and a result path.  If this does not find a field it will assert with an exception
     *
     * @param mergedField     the merged field
     * @param fieldsContainer the containing type of that field
     * @param resultPath      the result path in play
     *
     * @return the ExecutableNormalizedField
     */
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
