package graphql.execution;


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
import graphql.schema.Coercing;
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
import graphql.schema.PropertyDataFetcherHelper;
import graphql.schema.ValueState;
import graphql.schema.visibility.DefaultGraphqlFieldVisibility;
import graphql.schema.visibility.GraphqlFieldVisibility;
import graphql.util.FpKit;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.emptyMap;
import static graphql.collect.ImmutableKit.map;
import static graphql.language.NullValue.newNullValue;
import static graphql.language.ObjectField.newObjectField;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;

@SuppressWarnings("rawtypes")
@Internal
public class ValuesResolver {

    public enum ValueMode {
        COERCED,
        NORMALIZED
    }

    /**
     * This method coerces the "raw" variables values provided to the engine. The coerced values will be used to
     * provide arguments to {@link graphql.schema.DataFetchingEnvironment}
     * The coercing is ultimately done via {@link Coercing}.
     *
     * @param schema              the schema
     * @param variableDefinitions the variable definitions
     * @param rawVariables        the supplied variables
     *
     * @return coerced variable values as a map
     */
    public Map<String, Object> coerceVariableValues(GraphQLSchema schema,
                                                    List<VariableDefinition> variableDefinitions,
                                                    Map<String, Object> rawVariables) {

        return externalValueToInternalValueForVariables(schema, variableDefinitions, rawVariables, ValueMode.COERCED);
    }

    public Map<String, NormalizedInputValue> coerceNormalizedVariableValues(GraphQLSchema schema,
                                                                            List<VariableDefinition> variableDefinitions,
                                                                            Map<String, Object> rawVariables) {
        return (Map) externalValueToInternalValueForVariables(schema, variableDefinitions, rawVariables, ValueMode.NORMALIZED);
    }

    public Map<String, Object> getArgumentValues(List<GraphQLArgument> argumentTypes,
                                                 List<Argument> arguments,
                                                 Map<String, Object> coercedVariables) {
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(DEFAULT_FIELD_VISIBILITY).build();
        return getArgumentValuesImpl(codeRegistry, argumentTypes, arguments, coercedVariables, null, ValueMode.COERCED);
    }

    public Map<String, NormalizedInputValue> getNormalizedArgumentValues(List<GraphQLArgument> argumentTypes,
                                                                         List<Argument> arguments,
                                                                         Map<String, Object> coercedVariables,
                                                                         Map<String, NormalizedInputValue> normalizedVariables) {
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(DEFAULT_FIELD_VISIBILITY).build();
        return (Map) getArgumentValuesImpl(codeRegistry,
                argumentTypes,
                arguments,
                coercedVariables, normalizedVariables,
                ValueMode.NORMALIZED);
    }

    public Map<String, Object> getArgumentValues(GraphQLCodeRegistry codeRegistry,
                                                 List<GraphQLArgument> argumentTypes,
                                                 List<Argument> arguments,
                                                 Map<String, Object> coercedVariables) {
        return getArgumentValuesImpl(codeRegistry, argumentTypes, arguments, coercedVariables, null, ValueMode.COERCED);
    }

    public static Value<?> valueToLiteral(Object value, ValueState valueState, GraphQLType type) {
        return valueToLiteral(DEFAULT_FIELD_VISIBILITY, value, valueState, type);
    }

    /**
     * Takes a value which can be in different states (internal, literal, external value) and converts into Literal
     *
     * This assumes the value is valid!
     *
     * @return
     */
    public static Value<?> valueToLiteral(GraphqlFieldVisibility fieldVisibility, Object value, ValueState valueState, GraphQLType type) {
        if (valueState == ValueState.INTERNAL_VALUE) {
            return valueToLiteralLegacy(value, type);
        }
        if (valueState == ValueState.LITERAL) {
            return (Value<?>) value;
        }
        if (valueState == ValueState.EXTERNAL_VALUE) {
            return new ValuesResolver().externalValueToLiteral(fieldVisibility, value, (GraphQLInputType) type);
        }
        return assertShouldNeverHappen("unexpected value state " + valueState);
    }


