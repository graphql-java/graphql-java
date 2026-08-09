package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.values.InputInterceptor;
import graphql.language.ArrayValue;
import graphql.language.EnumValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.normalized.NormalizedInputValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.ExecutableSchema;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.InputValueWithState;
import graphql.schema.SchemaEnum;
import graphql.schema.SchemaInputField;
import graphql.schema.SchemaInputObject;
import graphql.schema.SchemaInputType;
import graphql.schema.SchemaList;
import graphql.schema.SchemaNonNull;
import graphql.schema.SchemaScalar;
import graphql.schema.SchemaType;
import graphql.schema.visibility.GraphqlFieldVisibility;
import graphql.util.FpKit;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;

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
        return valueToLiteralImpl(
                null,
                fieldVisibility,
                inputValueWithState,
                type,
                valueMode,
                graphqlContext,
                locale);
    }

    private static Object valueToLiteralImpl(
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            InputValueWithState inputValueWithState,
            SchemaType type,
            ValuesResolver.ValueMode valueMode,
            GraphQLContext graphqlContext,
            Locale locale) {
        if (inputValueWithState.isInternal()) {
            if (valueMode == NORMALIZED) {
                return assertShouldNeverHappen("can't infer normalized structure");
            }
            if (type instanceof GraphQLType) {
                Value<?> value = ValuesResolverLegacy.valueToLiteralLegacy(
                        inputValueWithState.getValue(),
                        (GraphQLType) type,
                        graphqlContext,
                        locale);
                return value == null ? NullValue.of() : value;
            }
            return internalValueToLiteral(
                    schema,
                    fieldVisibility,
                    inputValueWithState.getValue(),
                    type,
                    graphqlContext,
                    locale);
        }
        if (inputValueWithState.isLiteral()) {
            return inputValueWithState.getValue();
        }
        if (inputValueWithState.isExternal()) {
            return externalValueToLiteral(
                    schema,
                    fieldVisibility,
                    inputValueWithState.getValue(),
                    (SchemaInputType) type,
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
     *
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
        GraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;

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

    static Object externalValueToLiteral(
            ExecutableSchema schema,
            @Nullable Object value,
            SchemaInputType type,
            ValuesResolver.ValueMode valueMode,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        return externalValueToLiteral(
                schema,
                DEFAULT_FIELD_VISIBILITY,
                value,
                type,
                valueMode,
                graphqlContext,
                locale);
    }

    private static Object externalValueToLiteral(
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            @Nullable Object value,
            SchemaInputType type,
            ValuesResolver.ValueMode valueMode,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        if (value == null) {
            return newNullValue().build();
        }
        if (isNonNull(type)) {
            return externalValueToLiteral(
                    schema,
                    fieldVisibility,
                    value,
                    (SchemaInputType) ((SchemaNonNull) type)
                            .getWrappedType(),
                    valueMode,
                    graphqlContext,
                    locale);
        }
        if (type instanceof SchemaScalar) {
            return externalValueToLiteralForScalar(
                    schema,
                    (SchemaScalar) type,
                    value,
                    graphqlContext,
                    locale);
        } else if (type instanceof SchemaEnum) {
            return externalValueToLiteralForEnum(
                    (SchemaEnum) type,
                    value,
                    graphqlContext,
                    locale);
        } else if (type instanceof SchemaList) {
            return externalValueToLiteralForList(
                    schema,
                    fieldVisibility,
                    (SchemaList) type,
                    value,
                    valueMode,
                    graphqlContext,
                    locale);
        } else if (type instanceof SchemaInputObject) {
            return externalValueToLiteralForObject(
                    schema,
                    fieldVisibility,
                    (SchemaInputObject) type,
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
            @Nullable ExecutableSchema schema,
            SchemaScalar scalarType,
            Object value,
            GraphQLContext graphqlContext,
            @NonNull Locale locale
    ) {
        return getScalarCoercing(schema, scalarType)
                .valueToLiteral(value, graphqlContext, locale);

    }

    /**
     * No validation
     */
    private static Value<?> externalValueToLiteralForEnum(
            SchemaEnum enumType,
            Object value,
            GraphQLContext graphqlContext,
            Locale locale) {
        return enumType.valueToLiteral(value, graphqlContext, locale);
    }

    /**
     * No validation
     */
    @SuppressWarnings("unchecked")
    private static Object externalValueToLiteralForList(
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            SchemaList listType,
            Object value,
            ValuesResolver.ValueMode valueMode,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        SchemaInputType wrappedType =
                (SchemaInputType) listType.getWrappedType();
        List<Object> valueList = FpKit.toListOrSingletonList(value);
        ImmutableList.Builder<Object> resultBuilder = ImmutableList.builderWithExpectedSize(valueList.size());
        for (Object item : valueList) {
            resultBuilder.add(externalValueToLiteral(
                    schema,
                    fieldVisibility,
                    item,
                    wrappedType,
                    valueMode,
                    graphqlContext,
                    locale));
        }
        ImmutableList<?> result = resultBuilder.build();

        if (valueMode == NORMALIZED) {
            return result;
        } else {
            return ArrayValue.newArrayValue().values((ImmutableList<Value>) result).build();
        }
    }

    /**
     * No validation
     */
    @SuppressWarnings("unchecked")
    private static Object externalValueToLiteralForObject(
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            SchemaInputObject inputObjectType,
            Object inputValue,
            ValuesResolver.ValueMode valueMode,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        assertTrue(inputValue instanceof Map, "Expect Map as input");
        Map<String, Object> inputMap = (Map<String, Object>) inputValue;
        List<? extends SchemaInputField> fieldDefinitions =
                getInputFields(schema, fieldVisibility, inputObjectType);

        Map<String, Object> normalizedResult = new LinkedHashMap<>();
        ImmutableList.Builder<ObjectField> objectFields = ImmutableList.builder();
        for (SchemaInputField inputFieldDefinition : fieldDefinitions) {
            SchemaInputType fieldType = inputFieldDefinition.getType();
            String fieldName = inputFieldDefinition.getName();
            boolean hasValue = inputMap.containsKey(fieldName);
            Object fieldValue = inputMap.getOrDefault(fieldName, null);
            if (!hasValue
                    && inputFieldDefinition
                    .getInputFieldDefaultValue()
                    .isSet()) {
                Object defaultValueLiteral = valueToLiteralImpl(
                        schema,
                        fieldVisibility,
                        inputFieldDefinition.getInputFieldDefaultValue(),
                        fieldType,
                        ValuesResolver.ValueMode.LITERAL,
                        graphqlContext,
                        locale);
                if (valueMode == ValuesResolver.ValueMode.LITERAL) {
                    normalizedResult.put(
                            fieldName,
                            new NormalizedInputValue(
                                    simplePrint(fieldType),
                                    defaultValueLiteral));
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
                            schema,
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

    private static Value<?> internalValueToLiteral(
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            @Nullable Object value,
            SchemaType type,
            GraphQLContext graphqlContext,
            Locale locale) {
        if (value == null) {
            return NullValue.of();
        }
        if (type instanceof SchemaNonNull) {
            return internalValueToLiteral(
                    schema,
                    fieldVisibility,
                    value,
                    ((SchemaNonNull) type).getWrappedType(),
                    graphqlContext,
                    locale);
        }
        if (type instanceof SchemaScalar) {
            return getScalarCoercing(schema, (SchemaScalar) type)
                    .valueToLiteral(value, graphqlContext, locale);
        }
        if (type instanceof SchemaEnum) {
            return internalEnumValueToLiteral(
                    value,
                    (SchemaEnum) type,
                    graphqlContext,
                    locale);
        }
        if (type instanceof SchemaList) {
            return internalListValueToLiteral(
                    schema,
                    fieldVisibility,
                    value,
                    (SchemaList) type,
                    graphqlContext,
                    locale);
        }
        if (type instanceof SchemaInputObject) {
            return internalObjectValueToLiteral(
                    schema,
                    fieldVisibility,
                    value,
                    (SchemaInputObject) type,
                    graphqlContext,
                    locale);
        }
        return assertShouldNeverHappen("unexpected type %s", type);
    }

    private static Value<?> internalEnumValueToLiteral(
            Object value,
            SchemaEnum enumType,
            GraphQLContext graphqlContext,
            Locale locale) {
        Object serialized = enumType.serialize(
                value,
                graphqlContext,
                locale);
        return EnumValue.newEnumValue(serialized.toString()).build();
    }

    @SuppressWarnings("unchecked")
    private static Value<?> internalListValueToLiteral(
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            Object value,
            SchemaList listType,
            GraphQLContext graphqlContext,
            Locale locale) {
        List<Object> values = FpKit.toListOrSingletonList(value);
        ImmutableList.Builder<Value> result =
                ImmutableList.builderWithExpectedSize(values.size());
        for (Object item : values) {
            result.add(internalValueToLiteral(
                    schema,
                    fieldVisibility,
                    item,
                    listType.getWrappedType(),
                    graphqlContext,
                    locale));
        }
        return ArrayValue.newArrayValue().values(result.build()).build();
    }

    @SuppressWarnings("unchecked")
    private static Value<?> internalObjectValueToLiteral(
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            Object value,
            SchemaInputObject inputObjectType,
            GraphQLContext graphqlContext,
            Locale locale) {
        assertTrue(value instanceof Map, "Expect Map as input");
        Map<String, Object> values = (Map<String, Object>) value;
        ImmutableList.Builder<ObjectField> fields = ImmutableList.builder();
        for (SchemaInputField field :
                getInputFields(schema, fieldVisibility, inputObjectType)) {
            if (!values.containsKey(field.getName())) {
                continue;
            }
            Value<?> fieldValue = internalValueToLiteral(
                    schema,
                    fieldVisibility,
                    values.get(field.getName()),
                    field.getType(),
                    graphqlContext,
                    locale);
            fields.add(newObjectField()
                    .name(field.getName())
                    .value(fieldValue)
                    .build());
        }
        return ObjectValue.newObjectValue()
                .objectFields(fields.build())
                .build();
    }

    /**
     * performs validation too
     */
    static CoercedVariables externalValueToInternalValueForVariables(
            InputInterceptor inputInterceptor,
            ExecutableSchema schema,
            List<VariableDefinition> variableDefinitions,
            RawVariables rawVariables,
            GraphQLContext graphqlContext, Locale locale
    ) {
        Map<String, Object> coercedValues = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            try {
                String variableName = variableDefinition.getName();
                SchemaType variableType = getVariableType(
                        schema,
                        variableDefinition);
                assertTrue(variableType instanceof SchemaInputType);
                SchemaInputType variableInputType =
                        (SchemaInputType) variableType;
                // can be NullValue
                Value defaultValue = variableDefinition.getDefaultValue();
                boolean hasValue = rawVariables.containsKey(variableName);
                Object value = rawVariables.get(variableName);
                if (!hasValue && defaultValue != null) {
                    Object coercedDefaultValue = literalToInternalValue(
                            inputInterceptor,
                            schema,
                            variableInputType,
                            defaultValue,
                            CoercedVariables.emptyVariables(),
                            graphqlContext,
                            locale);
                    coercedValues.put(variableName, coercedDefaultValue);
                } else if (isNonNull(variableType) && (!hasValue || value == null)) {
                    throw newNonNullableValueCoercedAsNullException(
                            variableDefinition,
                            variableType);
                } else if (hasValue) {
                    if (value == null) {
                        coercedValues.put(variableName, null);
                    } else {
                        Object coercedValue = externalValueToInternalValueImpl(
                                variableName,
                                inputInterceptor,
                                schema,
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

    private static SchemaType getVariableType(
            ExecutableSchema schema,
            VariableDefinition variableDefinition) {
        if (schema instanceof GraphQLSchema) {
            return TypeFromAST.getTypeFromAST(
                    (GraphQLSchema) schema,
                    variableDefinition.getType());
        }
        return TypeFromAST.getSchemaTypeFromAST(
                schema,
                variableDefinition.getType());
    }

    static Object externalValueToInternalValueImpl(
            InputInterceptor inputInterceptor,
            GraphqlFieldVisibility fieldVisibility,
            GraphQLInputType graphQLType,
            Object originalValue,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws NonNullableValueCoercedAsNullException, CoercingParseValueException {
        return externalValueToInternalValueImpl(
                "externalValue",
                inputInterceptor,
                null,
                fieldVisibility,
                graphQLType,
                originalValue,
                graphqlContext,
                locale);
    }

    static Object externalValueToInternalValueImpl(
            String variableName,
            InputInterceptor inputInterceptor,
            ExecutableSchema schema,
            SchemaInputType type,
            Object originalValue,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws NonNullableValueCoercedAsNullException,
            CoercingParseValueException {
        return externalValueToInternalValueImpl(
                variableName,
                inputInterceptor,
                schema,
                DEFAULT_FIELD_VISIBILITY,
                type,
                originalValue,
                graphqlContext,
                locale);
    }

    private static Object externalValueToInternalValueImpl(
            InputInterceptor inputInterceptor,
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            SchemaInputType type,
            Object originalValue,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws NonNullableValueCoercedAsNullException,
            CoercingParseValueException {
        return externalValueToInternalValueImpl(
                "externalValue",
                inputInterceptor,
                schema,
                fieldVisibility,
                type,
                originalValue,
                graphqlContext,
                locale);
    }

    private static Object externalValueToInternalValueImpl(
            String variableName,
            InputInterceptor inputInterceptor,
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            SchemaInputType type,
            Object originalValue,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws NonNullableValueCoercedAsNullException,
            CoercingParseValueException {
        if (isNonNull(type)) {
            Object returnValue = externalValueToInternalValueImpl(
                    variableName,
                    inputInterceptor,
                    schema,
                    fieldVisibility,
                    (SchemaInputType) ((SchemaNonNull) type)
                            .getWrappedType(),
                    originalValue,
                    graphqlContext,
                    locale);
            if (returnValue == null) {
                throw newNonNullableValueCoercedAsNullException(type);
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
            value = inputInterceptor.intercept(
                    originalValue,
                    type,
                    graphqlContext,
                    locale);
        }
        if (value == null) {
            return null;
        }

        if (type instanceof SchemaScalar) {
            return externalValueToInternalValueForScalar(
                    schema,
                    (SchemaScalar) type,
                    value,
                    graphqlContext,
                    locale);
        } else if (type instanceof SchemaEnum) {
            return externalValueToInternalValueForEnum(
                    (SchemaEnum) type,
                    value,
                    graphqlContext,
                    locale);
        } else if (type instanceof SchemaList) {
            return externalValueToInternalValueForList(
                    inputInterceptor,
                    schema,
                    fieldVisibility,
                    (SchemaList) type,
                    value,
                    graphqlContext,
                    locale);
        } else if (type instanceof SchemaInputObject) {
            if (value instanceof Map) {
                SchemaInputObject inputObjectType =
                        (SchemaInputObject) type;
                //noinspection unchecked
                Map<String, Object> coercedMap = externalValueToInternalValueForObject(
                        inputInterceptor,
                        schema,
                        fieldVisibility,
                        inputObjectType,
                        (Map<String, Object>) value,
                        graphqlContext,
                        locale);

                ValuesResolverOneOfValidation.validateOneOfInputTypes(
                        schema,
                        inputObjectType,
                        coercedMap,
                        null,
                        variableName,
                        locale);
                return coercedMap;
            } else {
                throw CoercingParseValueException.newCoercingParseValueException()
                        .message("Expected type 'Map' but was '" + value.getClass().getSimpleName() +
                                "'. Variables for input objects must be an instance of type 'Map'.")
                        .build();
            }
        } else {
            return assertShouldNeverHappen("unhandled type %s", type);
        }
    }

    /**
     * performs validation
     */
    private static Map<String, Object> externalValueToInternalValueForObject(
            InputInterceptor inputInterceptor,
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            SchemaInputObject inputObjectType,
            Map<String, Object> inputMap,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws NonNullableValueCoercedAsNullException, CoercingParseValueException {
        List<? extends SchemaInputField> fieldDefinitions =
                getInputFields(schema, fieldVisibility, inputObjectType);
        List<String> fieldNames = map(
                fieldDefinitions,
                SchemaInputField::getName);
        for (String providedFieldName : inputMap.keySet()) {
            if (!fieldNames.contains(providedFieldName)) {
                throw newInputMapDefinesTooManyFieldsException(
                        inputObjectType,
                        providedFieldName);
            }
        }

        Map<String, Object> coercedValues = new LinkedHashMap<>();

        for (SchemaInputField inputFieldDefinition : fieldDefinitions) {
            SchemaInputType fieldType = inputFieldDefinition.getType();
            String fieldName = inputFieldDefinition.getName();
            InputValueWithState defaultValue = inputFieldDefinition.getInputFieldDefaultValue();
            boolean hasValue = inputMap.containsKey(fieldName);
            Object value = inputMap.getOrDefault(fieldName, null);
            if (!hasValue && defaultValue.isSet()) {
                Object coercedDefaultValue = defaultValueToInternalValue(
                        inputInterceptor,
                        schema,
                        fieldVisibility,
                        defaultValue,
                        fieldType,
                        graphqlContext,
                        locale);
                coercedValues.put(fieldName, coercedDefaultValue);
            } else if (isNonNull(fieldType) && (!hasValue || value == null)) {
                throw newNonNullableValueCoercedAsNullException(
                        fieldName,
                        fieldType);
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(fieldName, null);
                } else {
                    value = externalValueToInternalValueImpl(
                            inputInterceptor,
                            schema,
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
            @Nullable ExecutableSchema schema,
            SchemaScalar scalarType,
            Object value,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws CoercingParseValueException {
        return getScalarCoercing(schema, scalarType).parseValue(
                value,
                graphqlContext,
                locale);
    }

    /**
     * including validation
     */
    private static Object externalValueToInternalValueForEnum(
            SchemaEnum enumType,
            Object value,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws CoercingParseValueException {
        return enumType.parseValue(value, graphqlContext, locale);
    }

    /**
     * including validation
     */
    private static List externalValueToInternalValueForList(
            InputInterceptor inputInterceptor,
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            SchemaList listType,
            Object value,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws CoercingParseValueException, NonNullableValueCoercedAsNullException {

        SchemaInputType wrappedType =
                (SchemaInputType) listType.getWrappedType();
        List<Object> listOrSingletonList = FpKit.toListOrSingletonList(value);
        List<Object> list = FpKit.arrayListSizedTo(listOrSingletonList);
        for (Object val : listOrSingletonList) {
            list.add(externalValueToInternalValueImpl(
                    inputInterceptor,
                    schema,
                    fieldVisibility,
                    wrappedType,
                    val,
                    graphqlContext,
                    locale));
        }
        return list;
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
     *
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
                null,
                fieldVisibility,
                type,
                inputValue,
                coercedVariables,
                graphqlContext,
                locale);
    }

    static Object literalToInternalValue(
            InputInterceptor inputInterceptor,
            ExecutableSchema schema,
            SchemaInputType type,
            Value inputValue,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        return literalToInternalValueImpl(
                inputInterceptor,
                schema,
                DEFAULT_FIELD_VISIBILITY,
                type,
                inputValue,
                coercedVariables,
                graphqlContext,
                locale);
    }

    static Object literalToInternalValue(
            InputInterceptor inputInterceptor,
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            SchemaInputType type,
            Value inputValue,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        return literalToInternalValueImpl(
                inputInterceptor,
                schema,
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
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            SchemaType type,
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
        if (type instanceof SchemaScalar) {
            return literalToInternalValueForScalar(
                    schema,
                    inputValue,
                    (SchemaScalar) type,
                    coercedVariables,
                    graphqlContext,
                    locale);
        }
        if (isNonNull(type)) {
            return literalToInternalValue(
                    inputInterceptor,
                    schema,
                    fieldVisibility,
                    (SchemaInputType) ((SchemaNonNull) type)
                            .getWrappedType(),
                    inputValue,
                    coercedVariables,
                    graphqlContext,
                    locale);
        }
        if (type instanceof SchemaInputObject) {
            return literalToInternalValueForInputObject(
                    inputInterceptor,
                    schema,
                    fieldVisibility,
                    (SchemaInputObject) type,
                    (ObjectValue) inputValue,
                    coercedVariables,
                    graphqlContext,
                    locale);
        }
        if (type instanceof SchemaEnum) {
            return literalToInternalValueForEnum(
                    inputValue,
                    (SchemaEnum) type,
                    graphqlContext,
                    locale);
        }
        if (isList(type)) {
            return literalToInternalValueForList(
                    inputInterceptor,
                    schema,
                    fieldVisibility,
                    (SchemaList) type,
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
            @Nullable ExecutableSchema schema,
            Value inputValue,
            SchemaScalar scalarType,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            @NonNull Locale locale
    ) {
        // the CoercingParseLiteralException exception that could happen here has been validated earlier via ValidationUtil
        return getScalarCoercing(schema, scalarType).parseLiteral(
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
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            SchemaList listType,
            Value value,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {

        SchemaInputType inputType =
                (SchemaInputType) listType.getWrappedType();
        if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            List<Object> result = new ArrayList<>();
            for (Value singleValue : arrayValue.getValues()) {
                result.add(literalToInternalValue(
                        inputInterceptor,
                        schema,
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
                            schema,
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
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            SchemaInputObject type,
            ObjectValue inputValue,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        Map<String, Object> coercedValues = new LinkedHashMap<>();

        Map<String, ObjectField> inputFieldsByName = mapObjectValueFieldsByName(inputValue);


        List<? extends SchemaInputField> inputFieldTypes =
                getInputFields(schema, fieldVisibility, type);
        for (SchemaInputField inputFieldDefinition : inputFieldTypes) {
            SchemaInputType fieldType = inputFieldDefinition.getType();
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
            if (!hasValue
                    && inputFieldDefinition
                    .getInputFieldDefaultValue()
                    .isSet()) {
                Object coercedDefaultValue = defaultValueToInternalValue(
                        inputInterceptor,
                        schema,
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
                            schema,
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

    private static Object literalToInternalValueForEnum(
            Value<?> inputValue,
            SchemaEnum enumType,
            GraphQLContext graphqlContext,
            Locale locale) {
        return enumType.parseLiteral(
                inputValue,
                graphqlContext,
                locale);
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

    private static List<? extends SchemaInputField> getInputFields(
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            SchemaInputObject inputObjectType) {
        if (schema != null) {
            return schema.getInputFields(inputObjectType);
        }
        assertTrue(
                inputObjectType instanceof GraphQLInputObjectType,
                "A schema-less input object must be a GraphQLInputObjectType");
        return fieldVisibility.getFieldDefinitions(
                (GraphQLInputObjectType) inputObjectType);
    }

    private static Coercing<?, ?> getScalarCoercing(
            @Nullable ExecutableSchema schema,
            SchemaScalar scalarType) {
        if (schema != null) {
            return schema.getScalarCoercing(scalarType);
        }
        return scalarType.getCoercing();
    }

    static Object defaultValueToInternalValue(
            InputInterceptor inputInterceptor,
            @Nullable ExecutableSchema schema,
            GraphqlFieldVisibility fieldVisibility,
            InputValueWithState defaultValue,
            SchemaInputType type,
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
                    schema,
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
                    schema,
                    fieldVisibility,
                    type,
                    defaultValue.getValue(),
                    graphqlContext,
                    locale);
        }
        return assertShouldNeverHappen();
    }

    private static NonNullableValueCoercedAsNullException newNonNullableValueCoercedAsNullException(
            VariableDefinition variableDefinition,
            SchemaType type) {
        if (type instanceof GraphQLType) {
            return new NonNullableValueCoercedAsNullException(
                    variableDefinition,
                    (GraphQLType) type);
        }
        return new NonNullableValueCoercedAsNullException(
                variableDefinition,
                type);
    }

    private static NonNullableValueCoercedAsNullException newNonNullableValueCoercedAsNullException(
            SchemaType type) {
        if (type instanceof GraphQLType) {
            return new NonNullableValueCoercedAsNullException(
                    (GraphQLType) type);
        }
        return new NonNullableValueCoercedAsNullException(type);
    }

    private static NonNullableValueCoercedAsNullException newNonNullableValueCoercedAsNullException(
            String fieldName,
            SchemaType type) {
        if (type instanceof GraphQLType) {
            return new NonNullableValueCoercedAsNullException(
                    fieldName,
                    emptyList(),
                    (GraphQLType) type);
        }
        return new NonNullableValueCoercedAsNullException(
                fieldName,
                emptyList(),
                type);
    }

    private static InputMapDefinesTooManyFieldsException newInputMapDefinesTooManyFieldsException(
            SchemaType type,
            String fieldName) {
        if (type instanceof GraphQLType) {
            return new InputMapDefinesTooManyFieldsException(
                    (GraphQLType) type,
                    fieldName);
        }
        return new InputMapDefinesTooManyFieldsException(type, fieldName);
    }
}
