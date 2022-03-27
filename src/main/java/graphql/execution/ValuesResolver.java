package graphql.execution;


import com.google.common.collect.ImmutableList;
import graphql.AssertException;
import graphql.Internal;
import graphql.Scalars;
import graphql.VisibleForTesting;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.normalized.NormalizedInputValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.InputValueWithState;
import graphql.schema.PropertyDataFetcherHelper;
import graphql.schema.visibility.DefaultGraphqlFieldVisibility;
import graphql.schema.visibility.GraphqlFieldVisibility;
import graphql.util.FpKit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.emptyMap;
import static graphql.collect.ImmutableKit.map;
import static graphql.execution.ValuesResolver.ValueMode.NORMALIZED;
import static graphql.language.NullValue.newNullValue;
import static graphql.language.ObjectField.newObjectField;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;
import static java.util.stream.Collectors.toList;

@SuppressWarnings("rawtypes")
@Internal
public class ValuesResolver {

    public enum ValueMode {
        LITERAL,
        NORMALIZED
    }

    /**
     * This method coerces the "raw" variables values provided to the engine. The coerced values will be used to
     * provide arguments to {@link graphql.schema.DataFetchingEnvironment}
     *
     * This method is called once per execution and also performs validation.
     *
     * @param schema              the schema
     * @param variableDefinitions the variable definitions
     * @param rawVariables        the supplied variables
     *
     * @return coerced variable values as a map
     */
    public Map<String, Object> coerceVariableValues(GraphQLSchema schema,
                                                    List<VariableDefinition> variableDefinitions,
                                                    Map<String, Object> rawVariables) throws CoercingParseValueException, NonNullableValueCoercedAsNullException {

        return externalValueToInternalValueForVariables(schema, variableDefinitions, rawVariables);
    }

