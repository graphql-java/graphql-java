package graphql.normalized;

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
    private final Map<Field, List<NormalizedField>> fieldToNormalizedField;
    private final Map<NormalizedField, MergedField> normalizedFieldToMergedField;
    private final Map<FieldCoordinates, List<NormalizedField>> coordinatesToNormalizedFields;

    public NormalizedQueryTree(List<NormalizedField> topLevelFields,
                               Map<Field, List<NormalizedField>> fieldToNormalizedField,
                               Map<NormalizedField, MergedField> normalizedFieldToMergedField,
                               Map<FieldCoordinates, List<NormalizedField>> coordinatesToNormalizedFields) {
        this.topLevelFields = topLevelFields;
        this.fieldToNormalizedField = fieldToNormalizedField;
        this.normalizedFieldToMergedField = normalizedFieldToMergedField;
        this.coordinatesToNormalizedFields = coordinatesToNormalizedFields;
    }

    public Map<FieldCoordinates, List<NormalizedField>> getCoordinatesToNormalizedFields() {
        return coordinatesToNormalizedFields;
    }

    public List<NormalizedField> getTopLevelFields() {
        return topLevelFields;
    }

    public Map<Field, List<NormalizedField>> getFieldToNormalizedField() {
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
                if (normalizedField.getObjectType() == fieldsContainer) {
                    return normalizedField;
                }
            }
        }
        return Assert.assertShouldNeverHappen("normalized field not found");
    }


}