    /**
     * includes validation
     */
    public static Object externalValueToInternalValue(GraphqlFieldVisibility fieldVisibility, Object externalValue, GraphQLInputType type) {
        return new ValuesResolver().externalValueToInternalValue(fieldVisibility, type, externalValue, ValueMode.COERCED);
    }

    public static Object valueToInternalValue(Object value, ValueState valueState, GraphQLType type) throws CoercingParseValueException, CoercingParseLiteralException {
        DefaultGraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;
        if (valueState == ValueState.INTERNAL_VALUE) {
            return value;
        }
        if (valueState == ValueState.LITERAL) {
            return new ValuesResolver().literalToInternalValue(fieldVisibility, type, (Value<?>) value, emptyMap(), null, ValueMode.COERCED, false);
        }
        if (valueState == ValueState.EXTERNAL_VALUE) {
            return new ValuesResolver().externalValueToInternalValue(fieldVisibility, (GraphQLInputType) type, value, ValueMode.COERCED);
        }
        return assertShouldNeverHappen("unexpected value state " + valueState);
    }

    private Value externalValueToLiteral(GraphqlFieldVisibility fieldVisibility, @Nullable Object value, GraphQLInputType type) {
        if (value == null) {
            return newNullValue().build();
        }
        if (GraphQLTypeUtil.isNonNull(type)) {
            return externalValueToLiteral(fieldVisibility, value, (GraphQLInputType) unwrapNonNull(type));
        }
        if (type instanceof GraphQLScalarType) {
            return externalValueToLiteralForScalar((GraphQLScalarType) type, value);
        } else if (type instanceof GraphQLEnumType) {
            return externalValueToLiteralForEnum((GraphQLEnumType) type, value);
        } else if (type instanceof GraphQLList) {
            return externalValueToLiteralForList(fieldVisibility, (GraphQLList) type, value);
        } else if (type instanceof GraphQLInputObjectType) {
            return externalValueToLiteralForObject(fieldVisibility, (GraphQLInputObjectType) type, value);
        } else {
            return assertShouldNeverHappen("unexpected type %s", type);
        }
    }

    private Value externalValueToLiteralForScalar(GraphQLScalarType scalarType, Object value) {
        return scalarType.getCoercing().valueToLiteral(value);

    }

    private Value externalValueToLiteralForEnum(GraphQLEnumType enumType, Object value) {
        return enumType.valueToLiteral(value);
    }

    private ArrayValue externalValueToLiteralForList(GraphqlFieldVisibility fieldVisibility, GraphQLList listType, Object value) {
        if (value instanceof Iterable) {
            List<Value> result = new ArrayList<>();
            for (Object val : (Iterable) value) {
                result.add(externalValueToLiteral(fieldVisibility, val, listType));
            }
            return ArrayValue.newArrayValue().values(result).build();
        } else {
            List<Value> result = Collections.singletonList(externalValueToLiteral(fieldVisibility, value, (GraphQLInputType) listType.getWrappedType()));
            return ArrayValue.newArrayValue().values(result).build();
        }
    }

