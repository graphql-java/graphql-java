package graphql.normalized;

import com.google.common.collect.ImmutableListMultimap;
import graphql.Assert;
import graphql.Internal;
import graphql.execution.MergedField;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldsContainer;

import java.util.List;
import java.util.Map;

@Internal
public class PreNormalizedQuery {

    private final List<PreNormalizedField> topLevelFields;
    private final ImmutableListMultimap<Field, PreNormalizedField> fieldToPreNormalizedField;
    private final Map<PreNormalizedField, MergedField> PreNormalizedFieldToMergedField;
    private final ImmutableListMultimap<FieldCoordinates, PreNormalizedField> coordinatesToPreNormalizedFields;

    public PreNormalizedQuery(List<PreNormalizedField> topLevelFields,
                              ImmutableListMultimap<Field, PreNormalizedField> fieldToPreNormalizedField,
                              Map<PreNormalizedField, MergedField> PreNormalizedFieldToMergedField,
                              ImmutableListMultimap<FieldCoordinates, PreNormalizedField> coordinatesToPreNormalizedFields) {
        this.topLevelFields = topLevelFields;
        this.fieldToPreNormalizedField = fieldToPreNormalizedField;
        this.PreNormalizedFieldToMergedField = PreNormalizedFieldToMergedField;
        this.coordinatesToPreNormalizedFields = coordinatesToPreNormalizedFields;
    }

    public ImmutableListMultimap<FieldCoordinates, PreNormalizedField> getCoordinatesToPreNormalizedFields() {
        return coordinatesToPreNormalizedFields;
    }

    public List<PreNormalizedField> getTopLevelFields() {
        return topLevelFields;
    }

    /**
     * This is a multimap: the size of it reflects the all the normalized fields
     *
     * @return an immutable list multi map of field to normalised field
     */
    public ImmutableListMultimap<Field, PreNormalizedField> getFieldToPreNormalizedField() {
        return fieldToPreNormalizedField;
    }

    public List<PreNormalizedField> getPreNormalizedFields(Field field) {
        return fieldToPreNormalizedField.get(field);
    }

    public Map<PreNormalizedField, MergedField> getPreNormalizedFieldToMergedField() {
        return PreNormalizedFieldToMergedField;
    }

    public MergedField getMergedField(PreNormalizedField PreNormalizedField) {
        return PreNormalizedFieldToMergedField.get(PreNormalizedField);
    }

    public PreNormalizedField getPreNormalizedField(MergedField mergedField, GraphQLFieldsContainer fieldsContainer, ResultPath resultPath) {
        List<PreNormalizedField> PreNormalizedFields = fieldToPreNormalizedField.get(mergedField.getSingleField());
        List<String> keysOnlyPath = resultPath.getKeysOnly();
        for (PreNormalizedField PreNormalizedField : PreNormalizedFields) {
            if (PreNormalizedField.getListOfResultKeys().equals(keysOnlyPath)) {
                if (PreNormalizedField.getObjectTypeNames().contains(fieldsContainer.getName())) {
                    return PreNormalizedField;
                }
            }
        }
        return Assert.assertShouldNeverHappen("normalized field not found");
    }


}
