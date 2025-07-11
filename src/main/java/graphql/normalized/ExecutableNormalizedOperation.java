package graphql.normalized;

import com.google.common.collect.ImmutableListMultimap;
import graphql.Assert;
import graphql.PublicApi;
import graphql.execution.MergedField;
import graphql.execution.ResultPath;
import graphql.execution.directives.QueryDirectives;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldsContainer;

import java.util.List;
import java.util.Map;

/**
 * A {@link ExecutableNormalizedOperation} represent how the text of a graphql operation (sometimes known colloquially as a query)
 * will be executed at runtime according to the graphql specification.  It handles complex mechanisms like merging
 * duplicate fields into one and also detecting when the types of a given field may actually be for more than one possible object
 * type.
 * <p>
 * An operation consists of a list of {@link ExecutableNormalizedField}s in a parent child hierarchy
 */
@PublicApi
public class ExecutableNormalizedOperation implements GraphQlNormalizedOperation {
    private final OperationDefinition.Operation operation;
    private final String operationName;
    private final List<ExecutableNormalizedField> topLevelFields;
    private final ImmutableListMultimap<Field, ExecutableNormalizedField> fieldToNormalizedField;
    private final Map<ExecutableNormalizedField, MergedField> normalizedFieldToMergedField;
    private final Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives;
    private final ImmutableListMultimap<FieldCoordinates, ExecutableNormalizedField> coordinatesToNormalizedFields;
    private final int operationFieldCount;
    private final int operationDepth;

    public ExecutableNormalizedOperation(
            OperationDefinition.Operation operation,
            String operationName,
            List<ExecutableNormalizedField> topLevelFields,
            ImmutableListMultimap<Field, ExecutableNormalizedField> fieldToNormalizedField,
            Map<ExecutableNormalizedField, MergedField> normalizedFieldToMergedField,
            Map<ExecutableNormalizedField, QueryDirectives> normalizedFieldToQueryDirectives,
            ImmutableListMultimap<FieldCoordinates, ExecutableNormalizedField> coordinatesToNormalizedFields,
            int operationFieldCount,
            int operationDepth) {
        this.operation = operation;
        this.operationName = operationName;
        this.topLevelFields = topLevelFields;
        this.fieldToNormalizedField = fieldToNormalizedField;
        this.normalizedFieldToMergedField = normalizedFieldToMergedField;
        this.normalizedFieldToQueryDirectives = normalizedFieldToQueryDirectives;
        this.coordinatesToNormalizedFields = coordinatesToNormalizedFields;
        this.operationFieldCount = operationFieldCount;
        this.operationDepth = operationDepth;
    }

    /**
     * @return operation AST being executed
     */
    public OperationDefinition.Operation getOperation() {
        return operation;
    }

    /**
     * @return the operation name, which can be null
     */
    public String getOperationName() {
        return operationName;
    }

    /**
     * @return This returns how many {@link ExecutableNormalizedField}s are in the operation.
     */
    public int getOperationFieldCount() {
        return operationFieldCount;
    }

    /**
     * @return This returns the depth of the operation
     */
    public int getOperationDepth() {
        return operationDepth;
    }

    /**
     * This multimap shows how a given {@link ExecutableNormalizedField} maps to a one or more field coordinate in the schema
     *
     * @return a multimap of fields to schema field coordinates
     */
    public ImmutableListMultimap<FieldCoordinates, ExecutableNormalizedField> getCoordinatesToNormalizedFields() {
        return coordinatesToNormalizedFields;
    }

    /**
     * @return a list of the top level {@link ExecutableNormalizedField}s in this operation.
     */
    public List<ExecutableNormalizedField> getTopLevelFields() {
        return topLevelFields;
    }

    /**
     * This is a multimap and  the size of it reflects all the normalized fields in the operation
     *
     * @return an immutable list multimap of {@link Field} to {@link ExecutableNormalizedField}
     */
    public ImmutableListMultimap<Field, ExecutableNormalizedField> getFieldToNormalizedField() {
        return fieldToNormalizedField;
    }

    /**
     * Looks up one or more {@link ExecutableNormalizedField}s given a {@link Field} AST element in the operation
     *
     * @param field the field to look up
     *
     * @return zero, one or more possible {@link ExecutableNormalizedField}s that represent that field
     */
    public List<ExecutableNormalizedField> getNormalizedFields(Field field) {
        return fieldToNormalizedField.get(field);
    }

    /**
     * @return a map of {@link ExecutableNormalizedField} to {@link MergedField}s
     */
    public Map<ExecutableNormalizedField, MergedField> getNormalizedFieldToMergedField() {
        return normalizedFieldToMergedField;
    }

    /**
     * Looks up the {@link MergedField} given a {@link ExecutableNormalizedField}
     *
     * @param executableNormalizedField the field to use the key
     *
     * @return a {@link MergedField} or null if its not present
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

    @Override
    public GraphQlNormalizedField getGraphQlNormalizedField(MergedField mergedField, GraphQLFieldsContainer fieldsContainer, ResultPath resultPath) {
        return getNormalizedField(mergedField, fieldsContainer, resultPath);
    }
}
