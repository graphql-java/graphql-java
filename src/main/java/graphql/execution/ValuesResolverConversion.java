package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.values.InputInterceptor;
import graphql.language.ArrayValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.normalized.NormalizedInputValue;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.InputValueWithState;
import graphql.schema.visibility.DefaultGraphqlFieldVisibility;
import graphql.schema.visibility.GraphqlFieldVisibility;
import graphql.util.FpKit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.map;
import static graphql.execution.ValuesResolver.ValueMode.NORMALIZED;
import static graphql.language.NullValue.newNullValue;
import static graphql.language.ObjectField.newObjectField;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOneAs;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;
import static java.util.stream.Collectors.toList;

/**
 * This class, originally broken out from {@link  ValuesResolver} contains code for the conversion of values
 * from one form (literal, external etc..) to another.
 */
@SuppressWarnings("rawtypes")
@Internal
class ValuesResolverConversion {

    static Object valueToLiteralImpl(GraphqlFieldVisibility fieldVisibility,
                                     InputValueWithState inputValueWithState,
                                     GraphQLType type,
                                     ValuesResolver.ValueMode valueMode,
                                     GraphQLContext graphqlContext,
                                     Locale locale) {
        if (inputValueWithState.isInternal()) {
            if (valueMode == NORMALIZED) {
                return assertShouldNeverHappen("can't infer normalized structure");
            }
            Value<?> value = ValuesResolverLegacy.valueToLiteralLegacy(
                    inputValueWithState.getValue(),
                    type,
                    graphqlContext,
                    locale);
            //
            // the valueToLiteralLegacy() nominally cant know if null means never set or is set to a null value
            // but this code can know - its is SET to a value so, it MUST be a Null Literal
            // this method would assert at the end of it if inputValueWithState.isNotSet() were true
            //
            return value == null ? NullValue.of() : value;
        }
        if (inputValueWithState.isLiteral()) {
            return inputValueWithState.getValue();
        }
        if (inputValueWithState.isExternal()) {
            return externalValueToLiteral(
                    fieldVisibility,
                    inputValueWithState.getValue(),
                    (GraphQLInputType) type,
                    valueMode,
                    graphqlContext,
                    locale);
        }
        return assertShouldNeverHappen("unexpected value state " + inputValueWithState);
    }

    /**
     * Converts an external value to an internal value
     *
     * @param fieldVisibility the field visibility to use
     * @param externalValue   the input external value
     * @param type            the type of input value
     * @param graphqlContext  the GraphqlContext to use
     * @param locale          the Locale to use
     * @return a value converted to an internal value
     */
    static Object externalValueToInternalValue(GraphqlFieldVisibility fieldVisibility,
                                               Object externalValue,
                                               GraphQLInputType type,
                                               GraphQLContext graphqlContext,
                                               Locale locale) {
        InputInterceptor inputInterceptor = graphqlContext.get(InputInterceptor.class);
        return externalValueToInternalValueImpl(
                inputInterceptor,
                fieldVisibility,
                type,
                externalValue,
                graphqlContext,
                locale);
    }

    @Nullable
    static Object valueToInternalValueImpl(
            InputInterceptor inputInterceptor,
            InputValueWithState inputValueWithState,
            GraphQLInputType inputType,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        DefaultGraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;

        if (inputValueWithState.isInternal()) {
            return inputValueWithState.getValue();
        }
        if (inputValueWithState.isLiteral()) {
            return literalToInternalValue(
                    inputInterceptor,
                    fieldVisibility,
                    inputType,
                    (Value<?>) inputValueWithState.getValue(),
                    CoercedVariables.emptyVariables(),
                    graphqlContext,
                    locale);
        }
        if (inputValueWithState.isExternal()) {
            return externalValueToInternalValueImpl(
                    inputInterceptor,
                    fieldVisibility,
                    inputType,
                    inputValueWithState.getValue(),
                    graphqlContext,
                    locale);
        }
        return assertShouldNeverHappen("unexpected value state " + inputValueWithState);
    }