    private ObjectValue externalValueToLiteralForObject(GraphqlFieldVisibility fieldVisibility,
                                                        GraphQLInputObjectType inputObjectType,
                                                        Object inputValue) {
        assertTrue(inputValue instanceof Map, () -> "Expect Map as input");
        Map<String, Object> inputMap = (Map<String, Object>) inputValue;
        List<GraphQLInputObjectField> fieldDefinitions = fieldVisibility.getFieldDefinitions(inputObjectType);


        List<ObjectField> objectFields = new ArrayList<>();
        for (GraphQLInputObjectField inputFieldDefinition : fieldDefinitions) {
            GraphQLInputType fieldType = inputFieldDefinition.getType();
            String fieldName = inputFieldDefinition.getName();
            boolean hasValue = inputMap.containsKey(fieldName);
            Object fieldValue = inputMap.getOrDefault(fieldName, null);
            if (!hasValue && inputFieldDefinition.hasSetDefaultValue()) {
                Value defaultValueLiteral = valueToLiteral(fieldVisibility, inputFieldDefinition.getInputFieldDefaultValue(), inputFieldDefinition.getDefaultValueState(), fieldType);
                objectFields.add(newObjectField().value(defaultValueLiteral).build());
            } else if (hasValue) {
                if (fieldValue == null) {
                    objectFields.add(newObjectField().value(newNullValue().build()).build());
                } else {
                    Value literal = externalValueToLiteral(fieldVisibility,
                            fieldValue,
                            fieldType);
                    objectFields.add(newObjectField().value(literal).build());
                }
            } else {
                // nullable type && hasValue == false && hasDefaultValue == false
                // meaning no value was provided for this field
            }
        }
        return ObjectValue.newObjectValue().objectFields(objectFields).build();
    }


