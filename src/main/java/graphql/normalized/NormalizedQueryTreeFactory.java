package graphql.normalized;

import graphql.Internal;
import graphql.execution.MergedField;
import graphql.execution.ValuesResolver;
import graphql.execution.nextgen.Common;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.normalized.FieldCollectorNormalizedQuery.CollectFieldResult;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
public class NormalizedQueryTreeFactory {

    private final ValuesResolver valuesResolver = new ValuesResolver();

    public static NormalizedQueryTree createNormalizedQuery(GraphQLSchema graphQLSchema,
                                                            Document document,
                                                            String operationName,
                                                            Map<String, Object> coercedVariableValues) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operationName);
        return new NormalizedQueryTreeFactory().createNormalizedQueryImpl(graphQLSchema, getOperationResult.operationDefinition, getOperationResult.fragmentsByName, coercedVariableValues, null);
    }


    public static NormalizedQueryTree createNormalizedQuery(GraphQLSchema graphQLSchema,
                                                            OperationDefinition operationDefinition,
                                                            Map<String, FragmentDefinition> fragments,
                                                            Map<String, Object> coercedVariableValues) {
        return new NormalizedQueryTreeFactory().createNormalizedQueryImpl(graphQLSchema, operationDefinition, fragments, coercedVariableValues, null);
    }

    public static NormalizedQueryTree createNormalizedQueryWithRawVariables(GraphQLSchema graphQLSchema,
                                                                            Document document,
                                                                            String operationName,
                                                                            Map<String, Object> rawVariables) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operationName);
        return new NormalizedQueryTreeFactory().createNormalizedQueryImplWithRawVariables(graphQLSchema, getOperationResult.operationDefinition, getOperationResult.fragmentsByName, rawVariables);
    }

    private NormalizedQueryTree createNormalizedQueryImplWithRawVariables(GraphQLSchema graphQLSchema,
                                                                          OperationDefinition operationDefinition,
                                                                          Map<String, FragmentDefinition> fragments,
                                                                          Map<String, Object> rawVariables
    ) {

        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();
        Map<String, Object> coerceVariableValues = valuesResolver.coerceVariableValues(graphQLSchema, variableDefinitions, rawVariables);
        Map<String, NormalizedInputValue> normalizedVariableValues = valuesResolver.coerceNormalizedVariableValues(graphQLSchema, variableDefinitions, rawVariables);
        return createNormalizedQueryImpl(graphQLSchema, operationDefinition, fragments, coerceVariableValues, normalizedVariableValues);
    }

    /**
     * Creates a new Normalized query tree for the provided query
     */
    private NormalizedQueryTree createNormalizedQueryImpl(GraphQLSchema graphQLSchema,
                                                          OperationDefinition operationDefinition,
                                                          Map<String, FragmentDefinition> fragments,
                                                          Map<String, Object> coercedVariableValues,
                                                          @Nullable Map<String, NormalizedInputValue> normalizedVariableValues) {


        FieldCollectorNormalizedQuery fieldCollector = new FieldCollectorNormalizedQuery();
        FieldCollectorNormalizedQueryParams parameters = FieldCollectorNormalizedQueryParams
                .newParameters()
                .fragments(fragments)
                .schema(graphQLSchema)
                .coercedVariables(coercedVariableValues)
                .normalizedVariables(normalizedVariableValues)
                .build();

        GraphQLObjectType rootType = Common.getOperationRootType(graphQLSchema, operationDefinition);
        CollectFieldResult topLevelFields = fieldCollector.collectFromOperation(parameters, operationDefinition, rootType);

        Map<Field, List<NormalizedField>> fieldToNormalizedField = new LinkedHashMap<>();
        Map<NormalizedField, MergedField> normalizedFieldToMergedField = new LinkedHashMap<>();
        Map<FieldCoordinates, List<NormalizedField>> coordinatesToNormalizedFields = new LinkedHashMap<>();

        List<NormalizedField> realRoots = new ArrayList<>();

        for (NormalizedField topLevel : topLevelFields.getChildren()) {

            MergedField mergedField = topLevelFields.getMergedFieldByNormalized().get(topLevel);
            NormalizedField realTopLevel = buildFieldWithChildren(topLevel, mergedField, fieldCollector, parameters, fieldToNormalizedField, normalizedFieldToMergedField, coordinatesToNormalizedFields, 1);
            fixUpParentReference(realTopLevel);

            normalizedFieldToMergedField.put(realTopLevel, mergedField);
            FieldCoordinates coordinates = FieldCoordinates.coordinates(realTopLevel.getObjectType(), realTopLevel.getFieldDefinition());
            coordinatesToNormalizedFields.computeIfAbsent(coordinates, k -> new ArrayList<>()).add(realTopLevel);
            updateByAstFieldMap(realTopLevel, mergedField, fieldToNormalizedField);
            realRoots.add(realTopLevel);
        }
        return new NormalizedQueryTree(realRoots, fieldToNormalizedField, normalizedFieldToMergedField, coordinatesToNormalizedFields);
    }

    private void fixUpParentReference(NormalizedField rootNormalizedField) {
        for (NormalizedField child : rootNormalizedField.getChildren()) {
            child.replaceParent(rootNormalizedField);
        }
    }


    private NormalizedField buildFieldWithChildren(NormalizedField field,
                                                   MergedField mergedField,
                                                   FieldCollectorNormalizedQuery fieldCollector,
                                                   FieldCollectorNormalizedQueryParams fieldCollectorNormalizedQueryParams,
                                                   Map<Field, List<NormalizedField>> fieldToMergedField,
                                                   Map<NormalizedField, MergedField> normalizedFieldToMergedField,
                                                   Map<FieldCoordinates, List<NormalizedField>> coordinatesToNormalizedFields,
                                                   int curLevel) {
        CollectFieldResult fieldsWithoutChildren = fieldCollector.collectFields(fieldCollectorNormalizedQueryParams, field, mergedField, curLevel + 1);
        List<NormalizedField> realChildren = new ArrayList<>();
        for (NormalizedField fieldWithoutChildren : fieldsWithoutChildren.getChildren()) {
            MergedField mergedFieldForChild = fieldsWithoutChildren.getMergedFieldByNormalized().get(fieldWithoutChildren);
            NormalizedField realChild = buildFieldWithChildren(fieldWithoutChildren, mergedFieldForChild, fieldCollector, fieldCollectorNormalizedQueryParams, fieldToMergedField, normalizedFieldToMergedField, coordinatesToNormalizedFields, curLevel + 1);
            fixUpParentReference(realChild);

            normalizedFieldToMergedField.put(realChild, mergedFieldForChild);
            FieldCoordinates coordinates = FieldCoordinates.coordinates(realChild.getObjectType(), realChild.getFieldDefinition());
            coordinatesToNormalizedFields.computeIfAbsent(coordinates, k -> new ArrayList<>()).add(realChild);

            realChildren.add(realChild);

            updateByAstFieldMap(realChild, mergedFieldForChild, fieldToMergedField);
        }
        return field.transform(builder -> builder.children(realChildren));
    }

    private void updateByAstFieldMap(NormalizedField normalizedField, MergedField mergedField, Map<Field, List<NormalizedField>> fieldToNormalizedField) {
        for (Field astField : mergedField.getFields()) {
            fieldToNormalizedField.computeIfAbsent(astField, ignored -> new ArrayList<>()).add(normalizedField);
        }
    }
}