    /**
     * No validation: the external value is assumed to be valid.
     */
    static Object externalValueToLiteral(
            GraphqlFieldVisibility fieldVisibility,
            @Nullable Object value,
            GraphQLInputType type,
            ValuesResolver.ValueMode valueMode,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        if (value == null) {
            return newNullValue().build();
        }
        if (GraphQLTypeUtil.isNonNull(type)) {
            return externalValueToLiteral(
                    fieldVisibility,
                    value,
                    (GraphQLInputType) unwrapNonNull(type),
                    valueMode,
                    graphqlContext,
                    locale);
        }
        if (type instanceof GraphQLScalarType) {
            return externalValueToLiteralForScalar(
                    (GraphQLScalarType) type,
                    value,
                    graphqlContext,
                    locale);
        } else if (type instanceof GraphQLEnumType) {
            return externalValueToLiteralForEnum(
                    (GraphQLEnumType) type,
                    value,
                    graphqlContext,
                    locale);
        } else if (type instanceof GraphQLList) {
            return externalValueToLiteralForList(
                    fieldVisibility,
                    (GraphQLList) type,
                    value,
                    valueMode,
                    graphqlContext,
                    locale);
        } else if (type instanceof GraphQLInputObjectType) {
            return externalValueToLiteralForObject(
                    fieldVisibility,
                    (GraphQLInputObjectType) type,
                    value,
                    valueMode,
                    graphqlContext,
                    locale);
        } else {
            return assertShouldNeverHappen("unexpected type %s", type);
        }
    }

    /**
     * No validation
     */
    private static Value<?> externalValueToLiteralForScalar(
            GraphQLScalarType scalarType,
            Object value,
            GraphQLContext graphqlContext,
            @NotNull Locale locale
    ) {
        return scalarType.getCoercing().valueToLiteral(value, graphqlContext, locale);

    }

    /**
     * No validation
     */
    private static Value<?> externalValueToLiteralForEnum(
            GraphQLEnumType enumType,
            Object value,
            GraphQLContext graphqlContext,
            Locale locale) {
        return enumType.valueToLiteral(
                value,
                graphqlContext,
                locale);
    }

    /**
     * No validation
     */
    @SuppressWarnings("unchecked")
    private static Object externalValueToLiteralForList(
            GraphqlFieldVisibility fieldVisibility,
            GraphQLList listType,
            Object value,
            ValuesResolver.ValueMode valueMode,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        GraphQLInputType wrappedType = (GraphQLInputType) listType.getWrappedType();
        List<?> result = FpKit.toListOrSingletonList(value)
                .stream()
                .map(val -> externalValueToLiteral(
                        fieldVisibility,
                        val,
                        wrappedType,
                        valueMode,
                        graphqlContext,
                        locale))
                .collect(toList());
        if (valueMode == NORMALIZED) {
            return result;
        } else {
            return ArrayValue.newArrayValue().values((List<Value>) result).build();
        }
    }

    /**
     * No validation
     */
    @SuppressWarnings("unchecked")
    private static Object externalValueToLiteralForObject(
            GraphqlFieldVisibility fieldVisibility,
            GraphQLInputObjectType inputObjectType,
            Object inputValue,
            ValuesResolver.ValueMode valueMode,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        assertTrue(inputValue instanceof Map, () -> "Expect Map as input");
        Map<String, Object> inputMap = (Map<String, Object>) inputValue;
        List<GraphQLInputObjectField> fieldDefinitions = fieldVisibility.getFieldDefinitions(inputObjectType);

        Map<String, Object> normalizedResult = new LinkedHashMap<>();
        ImmutableList.Builder<ObjectField> objectFields = ImmutableList.builder();
        for (GraphQLInputObjectField inputFieldDefinition : fieldDefinitions) {
            GraphQLInputType fieldType = inputFieldDefinition.getType();
            String fieldName = inputFieldDefinition.getName();
            boolean hasValue = inputMap.containsKey(fieldName);
            Object fieldValue = inputMap.getOrDefault(fieldName, null);
            if (!hasValue && inputFieldDefinition.hasSetDefaultValue()) {
                //TODO: consider valueMode
                Object defaultValueLiteral = valueToLiteralImpl(
                        fieldVisibility,
                        inputFieldDefinition.getInputFieldDefaultValue(),
                        fieldType,
                        ValuesResolver.ValueMode.LITERAL,
                        graphqlContext,
                        locale);
                if (valueMode == ValuesResolver.ValueMode.LITERAL) {
                    normalizedResult.put(fieldName, new NormalizedInputValue(simplePrint(fieldType), defaultValueLiteral));
                } else {
                    objectFields.add(newObjectField().name(fieldName).value((Value) defaultValueLiteral).build());
                }
            } else if (hasValue) {
                if (fieldValue == null) {
                    if (valueMode == NORMALIZED) {
                        normalizedResult.put(fieldName, new NormalizedInputValue(simplePrint(fieldType), null));
                    } else {
                        objectFields.add(newObjectField().name(fieldName).value(newNullValue().build()).build());
                    }
                } else {
                    Object literal = externalValueToLiteral(
                            fieldVisibility,
                            fieldValue,
                            fieldType,
                            valueMode,
                            graphqlContext, locale);
                    if (valueMode == NORMALIZED) {
                        normalizedResult.put(fieldName, new NormalizedInputValue(simplePrint(fieldType), literal));
                    } else {
                        objectFields.add(newObjectField().name(fieldName).value((Value) literal).build());
                    }
                }
            }
        }
        if (valueMode == NORMALIZED) {
            return normalizedResult;
        }
        return ObjectValue.newObjectValue().objectFields(objectFields.build()).build();
    }

