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
public class NormalizedQueryTree {

    private final List<NormalizedField> topLevelFields;
    private final ImmutableListMultimap<Field, NormalizedField> fieldToNormalizedField;
    private final Map<NormalizedField, MergedField> normalizedFieldToMergedField;
    private final ImmutableListMultimap<FieldCoordinates, NormalizedField> coordinatesToNormalizedFields;

    public NormalizedQueryTree(List<NormalizedField> topLevelFields,
                               ImmutableListMultimap<Field, NormalizedField> fieldToNormalizedField,
                               Map<NormalizedField, MergedField> normalizedFieldToMergedField,
                               ImmutableListMultimap<FieldCoordinates, NormalizedField> coordinatesToNormalizedFields) {
        this.topLevelFields = topLevelFields;
        this.fieldToNormalizedField = fieldToNormalizedField;
        this.normalizedFieldToMergedField = normalizedFieldToMergedField;
        this.coordinatesToNormalizedFields = coordinatesToNormalizedFields;
    }

    public ImmutableListMultimap<FieldCoordinates, NormalizedField> getCoordinatesToNormalizedFields() {
        return coordinatesToNormalizedFields;
    }

    public List<NormalizedField> getTopLevelFields() {
        return topLevelFields;
    }

    /**
     * This is a multimap: the size of it reflects the all the normalized fields
     *
     * @return
     */
    public ImmutableListMultimap<Field, NormalizedField> getFieldToNormalizedField() {
        return fieldToNormalizedField;
    }

    public List<NormalizedField> getNormalizedFields(Field field) {
        return fieldToNormalizedField.get(field);
    }

    public Map<NormalizedField, MergedField> getNormalizedFieldToMergedField() {
        return normalizedFieldToMergedField;
    }

    public MergedField getMergedField(NormalizedField normalizedField) {
        return normalizedFieldToMergedField.get(normalizedField);
    }

    public NormalizedField getNormalizedField(MergedField mergedField, GraphQLFieldsContainer fieldsContainer, ResultPath resultPath) {
        List<NormalizedField> normalizedFields = fieldToNormalizedField.get(mergedField.getSingleField());
        List<String> keysOnlyPath = resultPath.getKeysOnly();
        for (NormalizedField normalizedField : normalizedFields) {
            if (normalizedField.getListOfResultKeys().equals(keysOnlyPath)) {
                if (normalizedField.getObjectTypeNames().contains(fieldsContainer.getName())) {
                    return normalizedField;
                }
            }
        }
        return Assert.assertShouldNeverHappen("normalized field not found");
    }


}