    private Map<String, Object> externalValueToInternalValueForVariables(GraphQLSchema schema,
                                                                         List<VariableDefinition> variableDefinitions,
                                                                         Map<String, Object> rawVariables,
                                                                         ValueMode valueMode) {
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        Map<String, Object> coercedValues = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            String variableName = variableDefinition.getName();
//            Deque<Object> nameStack = new ArrayDeque<>();
            GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());
            assertTrue(variableType instanceof GraphQLInputType);
            // can be NullValue
            Value defaultValue = variableDefinition.getDefaultValue();
            boolean hasValue = rawVariables.containsKey(variableName);
            Object value = rawVariables.get(variableName);
            if (!hasValue && defaultValue != null) {
                Object coercedDefaultValue = literalToInternalValue(fieldVisibility, variableType, defaultValue, Collections.emptyMap(), null, valueMode, false);
                coercedValues.put(variableName, newValue(coercedDefaultValue, variableType, valueMode));
            } else if (isNonNull(variableType) && (!hasValue || value == null)) {
                throw new NonNullableValueCoercedAsNullException(variableDefinition, variableType);
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(variableName, newValue(null, variableType, valueMode));
                } else {
                    Object coercedValue = externalValueToInternalValue(fieldVisibility, variableType, value, valueMode);
                    coercedValues.put(variableName, newValue(coercedValue, variableType, valueMode));
                }
            } else {
                // hasValue = false && no defaultValue for a nullable type
                // meaning no value was provided for variableName
            }
        }

        return coercedValues;
    }


    private Map<String, Object> getArgumentValuesImpl(GraphQLCodeRegistry codeRegistry,
                                                      List<GraphQLArgument> argumentTypes,
                                                      List<Argument> arguments,
                                                      Map<String, Object> coercedVariables,
                                                      @Nullable Map<String, NormalizedInputValue> normalizedVariables,
                                                      ValueMode valueMode) {
        if (argumentTypes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> variables = getVariables(coercedVariables, normalizedVariables, valueMode);

        Map<String, Object> coercedValues = new LinkedHashMap<>();
        Map<String, Argument> argumentMap = argumentMap(arguments);
        for (GraphQLArgument argumentDefinition : argumentTypes) {
            GraphQLInputType argumentType = argumentDefinition.getType();
            String argumentName = argumentDefinition.getName();
            Argument argument = argumentMap.get(argumentName);
            Object defaultValue = argumentDefinition.getArgumentDefaultValue();
            boolean hasValue = argument != null;
            Object value;
            Value argumentValue = argument != null ? argument.getValue() : null;
            if (argumentValue instanceof VariableReference) {
                String variableName = ((VariableReference) argumentValue).getName();
                hasValue = variables.containsKey(variableName);
                value = variables.get(variableName);
            } else {
                value = newValue(argumentValue, argumentType, valueMode);
            }
            if (!hasValue && argumentDefinition.hasSetDefaultValue()) {
                Object coercedDefaultValue = defaultValueToInternalValue(
                        codeRegistry.getFieldVisibility(),
                        defaultValue,
                        argumentDefinition.getDefaultValueState(),
                        argumentType,
                        valueMode);
                coercedValues.put(argumentName, newValue(coercedDefaultValue, argumentType, valueMode));
            } else if (isNonNull(argumentType) && (!hasValue || isNullValue(value))) {
                throw new RuntimeException();
            } else if (hasValue) {
                if (isNullValue(value)) {
                    coercedValues.put(argumentName, value);
                } else if (argumentValue instanceof VariableReference) {
                    coercedValues.put(argumentName, value);
                } else {
                    value = literalToInternalValue(codeRegistry.getFieldVisibility(), argumentType, argument.getValue(), coercedVariables, normalizedVariables, valueMode, false);
                    coercedValues.put(argumentName, newValue(value, argumentType, valueMode));
                }
            } else {
                // nullable type && hasValue == false && hasDefaultValue == false
                // meaning no value was provided for argumentName
            }

        }
        return coercedValues;

    }

    private Object newValue(Object value, GraphQLType type, ValueMode valueMode) {
        if (valueMode == ValueMode.COERCED) {
            return value;
        } else if (valueMode == ValueMode.NORMALIZED) {
            return new NormalizedInputValue(simplePrint(type), value);
        } else {
            return assertShouldNeverHappen();
        }
    }

    private Map<String, Object> getVariables(Map<String, Object> coercedVariables,
                                             @Nullable Map<String, NormalizedInputValue> normalizedVariables,
                                             ValueMode valueMode) {
        if (valueMode == ValueMode.COERCED) {
            return coercedVariables;
        } else if (valueMode == ValueMode.NORMALIZED) {
            return (Map) normalizedVariables;
        } else {
            return assertShouldNeverHappen();
        }
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
                                                Object value,
                                                ValueMode valueMode) throws NonNullableValueCoercedAsNullException, CoercingParseValueException {
//        nameStack.addLast(inputName);
        try {
            if (isNonNull(graphQLType)) {
                Object returnValue =
                        externalValueToInternalValue(fieldVisibility, unwrapOne(graphQLType), value, valueMode);
                if (returnValue == null) {
                    throw new NonNullableValueCoercedAsNullException("", emptyList(), graphQLType);
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
                return externalValueToInternalValueForList(fieldVisibility, (GraphQLList) graphQLType, value, valueMode);
            } else if (graphQLType instanceof GraphQLInputObjectType) {
                if (value instanceof Map) {
                    return externalValueToInternalValueForObject(fieldVisibility, (GraphQLInputObjectType) graphQLType, (Map<String, Object>) value, valueMode);
                } else {
                    throw CoercingParseValueException.newCoercingParseValueException()
                            .message("Expected type 'Map' but was '" + value.getClass().getSimpleName() +
                                    "'. Variables for input objects must be an instance of type 'Map'.")
//                            .path(Arrays.asList(nameStack.toArray()))
                            .build();
                }
            } else {
                return assertShouldNeverHappen("unhandled type %s", graphQLType);
            }
        } catch (CoercingParseValueException e) {
            if (e.getLocations() != null) {
                throw e;
            }
            CoercingParseValueException.Builder builder = CoercingParseValueException.newCoercingParseValueException();
            throw builder
                    .message("invalid value : " + e.getMessage())
                    .extensions(e.getExtensions())
                    .cause(e.getCause())
//                    .path(Arrays.asList(nameStack.toArray()))
                    .build();
        } finally {
//            nameStack.removeLast();
        }

    }

    /**
     * performs validation
     */
    private Object externalValueToInternalValueForObject(GraphqlFieldVisibility fieldVisibility,
                                                         GraphQLInputObjectType inputObjectType,
                                                         Map<String, Object> inputMap,
                                                         ValueMode valueMode) throws NonNullableValueCoercedAsNullException, CoercingParseValueException {
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
            Object defaultValue = inputFieldDefinition.getInputFieldDefaultValue();
            boolean hasValue = inputMap.containsKey(fieldName);
            Object value;
            Object fieldValue = inputMap.getOrDefault(fieldName, null);
            value = fieldValue;
            if (!hasValue && inputFieldDefinition.hasSetDefaultValue()) {
                Object coercedDefaultValue = defaultValueToInternalValue(fieldVisibility,
                        defaultValue,
                        inputFieldDefinition.getDefaultValueState(),
                        fieldType,
                        valueMode);
                coercedValues.put(fieldName, newValue(coercedDefaultValue, fieldType, valueMode));
            } else if (isNonNull(fieldType) && (!hasValue || value == null)) {
                throw new NonNullableValueCoercedAsNullException(fieldName, emptyList(), fieldType);
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(fieldName, newValue(null, fieldType, valueMode));
                } else {
                    value = externalValueToInternalValue(fieldVisibility,
                            fieldType,
                            value,
                            valueMode);
                    coercedValues.put(fieldName, newValue(value, fieldType, valueMode));
                }
            } else {
                // nullable type && hasValue == false && hasDefaultValue == false
                // meaning no value was provided for this field
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
                                                     Object value,
                                                     ValueMode valueMode) throws CoercingParseValueException, NonNullableValueCoercedAsNullException {
        if (value instanceof Iterable) {
            List<Object> result = new ArrayList<>();
            for (Object val : (Iterable) value) {
                result.add(externalValueToInternalValue(fieldVisibility, graphQLList.getWrappedType(), val, valueMode));
            }
            return result;
        } else {
            return Collections.singletonList(externalValueToInternalValue(fieldVisibility, graphQLList.getWrappedType(), value, valueMode));
        }
    }

    /**
     * No validation (it was checked before via ArgumentsOfCorrectType and VariableDefaultValuesOfCorrectType)
     */
    public Object literalToInternalValue(GraphqlFieldVisibility fieldVisibility,
                                         GraphQLType type,
                                         Value inputValue,
                                         Map<String, Object> coercedVariables,
                                         @Nullable Map<String, NormalizedInputValue> normalizedVariables,
                                         ValueMode valueMode,
                                         boolean unwrappingList) {

        if (inputValue instanceof VariableReference) {
            Map<String, Object> variables = getVariables(coercedVariables, normalizedVariables, valueMode);
            Object variableValue = assertNotNull(variables.get(((VariableReference) inputValue).getName()));
            // this is a special case when we have a normalized variable inside a List:
            // we just need to the value here, because the whole list itself is already a NormalizedInputValue
            if (unwrappingList && variableValue instanceof NormalizedInputValue) {
                return ((NormalizedInputValue) variableValue).getValue();
            } else {
                return variableValue;
            }
        }
        if (inputValue instanceof NullValue) {
            return null;
        }
        if (type instanceof GraphQLScalarType) {
            return literalToInternalValueForScalar(inputValue, (GraphQLScalarType) type, coercedVariables);
        }
        if (isNonNull(type)) {
            return literalToInternalValue(fieldVisibility, unwrapOne(type), inputValue, coercedVariables, normalizedVariables, valueMode, unwrappingList);
        }
        if (type instanceof GraphQLInputObjectType) {
            return literalToInternalValueForInputObject(fieldVisibility, (GraphQLInputObjectType) type, (ObjectValue) inputValue, coercedVariables, normalizedVariables, valueMode);
        }
        if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).parseLiteral(inputValue);
        }
        if (isList(type)) {
            return literalToInternalValueForList(fieldVisibility, (GraphQLList) type, inputValue, coercedVariables, normalizedVariables, valueMode);
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
                                                 Map<String, Object> coercedVariables,
                                                 @Nullable Map<String, NormalizedInputValue> normalizedVariables,
                                                 ValueMode valueMode) {
        if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            List<Object> result = new ArrayList<>();
            for (Value singleValue : arrayValue.getValues()) {
                result.add(literalToInternalValue(fieldVisibility, graphQLList.getWrappedType(), singleValue, coercedVariables, normalizedVariables, valueMode, true));
            }
            return result;
        } else {
            return Collections.singletonList(literalToInternalValue(fieldVisibility,
                    graphQLList.getWrappedType(),
                    value,
                    coercedVariables,
                    normalizedVariables,
                    valueMode, true));
        }
    }

    /**
     * no validation
     */
    private Object literalToInternalValueForInputObject(GraphqlFieldVisibility fieldVisibility,
                                                        GraphQLInputObjectType type,
                                                        ObjectValue inputValue,
                                                        Map<String, Object> coercedVariables,
                                                        @Nullable Map<String, NormalizedInputValue> normalizedVariables,
                                                        ValueMode valueMode) {
        Map<String, Object> coercedValues = new LinkedHashMap<>();

        Map<String, ObjectField> inputFieldsByName = mapObjectValueFieldsByName(inputValue);

        Map<String, Object> variables = getVariables(coercedVariables, normalizedVariables, valueMode);

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
                hasValue = variables.containsKey(variableName);
                value = variables.get(variableName);
            } else {
                value = newValue(fieldValue, fieldType, valueMode);
            }
            if (!hasValue && inputFieldDefinition.hasSetDefaultValue()) {
                Object coercedDefaultValue = defaultValueToInternalValue(fieldVisibility,
                        inputFieldDefinition.getInputFieldDefaultValue(),
                        inputFieldDefinition.getDefaultValueState(),
                        fieldType,
                        valueMode);
                coercedValues.put(fieldName, newValue(coercedDefaultValue, fieldType, valueMode));
            } else if (isNonNull(fieldType) && (!hasValue || isNullValue(value))) {
                return assertShouldNeverHappen("Should have been validated before");
            } else if (hasValue) {
                if (isNullValue(value)) {
                    coercedValues.put(fieldName, value);
                } else if (fieldValue instanceof VariableReference) {
                    coercedValues.put(fieldName, value);
                } else {
                    value = literalToInternalValue(fieldVisibility, fieldType, fieldValue, coercedVariables, normalizedVariables, valueMode, true);
                    coercedValues.put(fieldName, newValue(value, fieldType, valueMode));
                }
            } else {
                // nullable type && hasValue == false && hasDefaultValue == false
                // meaning no value was provided for this field
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
                                               Object defaultValue,
                                               ValueState valueState,
                                               GraphQLInputType type,
                                               ValueMode valueMode) {
        if (valueState == ValueState.INTERNAL_VALUE) {
            return defaultValue;
        }
        if (valueState == ValueState.LITERAL) {
            // default value literals can't reference variables
            return literalToInternalValue(fieldVisibility, type, (Value) defaultValue, Collections.emptyMap(), null, valueMode, false);
        }
        if (valueState == ValueState.EXTERNAL_VALUE) {
            return externalValueToInternalValue(fieldVisibility, type, defaultValue, valueMode);
        }
        return assertShouldNeverHappen();
    }


    /**
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
    private static Value<?> handleListLegacy(Object _value, GraphQLList type) {
        GraphQLType itemType = type.getWrappedType();
        boolean isIterable = _value instanceof Iterable;
        if (isIterable || (_value != null && _value.getClass().isArray())) {
            Iterable<?> iterable = isIterable ? (Iterable<?>) _value : FpKit.toCollection(_value);
            List<Value> valuesNodes = new ArrayList<>();
            for (Object item : iterable) {
                Value<?> itemNode = valueToLiteralLegacy(item, itemType);
                if (itemNode != null) {
                    valuesNodes.add(itemNode);
                }
            }
            return ArrayValue.newArrayValue().values(valuesNodes).build();
        }
        return valueToLiteralLegacy(_value, itemType);
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

}
