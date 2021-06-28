package graphql.normalized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import graphql.Assert;
import graphql.Internal;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.language.VariableDefinition;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
public class PreNormalizedQuery {

    private final List<PreNormalizedField> topLevelFields;
    private final ImmutableListMultimap<Field, PreNormalizedField> fieldToPreNormalizedField;
    private final Map<PreNormalizedField, MergedField> preNormalizedFieldToMergedField;
    private final ImmutableListMultimap<FieldCoordinates, PreNormalizedField> coordinatesToPreNormalizedFields;
    private final ImmutableList<VariableDefinition> variableDefinitions;

    public PreNormalizedQuery(List<PreNormalizedField> topLevelFields,
                              ImmutableListMultimap<Field, PreNormalizedField> fieldToPreNormalizedField,
                              Map<PreNormalizedField, MergedField> PreNormalizedFieldToMergedField,
                              ImmutableListMultimap<FieldCoordinates, PreNormalizedField> coordinatesToPreNormalizedFields,
                              ImmutableList<VariableDefinition> variableDefinitions) {
        this.topLevelFields = topLevelFields;
        this.fieldToPreNormalizedField = fieldToPreNormalizedField;
        this.preNormalizedFieldToMergedField = PreNormalizedFieldToMergedField;
        this.coordinatesToPreNormalizedFields = coordinatesToPreNormalizedFields;
        this.variableDefinitions = variableDefinitions;
    }

    public ImmutableListMultimap<FieldCoordinates, PreNormalizedField> getCoordinatesToPreNormalizedFields() {
        return coordinatesToPreNormalizedFields;
    }

    public List<PreNormalizedField> getTopLevelFields() {
        return topLevelFields;
    }

    public ImmutableList<VariableDefinition> getVariableDefinitions() {
        return variableDefinitions;
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
        return preNormalizedFieldToMergedField;
    }

    public MergedField getMergedField(PreNormalizedField PreNormalizedField) {
        return preNormalizedFieldToMergedField.get(PreNormalizedField);
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

    public MergedSelectionSet getSubSelection(MergedField mergedField,
                                              GraphQLObjectType objectType,
                                              ResultPath resultPath,
                                              GraphQLObjectType resolvedChildType,
                                              Map<String, Object> coercedVariables) {
        PreNormalizedField normalizedField = getPreNormalizedField(mergedField, objectType, resultPath);

        Map<String, MergedField> subFieldsMap = new LinkedHashMap<>();
        for (PreNormalizedField child : normalizedField.getChildren()) {
            // only add child if the type matches
            if (!child.getObjectTypeNames().contains(resolvedChildType.getName())) {
                continue;
            }
            if (!child.getIncludeCondition().evaluate(coercedVariables)) {
                continue;
            }
            MergedField newMergedField = preNormalizedFieldToMergedField.get(child);
            subFieldsMap.put(child.getResultKey(), newMergedField);
        }
        return MergedSelectionSet.newMergedSelectionSet().subFields(subFieldsMap).build();
    }


}
