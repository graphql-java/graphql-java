package graphql.execution;


import graphql.Assert;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.collect.ImmutableKit;
import graphql.execution.values.InputInterceptor;
import graphql.i18n.I18n;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.normalized.NormalizedInputValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.InputValueWithState;
import graphql.schema.visibility.GraphqlFieldVisibility;
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
import static graphql.execution.ValuesResolver.ValueMode.NORMALIZED;
import static graphql.execution.ValuesResolverConversion.externalValueToInternalValueImpl;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;

@SuppressWarnings("rawtypes")
@Internal
public class ValuesResolver {

    private ValuesResolver() {
    }

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
     * @param graphqlContext      the GraphqlContext to use
     * @param locale              the Locale to use
     *
     * @return coerced variable values as a map
     */
    public static CoercedVariables coerceVariableValues(GraphQLSchema schema,
                                                        List<VariableDefinition> variableDefinitions,
                                                        RawVariables rawVariables,
                                                        GraphQLContext graphqlContext,
                                                        Locale locale) throws CoercingParseValueException, NonNullableValueCoercedAsNullException {

        InputInterceptor inputInterceptor = graphqlContext.get(InputInterceptor.class);
        return ValuesResolverConversion.externalValueToInternalValueForVariables(
                inputInterceptor,
                schema,
                variableDefinitions,
                rawVariables,
                graphqlContext,
                locale);
    }