    /**
     * performs validation too
     */
    static CoercedVariables externalValueToInternalValueForVariables(
            InputInterceptor inputInterceptor,
            GraphQLSchema schema,
            List<VariableDefinition> variableDefinitions,
            RawVariables rawVariables,
            GraphQLContext graphqlContext, Locale locale
    ) {
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        Map<String, Object> coercedValues = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            try {
                String variableName = variableDefinition.getName();
                GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());
                assertTrue(variableType instanceof GraphQLInputType);
                GraphQLInputType variableInputType = (GraphQLInputType) variableType;
                // can be NullValue
                Value defaultValue = variableDefinition.getDefaultValue();
                boolean hasValue = rawVariables.containsKey(variableName);
                Object value = rawVariables.get(variableName);
                if (!hasValue && defaultValue != null) {
                    Object coercedDefaultValue = literalToInternalValue(
                            inputInterceptor,
                            fieldVisibility,
                            variableInputType,
                            defaultValue,
                            CoercedVariables.emptyVariables(),
                            graphqlContext,
                            locale);
                    coercedValues.put(variableName, coercedDefaultValue);
                } else if (isNonNull(variableType) && (!hasValue || value == null)) {
                    throw new NonNullableValueCoercedAsNullException(variableDefinition, variableType);
                } else if (hasValue) {
                    if (value == null) {
                        coercedValues.put(variableName, null);
                    } else {
                        Object coercedValue = externalValueToInternalValueImpl(
                                inputInterceptor,
                                fieldVisibility,
                                variableInputType,
                                value,
                                graphqlContext,
                                locale);
                        coercedValues.put(variableName, coercedValue);
                    }
                }
            } catch (CoercingParseValueException e) {
                throw CoercingParseValueException.newCoercingParseValueException()
                        .message(String.format("Variable '%s' has an invalid value: %s", variableDefinition.getName(), e.getMessage()))
                        .extensions(e.getExtensions())
                        .cause(e.getCause())
                        .sourceLocation(variableDefinition.getSourceLocation())
                        .build();
            } catch (NonNullableValueCoercedAsNullException e) {
                throw new NonNullableValueCoercedAsNullException(variableDefinition, e.getMessage());
            }
        }

        return CoercedVariables.of(coercedValues);
    }

    /**
     * Performs validation too
     */
    @SuppressWarnings("unchecked")
    static Object externalValueToInternalValueImpl(
            InputInterceptor inputInterceptor,
            GraphqlFieldVisibility fieldVisibility,
            GraphQLInputType graphQLType,
            Object originalValue,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws NonNullableValueCoercedAsNullException, CoercingParseValueException {
        if (isNonNull(graphQLType)) {
            Object returnValue = externalValueToInternalValueImpl(
                    inputInterceptor,
                    fieldVisibility,
                    unwrapOneAs(graphQLType),
                    originalValue,
                    graphqlContext,
                    locale);
            if (returnValue == null) {
                throw new NonNullableValueCoercedAsNullException(graphQLType);
            }
            return returnValue;
        }
        //
        // we have a @Internal hook that allows input values to be changed before they are
        // presented to scalars and enums - if it's not present then the cost is an extra `if`
        // statement.  We expect this to be NOT present most of the time
        //
        Object value = originalValue;
        if (inputInterceptor != null) {
            value = inputInterceptor.intercept(originalValue, graphQLType, graphqlContext, locale);
        }
        if (value == null) {
            return null;
        }

        if (graphQLType instanceof GraphQLScalarType) {
            return externalValueToInternalValueForScalar(
                    (GraphQLScalarType) graphQLType,
                    value,
                    graphqlContext,
                    locale);
        } else if (graphQLType instanceof GraphQLEnumType) {
            return externalValueToInternalValueForEnum(
                    (GraphQLEnumType) graphQLType,
                    value,
                    graphqlContext,
                    locale);
        } else if (graphQLType instanceof GraphQLList) {
            return externalValueToInternalValueForList(
                    inputInterceptor,
                    fieldVisibility,
                    (GraphQLList) graphQLType,
                    value,
                    graphqlContext,
                    locale);
        } else if (graphQLType instanceof GraphQLInputObjectType) {
            if (value instanceof Map) {
                return externalValueToInternalValueForObject(
                        inputInterceptor,
                        fieldVisibility,
                        (GraphQLInputObjectType) graphQLType,
                        (Map<String, Object>) value,
                        graphqlContext,
                        locale);
            } else {
                throw CoercingParseValueException.newCoercingParseValueException()
                        .message("Expected type 'Map' but was '" + value.getClass().getSimpleName() +
                                "'. Variables for input objects must be an instance of type 'Map'.")
                        .build();
            }
        } else {
            return assertShouldNeverHappen("unhandled type %s", graphQLType);
        }
    }

    /**
     * performs validation
     */
    private static Object externalValueToInternalValueForObject(
            InputInterceptor inputInterceptor,
            GraphqlFieldVisibility fieldVisibility,
            GraphQLInputObjectType inputObjectType,
            Map<String, Object> inputMap,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws NonNullableValueCoercedAsNullException, CoercingParseValueException {
        List<GraphQLInputObjectField> fieldDefinitions = fieldVisibility.getFieldDefinitions(inputObjectType);
        List<String> fieldNames = map(fieldDefinitions, GraphQLInputObjectField::getName);
        for (String providedFieldName : inputMap.keySet()) {
            if (!fieldNames.contains(providedFieldName)) {
                throw new InputMapDefinesTooManyFieldsException(inputObjectType, providedFieldName);
            }
        }

        Map<String, Object> coercedValues = new LinkedHashMap<>();

        for (GraphQLInputObjectField inputFieldDefinition : fieldDefinitions) {
            GraphQLInputType fieldType = inputFieldDefinition.getType();
            String fieldName = inputFieldDefinition.getName();
            InputValueWithState defaultValue = inputFieldDefinition.getInputFieldDefaultValue();
            boolean hasValue = inputMap.containsKey(fieldName);
            Object value = inputMap.getOrDefault(fieldName, null);
            if (!hasValue && inputFieldDefinition.hasSetDefaultValue()) {
                Object coercedDefaultValue = defaultValueToInternalValue(
                        inputInterceptor,
                        fieldVisibility,
                        defaultValue,
                        fieldType,
                        graphqlContext,
                        locale);
                coercedValues.put(fieldName, coercedDefaultValue);
            } else if (isNonNull(fieldType) && (!hasValue || value == null)) {
                throw new NonNullableValueCoercedAsNullException(fieldName, emptyList(), fieldType);
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(fieldName, null);
                } else {
                    value = externalValueToInternalValueImpl(
                            inputInterceptor,
                            fieldVisibility,
                            fieldType,
                            value,
                            graphqlContext,
                            locale);
                    coercedValues.put(fieldName, value);
                }
            }
        }
        return coercedValues;
    }

    /**
     * including validation
     */
    private static Object externalValueToInternalValueForScalar(
            GraphQLScalarType graphQLScalarType,
            Object value,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws CoercingParseValueException {
        return graphQLScalarType.getCoercing().parseValue(
                value,
                graphqlContext,
                locale);
    }

    /**
     * including validation
     */
    private static Object externalValueToInternalValueForEnum(
            GraphQLEnumType graphQLEnumType,
            Object value,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws CoercingParseValueException {
        return graphQLEnumType.parseValue(
                value,
                graphqlContext,
                locale);
    }

    /**
     * including validation
     */
    private static List externalValueToInternalValueForList(
            InputInterceptor inputInterceptor,
            GraphqlFieldVisibility fieldVisibility,
            GraphQLList graphQLList,
            Object value,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws CoercingParseValueException, NonNullableValueCoercedAsNullException {

        GraphQLInputType wrappedType = (GraphQLInputType) graphQLList.getWrappedType();
        return FpKit.toListOrSingletonList(value)
                .stream()
                .map(val -> externalValueToInternalValueImpl(
                        inputInterceptor,
                        fieldVisibility,
                        wrappedType,
                        val,
                        graphqlContext,
                        locale))
                .collect(toList());
    }

    /**
     * No validation (it was checked before via ArgumentsOfCorrectType and VariableDefaultValuesOfCorrectType)
     *
     * @param fieldVisibility  the field visibility
     * @param type             the type of the input value
     * @param inputValue       the AST literal to be changed
     * @param coercedVariables the coerced variable values
     * @param graphqlContext   the GraphqlContext to use
     * @param locale           the Locale to use
     * @return literal converted to an internal value
     */
    static Object literalToInternalValue(
            InputInterceptor inputInterceptor,
            GraphqlFieldVisibility fieldVisibility,
            GraphQLInputType type,
            Value inputValue,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        return literalToInternalValueImpl(
                inputInterceptor,
                fieldVisibility,
                type,
                inputValue,
                coercedVariables,
                graphqlContext,
                locale);
    }

    @Nullable
    private static Object literalToInternalValueImpl(
            InputInterceptor inputInterceptor,
            GraphqlFieldVisibility fieldVisibility,
            GraphQLType type,
            Value inputValue,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        if (inputValue instanceof VariableReference) {
            String variableName = ((VariableReference) inputValue).getName();
            return coercedVariables.get(variableName);
        }
        if (inputValue instanceof NullValue) {
            return null;
        }
        if (type instanceof GraphQLScalarType) {
            return literalToInternalValueForScalar(
                    inputValue,
                    (GraphQLScalarType) type,
                    coercedVariables,
                    graphqlContext,
                    locale);
        }
        if (isNonNull(type)) {
            return literalToInternalValue(
                    inputInterceptor,
                    fieldVisibility,
                    unwrapOneAs(type),
                    inputValue,
                    coercedVariables,
                    graphqlContext,
                    locale);
        }
        if (type instanceof GraphQLInputObjectType) {
            return literalToInternalValueForInputObject(
                    inputInterceptor,
                    fieldVisibility,
                    (GraphQLInputObjectType) type,
                    (ObjectValue) inputValue,
                    coercedVariables,
                    graphqlContext,
                    locale);
        }
        if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).parseLiteral(inputValue, graphqlContext, locale);
        }
        if (isList(type)) {
            return literalToInternalValueForList(
                    inputInterceptor,
                    fieldVisibility,
                    (GraphQLList) type,
                    inputValue,
                    coercedVariables,
                    graphqlContext,
                    locale);
        }
        return null;
    }

    /**
     * no validation
     */
    private static Object literalToInternalValueForScalar(
            Value inputValue,
            GraphQLScalarType scalarType,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            @NotNull Locale locale
    ) {
        // the CoercingParseLiteralException exception that could happen here has been validated earlier via ValidationUtil
        return scalarType.getCoercing().parseLiteral(
                inputValue,
                coercedVariables,
                graphqlContext,
                locale);
    }

    /**
     * no validation
     */
    private static Object literalToInternalValueForList(
            InputInterceptor inputInterceptor,
            GraphqlFieldVisibility fieldVisibility,
            GraphQLList graphQLList,
            Value value,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {

        GraphQLInputType inputType = (GraphQLInputType) graphQLList.getWrappedType();
        if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            List<Object> result = new ArrayList<>();
            for (Value singleValue : arrayValue.getValues()) {
                result.add(literalToInternalValue(
                        inputInterceptor,
                        fieldVisibility,
                        inputType,
                        singleValue,
                        coercedVariables,
                        graphqlContext,
                        locale));
            }
            return result;
        } else {
            return Collections.singletonList(
                    literalToInternalValue(
                            inputInterceptor,
                            fieldVisibility,
                            inputType,
                            value,
                            coercedVariables,
                            graphqlContext,
                            locale));
        }
    }

    /**
     * no validation
     */
    private static Object literalToInternalValueForInputObject(
            InputInterceptor inputInterceptor,
            GraphqlFieldVisibility fieldVisibility,
            GraphQLInputObjectType type,
            ObjectValue inputValue,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        Map<String, Object> coercedValues = new LinkedHashMap<>();

        Map<String, ObjectField> inputFieldsByName = mapObjectValueFieldsByName(inputValue);


        List<GraphQLInputObjectField> inputFieldTypes = fieldVisibility.getFieldDefinitions(type);
        for (GraphQLInputObjectField inputFieldDefinition : inputFieldTypes) {
            GraphQLInputType fieldType = inputFieldDefinition.getType();
            String fieldName = inputFieldDefinition.getName();
            ObjectField field = inputFieldsByName.get(fieldName);
            boolean hasValue = field != null;
            Object value;
            Value fieldValue = field != null ? field.getValue() : null;
            if (fieldValue instanceof VariableReference) {
                String variableName = ((VariableReference) fieldValue).getName();
                hasValue = coercedVariables.containsKey(variableName);
                value = coercedVariables.get(variableName);
            } else {
                value = fieldValue;
            }
            if (!hasValue && inputFieldDefinition.hasSetDefaultValue()) {
                Object coercedDefaultValue = defaultValueToInternalValue(
                        inputInterceptor,
                        fieldVisibility,
                        inputFieldDefinition.getInputFieldDefaultValue(),
                        fieldType,
                        graphqlContext,
                        locale);
                coercedValues.put(fieldName, coercedDefaultValue);
            } else if (isNonNull(fieldType) && (!hasValue || isNullValue(value))) {
                return assertShouldNeverHappen("Should have been validated before");
            } else if (hasValue) {
                if (isNullValue(value)) {
                    coercedValues.put(fieldName, value);
                } else if (fieldValue instanceof VariableReference) {
                    coercedValues.put(fieldName, value);
                } else {
                    value = literalToInternalValue(
                            inputInterceptor,
                            fieldVisibility,
                            fieldType,
                            fieldValue,
                            coercedVariables,
                            graphqlContext,
                            locale);
                    coercedValues.put(fieldName, value);
                }
            }
        }
        return coercedValues;
    }

    static boolean isNullValue(Object value) {
        if (value == null) {
            return true;
        }
        if (!(value instanceof NormalizedInputValue)) {
            return false;
        }
        return ((NormalizedInputValue) value).getValue() == null;
    }

    private static Map<String, ObjectField> mapObjectValueFieldsByName(ObjectValue inputValue) {
        Map<String, ObjectField> inputValueFieldsByName = new LinkedHashMap<>();
        for (ObjectField objectField : inputValue.getObjectFields()) {
            inputValueFieldsByName.put(objectField.getName(), objectField);
        }
        return inputValueFieldsByName;
    }

    static Object defaultValueToInternalValue(
            InputInterceptor inputInterceptor,
            GraphqlFieldVisibility fieldVisibility,
            InputValueWithState defaultValue,
            GraphQLInputType type,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        if (defaultValue.isInternal()) {
            return defaultValue.getValue();
        }
        if (defaultValue.isLiteral()) {
            // default value literals can't reference variables, this is why the variables are empty
            return literalToInternalValue(
                    inputInterceptor,
                    fieldVisibility,
                    type,
                    (Value) defaultValue.getValue(),
                    CoercedVariables.emptyVariables(),
                    graphqlContext,
                    locale);
        }
        if (defaultValue.isExternal()) {
            // performs validation too
            return externalValueToInternalValueImpl(
                    inputInterceptor,
                    fieldVisibility,
                    type,
                    defaultValue.getValue(),
                    graphqlContext,
                    locale);
        }
        return assertShouldNeverHappen();
    }
}