    /**
     * Normalized variables values are Literals with type information. No validation here!
     *
     * @param schema              the schema to use
     * @param variableDefinitions the list of variable definitions
     * @param rawVariables        the raw variables
     *
     * @return a map of the normalised values
     */
    public Map<String, NormalizedInputValue> getNormalizedVariableValues(GraphQLSchema schema,
                                                                         List<VariableDefinition> variableDefinitions,
                                                                         Map<String, Object> rawVariables) {
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        Map<String, NormalizedInputValue> result = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            String variableName = variableDefinition.getName();
            GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());
            assertTrue(variableType instanceof GraphQLInputType);
            // can be NullValue
            Value defaultValue = variableDefinition.getDefaultValue();
            boolean hasValue = rawVariables.containsKey(variableName);
            Object value = rawVariables.get(variableName);
            if (!hasValue && defaultValue != null) {
                result.put(variableName, new NormalizedInputValue(simplePrint(variableType), defaultValue));
            } else if (isNonNull(variableType) && (!hasValue || value == null)) {
                return assertShouldNeverHappen("variable values are expected to be valid");
            } else if (hasValue) {
                if (value == null) {
                    result.put(variableName, new NormalizedInputValue(simplePrint(variableType), null));
                } else {
                    Object literal = externalValueToLiteral(fieldVisibility, value, (GraphQLInputType) variableType, NORMALIZED);
                    result.put(variableName, new NormalizedInputValue(simplePrint(variableType), literal));
                }
            }
        }

        return result;

    }

    /**
     * This is not used for validation: the argument literals are all validated and the variables are validated (when coerced)
     *
     * @param argumentTypes    the list of argument types
     * @param arguments        the AST arguments
     * @param coercedVariables the coerced variables
     *
     * @return a map of named argument values
     */
    public Map<String, Object> getArgumentValues(List<GraphQLArgument> argumentTypes,
                                                 List<Argument> arguments,
                                                 Map<String, Object> coercedVariables) {
        return getArgumentValuesImpl(DEFAULT_FIELD_VISIBILITY, argumentTypes, arguments, coercedVariables);
    }

    /**
     * No validation as the arguments are assumed valid
     *
     * @param argumentTypes       the list of argument types
     * @param arguments           the AST arguments
     * @param normalizedVariables the normalised variables
     *
     * @return a map of named normalised values
     */
    public Map<String, NormalizedInputValue> getNormalizedArgumentValues(List<GraphQLArgument> argumentTypes,
                                                                         List<Argument> arguments,
                                                                         Map<String, NormalizedInputValue> normalizedVariables) {
        if (argumentTypes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, NormalizedInputValue> result = new LinkedHashMap<>();
        Map<String, Argument> argumentMap = argumentMap(arguments);
        for (GraphQLArgument argumentDefinition : argumentTypes) {
            String argumentName = argumentDefinition.getName();
            Argument argument = argumentMap.get(argumentName);
            if (argument == null) {
                continue;
            }

            // If a variable doesn't exist then we can't put it into the result Map
            if (isVariableAbsent(argument.getValue(), normalizedVariables)) {
                continue;
            }

            GraphQLInputType argumentType = argumentDefinition.getType();
            Object value = literalToNormalizedValue(DEFAULT_FIELD_VISIBILITY, argumentType, argument.getValue(), normalizedVariables);
            result.put(argumentName, new NormalizedInputValue(simplePrint(argumentType), value));
        }
        return result;
    }

    public Map<String, Object> getArgumentValues(GraphQLCodeRegistry codeRegistry,
                                                 List<GraphQLArgument> argumentTypes,
                                                 List<Argument> arguments,
                                                 Map<String, Object> coercedVariables) {
        return getArgumentValuesImpl(codeRegistry.getFieldVisibility(), argumentTypes, arguments, coercedVariables);
    }

    public static Value<?> valueToLiteral(InputValueWithState inputValueWithState, GraphQLType type) {
        return valueToLiteral(DEFAULT_FIELD_VISIBILITY, inputValueWithState, type);
    }

    /**
     * Takes a value which can be in different states (internal, literal, external value) and converts into Literal
     *
     * This assumes the value is valid!
     *
     * @param fieldVisibility     the field visibility to use
     * @param inputValueWithState the input value
     * @param type                the type of input value
     *
     * @return a value converted to a literal
     */
    public static Value<?> valueToLiteral(@NotNull GraphqlFieldVisibility fieldVisibility, @NotNull InputValueWithState inputValueWithState, @NotNull GraphQLType type) {
        return (Value<?>) valueToLiteral(fieldVisibility, inputValueWithState, type, ValueMode.LITERAL);
    }

    private static Object valueToLiteral(GraphqlFieldVisibility fieldVisibility, InputValueWithState inputValueWithState, GraphQLType type, ValueMode valueMode) {
        if (inputValueWithState.isInternal()) {
            if (valueMode == NORMALIZED) {
                return assertShouldNeverHappen("can't infer normalized structure");
            }
            return valueToLiteralLegacy(inputValueWithState.getValue(), type);
        }
        if (inputValueWithState.isLiteral()) {
            return inputValueWithState.getValue();
        }
        if (inputValueWithState.isExternal()) {
            return new ValuesResolver().externalValueToLiteral(fieldVisibility, inputValueWithState.getValue(), (GraphQLInputType) type, valueMode);
        }
        return assertShouldNeverHappen("unexpected value state " + inputValueWithState);
    }


    /**
     * Converts an external value to an internal value
     *
     * @param fieldVisibility the field visibility to use
     * @param externalValue   the input external value
     * @param type            the type of input value
     *
     * @return a value converted to an internal value
     */
    public static Object externalValueToInternalValue(GraphqlFieldVisibility fieldVisibility, Object externalValue, GraphQLInputType type) {
        return new ValuesResolver().externalValueToInternalValue(fieldVisibility, type, externalValue);
    }

    public static Object valueToInternalValue(InputValueWithState inputValueWithState, GraphQLType type) throws CoercingParseValueException, CoercingParseLiteralException {
        DefaultGraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;
        if (inputValueWithState.isInternal()) {
            return inputValueWithState.getValue();
        }
        if (inputValueWithState.isLiteral()) {
            return new ValuesResolver().literalToInternalValue(fieldVisibility, type, (Value<?>) inputValueWithState.getValue(), emptyMap());
        }
        if (inputValueWithState.isExternal()) {
            return new ValuesResolver().externalValueToInternalValue(fieldVisibility, type, inputValueWithState.getValue());
        }
        return assertShouldNeverHappen("unexpected value state " + inputValueWithState);
    }


    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T getInputValueImpl(GraphQLInputType inputType, InputValueWithState inputValue) {
        if (inputValue.isNotSet()) {
            return null;
        }
        return (T) valueToInternalValue(inputValue, inputType);
    }

    /**
     * No validation: the external value is assumed to be valid.
     */
    private Object externalValueToLiteral(GraphqlFieldVisibility fieldVisibility,
                                          @Nullable Object value,
                                          GraphQLInputType type,
                                          ValueMode valueMode
    ) {
        if (value == null) {
            return newNullValue().build();
        }
        if (GraphQLTypeUtil.isNonNull(type)) {
            return externalValueToLiteral(fieldVisibility, value, (GraphQLInputType) unwrapNonNull(type), valueMode);
        }
        if (type instanceof GraphQLScalarType) {
            return externalValueToLiteralForScalar((GraphQLScalarType) type, value);
        } else if (type instanceof GraphQLEnumType) {
            return externalValueToLiteralForEnum((GraphQLEnumType) type, value);
        } else if (type instanceof GraphQLList) {
            return externalValueToLiteralForList(fieldVisibility, (GraphQLList) type, value, valueMode);
        } else if (type instanceof GraphQLInputObjectType) {
            return externalValueToLiteralForObject(fieldVisibility, (GraphQLInputObjectType) type, value, valueMode);
        } else {
            return assertShouldNeverHappen("unexpected type %s", type);
        }
    }

    /**
     * No validation
     */
    private Value externalValueToLiteralForScalar(GraphQLScalarType scalarType, Object value) {
        return scalarType.getCoercing().valueToLiteral(value);

    }

    /**
     * No validation
     */
    private Value externalValueToLiteralForEnum(GraphQLEnumType enumType, Object value) {
        return enumType.valueToLiteral(value);
    }

    /**
     * No validation
     */
    @SuppressWarnings("unchecked")
    private Object externalValueToLiteralForList(GraphqlFieldVisibility fieldVisibility, GraphQLList listType, Object value, ValueMode valueMode) {
        GraphQLInputType wrappedType = (GraphQLInputType) listType.getWrappedType();
        List result = FpKit.toListOrSingletonList(value)
                .stream()
                .map(val -> {
                    return externalValueToLiteral(fieldVisibility, val, wrappedType, valueMode);
                })
                .collect(toList());
        if (valueMode == NORMALIZED) {
            return result;
        } else {
            return ArrayValue.newArrayValue().values(result).build();
        }
    }

    /**
     * No validation
     */
    @SuppressWarnings("unchecked")
    private Object externalValueToLiteralForObject(GraphqlFieldVisibility fieldVisibility,
                                                   GraphQLInputObjectType inputObjectType,
                                                   Object inputValue,
                                                   ValueMode valueMode) {
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
                Object defaultValueLiteral = valueToLiteral(fieldVisibility, inputFieldDefinition.getInputFieldDefaultValue(), fieldType);
                if (valueMode == ValueMode.LITERAL) {
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
                    Object literal = externalValueToLiteral(fieldVisibility,
                            fieldValue,
                            fieldType,
                            valueMode);
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
    private Map<String, Object> externalValueToInternalValueForVariables(GraphQLSchema schema,
                                                                         List<VariableDefinition> variableDefinitions,
                                                                         Map<String, Object> rawVariables) {
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        Map<String, Object> coercedValues = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            try {
                String variableName = variableDefinition.getName();
                GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());
                assertTrue(variableType instanceof GraphQLInputType);
                // can be NullValue
                Value defaultValue = variableDefinition.getDefaultValue();
                boolean hasValue = rawVariables.containsKey(variableName);
                Object value = rawVariables.get(variableName);
                if (!hasValue && defaultValue != null) {
                    Object coercedDefaultValue = literalToInternalValue(fieldVisibility, variableType, defaultValue, Collections.emptyMap());
                    coercedValues.put(variableName, coercedDefaultValue);
                } else if (isNonNull(variableType) && (!hasValue || value == null)) {
                    throw new NonNullableValueCoercedAsNullException(variableDefinition, variableType);
                } else if (hasValue) {
                    if (value == null) {
                        coercedValues.put(variableName, null);
                    } else {
                        Object coercedValue = externalValueToInternalValue(fieldVisibility, variableType, value);
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

        return coercedValues;
    }


    private Map<String, Object> getArgumentValuesImpl(GraphqlFieldVisibility fieldVisibility,
                                                      List<GraphQLArgument> argumentTypes,
                                                      List<Argument> arguments,
                                                      Map<String, Object> coercedVariables
    ) {
        if (argumentTypes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> coercedValues = new LinkedHashMap<>();
        Map<String, Argument> argumentMap = argumentMap(arguments);
        for (GraphQLArgument argumentDefinition : argumentTypes) {
            GraphQLInputType argumentType = argumentDefinition.getType();
            String argumentName = argumentDefinition.getName();
            Argument argument = argumentMap.get(argumentName);
            InputValueWithState defaultValue = argumentDefinition.getArgumentDefaultValue();
            boolean hasValue = argument != null;
            Object value;
            Value argumentValue = argument != null ? argument.getValue() : null;
            if (argumentValue instanceof VariableReference) {
                String variableName = ((VariableReference) argumentValue).getName();
                hasValue = coercedVariables.containsKey(variableName);
                value = coercedVariables.get(variableName);
            } else {
                value = argumentValue;
            }
            if (!hasValue && argumentDefinition.hasSetDefaultValue()) {
                Object coercedDefaultValue = defaultValueToInternalValue(
                        fieldVisibility,
                        defaultValue,
                        argumentType);
                coercedValues.put(argumentName, coercedDefaultValue);
            } else if (isNonNull(argumentType) && (!hasValue || isNullValue(value))) {
                throw new NonNullableValueCoercedAsNullException(argumentDefinition);
            } else if (hasValue) {
                if (isNullValue(value)) {
                    coercedValues.put(argumentName, value);
                } else if (argumentValue instanceof VariableReference) {
                    coercedValues.put(argumentName, value);
                } else {
                    value = literalToInternalValue(fieldVisibility, argumentType, argument.getValue(), coercedVariables);
                    coercedValues.put(argumentName, value);
                }
            }

        }
        return coercedValues;

    }

    private Map<String, Argument> argumentMap(List<Argument> arguments) {
        Map<String, Argument> result = new LinkedHashMap<>(arguments.size());
        for (Argument argument : arguments) {
            result.put(argument.getName(), argument);
        }
        return result;
    }


    /**
     * Performs validation too
     */
    @SuppressWarnings("unchecked")
    private Object externalValueToInternalValue(GraphqlFieldVisibility fieldVisibility,
                                                GraphQLType graphQLType,
                                                Object value) throws NonNullableValueCoercedAsNullException, CoercingParseValueException {
        if (isNonNull(graphQLType)) {
            Object returnValue =
                    externalValueToInternalValue(fieldVisibility, unwrapOne(graphQLType), value);
            if (returnValue == null) {
                throw new NonNullableValueCoercedAsNullException(graphQLType);
            }
            return returnValue;
        }

        if (value == null) {
            return null;
        }

        if (graphQLType instanceof GraphQLScalarType) {
            return externalValueToInternalValueForScalar((GraphQLScalarType) graphQLType, value);
        } else if (graphQLType instanceof GraphQLEnumType) {
            return externalValueToInternalValueForEnum((GraphQLEnumType) graphQLType, value);
        } else if (graphQLType instanceof GraphQLList) {
            return externalValueToInternalValueForList(fieldVisibility, (GraphQLList) graphQLType, value);
        } else if (graphQLType instanceof GraphQLInputObjectType) {
            if (value instanceof Map) {
                return externalValueToInternalValueForObject(fieldVisibility, (GraphQLInputObjectType) graphQLType, (Map<String, Object>) value);
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
    private Object externalValueToInternalValueForObject(GraphqlFieldVisibility fieldVisibility,
                                                         GraphQLInputObjectType inputObjectType,
                                                         Map<String, Object> inputMap
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
            Object value;
            Object fieldValue = inputMap.getOrDefault(fieldName, null);
            value = fieldValue;
            if (!hasValue && inputFieldDefinition.hasSetDefaultValue()) {
                Object coercedDefaultValue = defaultValueToInternalValue(fieldVisibility,
                        defaultValue,
                        fieldType);
                coercedValues.put(fieldName, coercedDefaultValue);
            } else if (isNonNull(fieldType) && (!hasValue || value == null)) {
                throw new NonNullableValueCoercedAsNullException(fieldName, emptyList(), fieldType);
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(fieldName, null);
                } else {
                    value = externalValueToInternalValue(fieldVisibility,
                            fieldType, value);
                    coercedValues.put(fieldName, value);
                }
            }
        }
        return coercedValues;
    }

    /**
     * including validation
     */
    private Object externalValueToInternalValueForScalar(GraphQLScalarType graphQLScalarType, Object value) throws CoercingParseValueException {
        return graphQLScalarType.getCoercing().parseValue(value);
    }

    /**
     * including validation
     */
    private Object externalValueToInternalValueForEnum(GraphQLEnumType graphQLEnumType, Object value) throws CoercingParseValueException {
        return graphQLEnumType.parseValue(value);
    }

    /**
     * including validation
     */
    private List externalValueToInternalValueForList(GraphqlFieldVisibility fieldVisibility,
                                                     GraphQLList graphQLList,
                                                     Object value
    ) throws CoercingParseValueException, NonNullableValueCoercedAsNullException {

        GraphQLType wrappedType = graphQLList.getWrappedType();
        return FpKit.toListOrSingletonList(value)
                .stream()
                .map(val -> externalValueToInternalValue(fieldVisibility, wrappedType, val))
                .collect(toList());
    }

    public Object literalToNormalizedValue(GraphqlFieldVisibility fieldVisibility,
                                           GraphQLType type,
                                           Value inputValue,
                                           Map<String, NormalizedInputValue> normalizedVariables
    ) {
        if (inputValue instanceof VariableReference) {
            String varName = ((VariableReference) inputValue).getName();
            return normalizedVariables.get(varName).getValue();
        }

        if (inputValue instanceof NullValue) {
            return null;
        }
        if (type instanceof GraphQLScalarType) {
            return inputValue;
        }
        if (isNonNull(type)) {
            return literalToNormalizedValue(fieldVisibility, unwrapOne(type), inputValue, normalizedVariables);
        }
        if (type instanceof GraphQLInputObjectType) {
            return literalToNormalizedValueForInputObject(fieldVisibility, (GraphQLInputObjectType) type, (ObjectValue) inputValue, normalizedVariables);
        }
        if (type instanceof GraphQLEnumType) {
            return inputValue;
        }
        if (isList(type)) {
            return literalToNormalizedValueForList(fieldVisibility, (GraphQLList) type, inputValue, normalizedVariables);
        }
        return null;
    }

    private Object literalToNormalizedValueForInputObject(GraphqlFieldVisibility fieldVisibility,
                                                          GraphQLInputObjectType type,
                                                          ObjectValue inputObjectLiteral,
                                                          Map<String, NormalizedInputValue> normalizedVariables) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (ObjectField field : inputObjectLiteral.getObjectFields()) {
            // If a variable doesn't exist then we can't put it into the result Map
            if (isVariableAbsent(field.getValue(), normalizedVariables)) {
                continue;
            }

            GraphQLInputType fieldType = type.getField(field.getName()).getType();
            Object fieldValue = literalToNormalizedValue(fieldVisibility, fieldType, field.getValue(), normalizedVariables);
            result.put(field.getName(), new NormalizedInputValue(simplePrint(fieldType), fieldValue));
        }
        return result;
    }

    private List<Object> literalToNormalizedValueForList(GraphqlFieldVisibility fieldVisibility,
                                                         GraphQLList type,
                                                         Value value,
                                                         Map<String, NormalizedInputValue> normalizedVariables) {
        if (value instanceof ArrayValue) {
            List<Object> result = new ArrayList<>();
            for (Value valueInArray : ((ArrayValue) value).getValues()) {
                result.add(literalToNormalizedValue(fieldVisibility, type.getWrappedType(), valueInArray, normalizedVariables));
            }
            return result;
        } else {
            return Collections.singletonList(literalToNormalizedValue(fieldVisibility, type.getWrappedType(), value, normalizedVariables));
        }
    }


    /**
     * No validation (it was checked before via ArgumentsOfCorrectType and VariableDefaultValuesOfCorrectType)
     *
     * @param fieldVisibility  the field visibility
     * @param type             the type of the input value
     * @param inputValue       the AST literal to be changed
     * @param coercedVariables the coerced variable values
     *
     * @return literal converted to an internal value
     */
    public Object literalToInternalValue(GraphqlFieldVisibility fieldVisibility,
                                         GraphQLType type,
                                         Value inputValue,
                                         Map<String, Object> coercedVariables) {

        if (inputValue instanceof VariableReference) {
            Object variableValue = coercedVariables.get(((VariableReference) inputValue).getName());
            return variableValue;
        }
        if (inputValue instanceof NullValue) {
            return null;
        }
        if (type instanceof GraphQLScalarType) {
            return literalToInternalValueForScalar(inputValue, (GraphQLScalarType) type, coercedVariables);
        }
        if (isNonNull(type)) {
            return literalToInternalValue(fieldVisibility, unwrapOne(type), inputValue, coercedVariables);
        }
        if (type instanceof GraphQLInputObjectType) {
            return literalToInternalValueForInputObject(fieldVisibility, (GraphQLInputObjectType) type, (ObjectValue) inputValue, coercedVariables);
        }
        if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).parseLiteral(inputValue);
        }
        if (isList(type)) {
            return literalToInternalValueForList(fieldVisibility, (GraphQLList) type, inputValue, coercedVariables);
        }
        return null;
    }

    /**
     * no validation
     */
    private Object literalToInternalValueForScalar(Value inputValue, GraphQLScalarType scalarType, Map<String, Object> variables) {
        // the CoercingParseLiteralException exception that could happen here has been validated earlier via ValidationUtil
        return scalarType.getCoercing().parseLiteral(inputValue, variables);
    }

    /**
     * no validation
     */
    private Object literalToInternalValueForList(GraphqlFieldVisibility fieldVisibility,
                                                 GraphQLList graphQLList,
                                                 Value value,
                                                 Map<String, Object> coercedVariables) {

        if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            List<Object> result = new ArrayList<>();
            for (Value singleValue : arrayValue.getValues()) {
                result.add(literalToInternalValue(fieldVisibility, graphQLList.getWrappedType(), singleValue, coercedVariables));
            }
            return result;
        } else {
            return Collections.singletonList(
                    literalToInternalValue(fieldVisibility,
                            graphQLList.getWrappedType(),
                            value,
                            coercedVariables));
        }
    }

    /**
     * no validation
     */
    private Object literalToInternalValueForInputObject(GraphqlFieldVisibility fieldVisibility,
                                                        GraphQLInputObjectType type,
                                                        ObjectValue inputValue,
                                                        Map<String, Object> coercedVariables) {
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
                Object coercedDefaultValue = defaultValueToInternalValue(fieldVisibility,
                        inputFieldDefinition.getInputFieldDefaultValue(),
                        fieldType);
                coercedValues.put(fieldName, coercedDefaultValue);
            } else if (isNonNull(fieldType) && (!hasValue || isNullValue(value))) {
                return assertShouldNeverHappen("Should have been validated before");
            } else if (hasValue) {
                if (isNullValue(value)) {
                    coercedValues.put(fieldName, value);
                } else if (fieldValue instanceof VariableReference) {
                    coercedValues.put(fieldName, value);
                } else {
                    value = literalToInternalValue(fieldVisibility, fieldType, fieldValue, coercedVariables);
                    coercedValues.put(fieldName, value);
                }
            }
        }
        return coercedValues;
    }

    private boolean isNullValue(Object value) {
        if (value == null) {
            return true;
        }
        if (!(value instanceof NormalizedInputValue)) {
            return false;
        }
        return ((NormalizedInputValue) value).getValue() == null;
    }

    private Map<String, ObjectField> mapObjectValueFieldsByName(ObjectValue inputValue) {
        Map<String, ObjectField> inputValueFieldsByName = new LinkedHashMap<>();
        for (ObjectField objectField : inputValue.getObjectFields()) {
            inputValueFieldsByName.put(objectField.getName(), objectField);
        }
        return inputValueFieldsByName;
    }

    private Object defaultValueToInternalValue(GraphqlFieldVisibility fieldVisibility,
                                               InputValueWithState defaultValue,
                                               GraphQLInputType type
    ) {
        if (defaultValue.isInternal()) {
            return defaultValue.getValue();
        }
        if (defaultValue.isLiteral()) {
            // default value literals can't reference variables, this is why the variables are empty
            return literalToInternalValue(fieldVisibility, type, (Value) defaultValue.getValue(), Collections.emptyMap());
        }
        if (defaultValue.isExternal()) {
            // performs validation too
            return externalValueToInternalValue(fieldVisibility, type, defaultValue.getValue());
        }
        return assertShouldNeverHappen();
    }


    /*
     * ======================LEGACY=======+TO BE REMOVED IN THE FUTURE ===============
     */

    /**
     * Legacy logic to convert an arbitrary java object to an Ast Literal.
     * Only provided here to preserve backwards compatibility.
     */
    @VisibleForTesting
    static Value<?> valueToLiteralLegacy(Object value, GraphQLType type) {
        assertTrue(!(value instanceof Value), () -> "Unexpected literal " + value);
        if (value == null) {
            return null;
        }

        if (isNonNull(type)) {
            return handleNonNullLegacy(value, (GraphQLNonNull) type);
        }

        // Convert JavaScript array to GraphQL list. If the GraphQLType is a list, but
        // the value is not an array, convert the value using the list's item type.
        if (isList(type)) {
            return handleListLegacy(value, (GraphQLList) type);
        }

        // Populate the fields of the input object by creating ASTs from each value
        // in the JavaScript object according to the fields in the input type.
        if (type instanceof GraphQLInputObjectType) {
            return handleInputObjectLegacy(value, (GraphQLInputObjectType) type);
        }

        if (!(type instanceof GraphQLScalarType || type instanceof GraphQLEnumType)) {
            throw new AssertException("Must provide Input Type, cannot use: " + type.getClass());
        }

        // Since value is an internally represented value, it must be serialized
        // to an externally represented value before converting into an AST.
        final Object serialized = serializeLegacy(type, value);
        if (isNullishLegacy(serialized)) {
            return null;
        }

        // Others serialize based on their corresponding JavaScript scalar types.
        if (serialized instanceof Boolean) {
            return BooleanValue.newBooleanValue().value((Boolean) serialized).build();
        }

        String stringValue = serialized.toString();
        // numbers can be Int or Float values.
        if (serialized instanceof Number) {
            return handleNumberLegacy(stringValue);
        }

        if (serialized instanceof String) {
            // Enum types use Enum literals.
            if (type instanceof GraphQLEnumType) {
                return EnumValue.newEnumValue().name(stringValue).build();
            }

            // ID types can use Int literals.
            if (type == Scalars.GraphQLID && stringValue.matches("^[0-9]+$")) {
                return IntValue.newIntValue().value(new BigInteger(stringValue)).build();
            }

            return StringValue.newStringValue().value(stringValue).build();
        }

        throw new AssertException("'Cannot convert value to AST: " + serialized);
    }

    private static Value<?> handleInputObjectLegacy(Object javaValue, GraphQLInputObjectType type) {
        List<GraphQLInputObjectField> fields = type.getFields();
        List<ObjectField> fieldNodes = new ArrayList<>();
        fields.forEach(field -> {
            String fieldName = field.getName();
            GraphQLInputType fieldType = field.getType();
            Object fieldValueObj = PropertyDataFetcherHelper.getPropertyValue(fieldName, javaValue, fieldType);
            Value<?> nodeValue = valueToLiteralLegacy(fieldValueObj, fieldType);
            if (nodeValue != null) {
                fieldNodes.add(newObjectField().name(fieldName).value(nodeValue).build());
            }
        });
        return ObjectValue.newObjectValue().objectFields(fieldNodes).build();
    }

    private static Value<?> handleNumberLegacy(String stringValue) {
        if (stringValue.matches("^[0-9]+$")) {
            return IntValue.newIntValue().value(new BigInteger(stringValue)).build();
        } else {
            return FloatValue.newFloatValue().value(new BigDecimal(stringValue)).build();
        }
    }

    @SuppressWarnings("rawtypes")
    private static Value<?> handleListLegacy(Object value, GraphQLList type) {
        GraphQLType itemType = type.getWrappedType();
        if (FpKit.isIterable(value)) {
            List<Value> valuesNodes = FpKit.toListOrSingletonList(value)
                    .stream()
                    .map(item -> valueToLiteralLegacy(item, itemType))
                    .collect(toList());
            return ArrayValue.newArrayValue().values(valuesNodes).build();
        }
        return valueToLiteralLegacy(value, itemType);
    }

    private static Value<?> handleNonNullLegacy(Object _value, GraphQLNonNull type) {
        GraphQLType wrappedType = type.getWrappedType();
        return valueToLiteralLegacy(_value, wrappedType);
    }

    private static Object serializeLegacy(GraphQLType type, Object value) {
        if (type instanceof GraphQLScalarType) {
            return ((GraphQLScalarType) type).getCoercing().serialize(value);
        } else {
            return ((GraphQLEnumType) type).serialize(value);
        }
    }

    private static boolean isNullishLegacy(Object serialized) {
        if (serialized instanceof Number) {
            return Double.isNaN(((Number) serialized).doubleValue());
        }
        return serialized == null;
    }

    /**
     * @return true if variable is absent from input, and if value is NOT a variable then false
     */
    private static boolean isVariableAbsent(Value value, Map<String, NormalizedInputValue> variables) {
        if (value instanceof VariableReference) {
            VariableReference varRef = (VariableReference) value;
            return !variables.containsKey(varRef.getName());
        }

        // Not variable, return false
        return false;
    }
}