    /**
     * Normalized variables values are Literals with type information. No validation here!
     *
     * @param schema              the schema to use
     * @param variableDefinitions the list of variable definitions
     * @param rawVariables        the raw variables
     * @param graphqlContext      the GraphqlContext to use
     * @param locale              the Locale to use
     *
     * @return a map of the normalised values
     */
    public static Map<String, NormalizedInputValue> getNormalizedVariableValues(
            GraphQLSchema schema,
            List<VariableDefinition> variableDefinitions,
            RawVariables rawVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
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
                    Object literal = ValuesResolverConversion.externalValueToLiteral(fieldVisibility, value, (GraphQLInputType) variableType, NORMALIZED, graphqlContext, locale);
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
     * @param graphqlContext   the GraphqlContext to use
     * @param locale           the Locale to use
     *
     * @return a map of named argument values
     */
    public static Map<String, Object> getArgumentValues(
            List<GraphQLArgument> argumentTypes,
            List<Argument> arguments,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        InputInterceptor inputInterceptor = graphqlContext.get(InputInterceptor.class);
        return getArgumentValuesImpl(inputInterceptor, DEFAULT_FIELD_VISIBILITY, argumentTypes, arguments, coercedVariables, graphqlContext, locale);
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
    public static Map<String, NormalizedInputValue> getNormalizedArgumentValues(
            List<GraphQLArgument> argumentTypes,
            List<Argument> arguments,
            Map<String, NormalizedInputValue> normalizedVariables
    ) {
        if (argumentTypes.isEmpty()) {
            return ImmutableKit.emptyMap();
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

    public static Map<String, Object> getArgumentValues(
            GraphQLCodeRegistry codeRegistry,
            List<GraphQLArgument> argumentTypes,
            List<Argument> arguments,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        InputInterceptor inputInterceptor = graphqlContext.get(InputInterceptor.class);
        return getArgumentValuesImpl(inputInterceptor, codeRegistry.getFieldVisibility(), argumentTypes, arguments, coercedVariables, graphqlContext, locale);
    }

    /**
     * Takes a value which can be in different states (internal, literal, external value) and converts into Literal
     * <p>
     * This assumes the value is valid!
     *
     * @param fieldVisibility     the field visibility to use
     * @param inputValueWithState the input value
     * @param type                the type of input value
     * @param graphqlContext      the GraphqlContext to use
     * @param locale              the Locale to use
     *
     * @return a value converted to a literal
     */
    public static Value<?> valueToLiteral(
            @NotNull GraphqlFieldVisibility fieldVisibility,
            @NotNull InputValueWithState inputValueWithState,
            @NotNull GraphQLType type,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        return (Value<?>) ValuesResolverConversion.valueToLiteralImpl(
                fieldVisibility,
                inputValueWithState,
                type,
                ValueMode.LITERAL,
                graphqlContext,
                locale);
    }

    public static Value<?> valueToLiteral(
            @NotNull InputValueWithState inputValueWithState,
            @NotNull GraphQLType type,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        return (Value<?>) ValuesResolverConversion.valueToLiteralImpl(
                DEFAULT_FIELD_VISIBILITY,
                inputValueWithState,
                type,
                ValueMode.LITERAL,
                graphqlContext,
                locale);
    }

    public static Object valueToInternalValue(
            InputValueWithState inputValueWithState,
            GraphQLInputType inputType,
            GraphQLContext graphqlContext,
            Locale locale
    ) throws CoercingParseValueException, CoercingParseLiteralException {
        InputInterceptor inputInterceptor = graphqlContext.get(InputInterceptor.class);
        return ValuesResolverConversion.valueToInternalValueImpl(
                inputInterceptor,
                inputValueWithState,
                inputType,
                graphqlContext,
                locale);
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
    public static Object externalValueToInternalValue(
            GraphqlFieldVisibility fieldVisibility,
            Object externalValue,
            GraphQLInputType type,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
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
    @SuppressWarnings("unchecked")
    public static <T> T getInputValueImpl(
            GraphQLInputType inputType,
            InputValueWithState inputValue,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        if (inputValue.isNotSet()) {
            return null;
        }
        return (T) valueToInternalValue(
                inputValue,
                inputType,
                graphqlContext,
                locale);
    }


    private static Map<String, Object> getArgumentValuesImpl(
            InputInterceptor inputInterceptor,
            GraphqlFieldVisibility fieldVisibility,
            List<GraphQLArgument> argumentTypes,
            List<Argument> arguments,
            CoercedVariables coercedVariables,
            GraphQLContext graphqlContext,
            Locale locale
    ) {
        if (argumentTypes.isEmpty()) {
            return ImmutableKit.emptyMap();
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
                Object coercedDefaultValue = ValuesResolverConversion.defaultValueToInternalValue(
                        inputInterceptor,
                        fieldVisibility,
                        defaultValue,
                        argumentType,
                        graphqlContext,
                        locale);
                coercedValues.put(argumentName, coercedDefaultValue);
            } else if (isNonNull(argumentType) && (!hasValue || ValuesResolverConversion.isNullValue(value))) {
                throw new NonNullableValueCoercedAsNullException(argumentDefinition);
            } else if (hasValue) {
                if (ValuesResolverConversion.isNullValue(value)) {
                    coercedValues.put(argumentName, value);
                } else if (argumentValue instanceof VariableReference) {
                    coercedValues.put(argumentName, value);
                } else {
                    value = ValuesResolverConversion.literalToInternalValue(inputInterceptor,
                            fieldVisibility,
                            argumentType,
                            argument.getValue(),
                            coercedVariables,
                            graphqlContext,
                            locale);
                    coercedValues.put(argumentName, value);
                }

                ValuesResolverOneOfValidation.validateOneOfInputTypes(argumentType, value, argumentValue, argumentName, locale);

            }
        }


        return coercedValues;
    }

    private static Map<String, Argument> argumentMap(List<Argument> arguments) {
        Map<String, Argument> result = new LinkedHashMap<>(arguments.size());
        for (Argument argument : arguments) {
            result.put(argument.getName(), argument);
        }
        return result;
    }


    public static Object literalToNormalizedValue(GraphqlFieldVisibility fieldVisibility,
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
            return literalToNormalizedValue(
                    fieldVisibility,
                    unwrapOne(type),
                    inputValue,
                    normalizedVariables);
        }
        if (type instanceof GraphQLInputObjectType) {
            return literalToNormalizedValueForInputObject(
                    fieldVisibility,
                    (GraphQLInputObjectType) type,
                    (ObjectValue) inputValue,
                    normalizedVariables);
        }
        if (type instanceof GraphQLEnumType) {
            return inputValue;
        }
        if (isList(type)) {
            return literalToNormalizedValueForList(
                    fieldVisibility,
                    (GraphQLList) type,
                    inputValue,
                    normalizedVariables);
        }
        return null;
    }

    private static Object literalToNormalizedValueForInputObject(GraphqlFieldVisibility fieldVisibility,
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
            Object fieldValue = literalToNormalizedValue(
                    fieldVisibility,
                    fieldType,
                    field.getValue(),
                    normalizedVariables);
            result.put(field.getName(), new NormalizedInputValue(simplePrint(fieldType), fieldValue));
        }
        return result;
    }

    private static List<Object> literalToNormalizedValueForList(GraphqlFieldVisibility fieldVisibility,
                                                                GraphQLList type,
                                                                Value value,
                                                                Map<String, NormalizedInputValue> normalizedVariables) {
        if (value instanceof ArrayValue) {
            List<Object> result = new ArrayList<>();
            for (Value valueInArray : ((ArrayValue) value).getValues()) {
                Object normalisedValue = literalToNormalizedValue(
                        fieldVisibility,
                        type.getWrappedType(),
                        valueInArray,
                        normalizedVariables);
                result.add(normalisedValue);
            }
            return result;
        } else {
            return Collections.singletonList(literalToNormalizedValue(
                    fieldVisibility,
                    type.getWrappedType(),
                    value,
                    normalizedVariables));
        }
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
