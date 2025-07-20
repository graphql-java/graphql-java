package graphql.normalized.nf;

import com.google.common.collect.ImmutableListMultimap;
import graphql.Assert;
import graphql.ExperimentalApi;
import graphql.execution.MergedField;
import graphql.execution.ResultPath;
import graphql.execution.directives.QueryDirectives;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.normalized.GraphQlNormalizedOperation;
import graphql.normalized.GraphQlNormalizedField;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldsContainer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A {@link NormalizedOperation} represent how the text of a graphql operation (sometimes known colloquially as a query)
 * will be executed at runtime according to the graphql specification.  It handles complex mechanisms like merging
 * duplicate fields into one and also detecting when the types of a given field may actually be for more than one possible object
 * type.
 * <p>
 * An operation consists of a list of {@link NormalizedField}s in a parent child hierarchy
 */
@ExperimentalApi
public class NormalizedOperation implements GraphQlNormalizedOperation {
    private final OperationDefinition.Operation operation;
    private final String operationName;
    private final List<NormalizedField> rootFields;
    private final ImmutableListMultimap<Field, NormalizedField> fieldToNormalizedField;
    private final Map<NormalizedField, MergedField> normalizedFieldToMergedField;
    private final Map<NormalizedField, QueryDirectives> normalizedFieldToQueryDirectives;
    private final ImmutableListMultimap<FieldCoordinates, NormalizedField> coordinatesToNormalizedFields;
    private final int operationFieldCount;
    private final int operationDepth;

    public NormalizedOperation(
            OperationDefinition.Operation operation,
            String operationName,
            List<NormalizedField> rootFields,
            ImmutableListMultimap<Field, NormalizedField> fieldToNormalizedField,
            Map<NormalizedField, MergedField> normalizedFieldToMergedField,
            Map<NormalizedField, QueryDirectives> normalizedFieldToQueryDirectives,
            ImmutableListMultimap<FieldCoordinates, NormalizedField> coordinatesToNormalizedFields,
            int operationFieldCount,
            int operationDepth) {
        this.operation = operation;
        this.operationName = operationName;
        this.rootFields = rootFields;
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
    public @Nullable String getOperationName() {
        return operationName;
    }

    /**
     * @return This returns how many {@link NormalizedField}s are in the operation.
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
     * This multimap shows how a given {@link NormalizedField} maps to a one or more field coordinate in the schema
     *
     * @return a multimap of fields to schema field coordinates
     */
    public ImmutableListMultimap<FieldCoordinates, NormalizedField> getCoordinatesToNormalizedFields() {
        return coordinatesToNormalizedFields;
    }

    /**
     * @return a list of the top level {@link NormalizedField}s in this operation.
     */
    public List<NormalizedField> getRootFields() {
        return rootFields;
    }

    /**
     * This is a multimap and  the size of it reflects all the normalized fields in the operation
     *
     * @return an immutable list multimap of {@link Field} to {@link NormalizedField}
     */
    public ImmutableListMultimap<Field, NormalizedField> getFieldToNormalizedField() {
        return fieldToNormalizedField;
    }

    /**
     * Looks up one or more {@link NormalizedField}s given a {@link Field} AST element in the operation
     *
     * @param field the field to look up
     *
     * @return zero, one or more possible {@link NormalizedField}s that represent that field
     */
    public List<NormalizedField> getNormalizedFields(Field field) {
        return fieldToNormalizedField.get(field);
    }

    /**
     * @return a map of {@link NormalizedField} to {@link MergedField}s
     */
    public Map<NormalizedField, MergedField> getNormalizedFieldToMergedField() {
        return normalizedFieldToMergedField;
    }

    /**
     * Looks up the {@link MergedField} given a {@link NormalizedField}
     *
     * @param NormalizedField the field to use the key
     *
     * @return a {@link MergedField} or null if its not present
     */
    public MergedField getMergedField(NormalizedField NormalizedField) {
        return normalizedFieldToMergedField.get(NormalizedField);
    }

    /**
     * @return a map of {@link NormalizedField} to its {@link QueryDirectives}
     */
    public Map<NormalizedField, QueryDirectives> getNormalizedFieldToQueryDirectives() {
        return normalizedFieldToQueryDirectives;

    }

    /**
     * This looks up the {@link QueryDirectives} associated with the given {@link NormalizedField}
     *
     * @param NormalizedField the executable normalised field in question
     *
     * @return the fields query directives or null
     */
    public QueryDirectives getQueryDirectives(NormalizedField NormalizedField) {
        return normalizedFieldToQueryDirectives.get(NormalizedField);
    }

    /**
     * This will find a {@link NormalizedField} given a merged field and a result path.  If this does not find a field it will assert with an exception
     *
     * @param mergedField     the merged field
     * @param fieldsContainer the containing type of that field
     * @param resultPath      the result path in play
     *
     * @return the NormalizedField
     */
    public NormalizedField getNormalizedField(MergedField mergedField, GraphQLFieldsContainer fieldsContainer, ResultPath resultPath) {
        List<NormalizedField> NormalizedFields = fieldToNormalizedField.get(mergedField.getSingleField());
        List<String> keysOnlyPath = resultPath.getKeysOnly();
        for (NormalizedField NormalizedField : NormalizedFields) {
            if (NormalizedField.getListOfResultKeys().equals(keysOnlyPath)) {
                if (NormalizedField.getObjectTypeNames().contains(fieldsContainer.getName())) {
                    return NormalizedField;
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
