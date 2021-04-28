package graphql.execution;


import graphql.Assert;
import graphql.Internal;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.collect.ImmutableKit.map;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;

@SuppressWarnings("rawtypes")
@Internal
public class ValuesResolver {

    /**
     * This method coerces the "raw" variables values provided to the engine. The coerced values will be used to
     * provide arguments to {@link graphql.schema.DataFetchingEnvironment}
     * The coercing is ultimately done via {@link Coercing}.
     *
     * @param schema              the schema
     * @param variableDefinitions the variable definitions
     * @param variableValues      the supplied variables
     *
     * @return coerced variable values as a map
     */
    public Map<String, Object> coerceVariableValues(GraphQLSchema schema, List<VariableDefinition> variableDefinitions, Map<String, Object> variableValues) {
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        Map<String, Object> coercedValues = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            String variableName = variableDefinition.getName();
            Deque<Object> nameStack = new ArrayDeque<>();
            GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());
            Assert.assertTrue(variableType instanceof GraphQLInputType);
            // can be NullValue
            Value defaultValue = variableDefinition.getDefaultValue();
            boolean hasValue = variableValues.containsKey(variableName);
            Object value = variableValues.get(variableName);
            if (!hasValue && defaultValue != null) {
                Object coercedDefaultValue = coerceValueAst(fieldVisibility, variableType, defaultValue, null);
                coercedValues.put(variableName, coercedDefaultValue);
            } else if (isNonNull(variableType) && (!hasValue || value == null)) {
                throw new NonNullableValueCoercedAsNullException(variableDefinition, variableType);
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(variableName, null);
                } else {
                    Object coercedValue = coerceValue(fieldVisibility, variableDefinition, variableDefinition.getName(), variableType, value, nameStack);
                    coercedValues.put(variableName, coercedValue);
                }
            } else {
                // hasValue = false && no defaultValue for a nullable type
                // meaning no value was provided for variableName
            }
        }

        return coercedValues;
    }

    public Map<String, Object> getArgumentValues(List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(DEFAULT_FIELD_VISIBILITY).build();
        return getArgumentValuesImpl(codeRegistry, argumentTypes, arguments, variables);
    }

    public Map<String, Object> getArgumentValues(GraphQLCodeRegistry codeRegistry, List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        return getArgumentValuesImpl(codeRegistry, argumentTypes, arguments, variables);
    }

    private Map<String, Object> getArgumentValuesImpl(GraphQLCodeRegistry codeRegistry, List<GraphQLArgument> argumentTypes, List<Argument> arguments,
                                                      Map<String, Object> coercedVariableValues) {
        if (argumentTypes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> coercedValues = new LinkedHashMap<>();
        Map<String, Argument> argumentMap = argumentMap(arguments);
        for (GraphQLArgument argumentDefinition : argumentTypes) {
            GraphQLInputType argumentType = argumentDefinition.getType();
            String argumentName = argumentDefinition.getName();
            Argument argument = argumentMap.get(argumentName);
            Object defaultValue = argumentDefinition.getDefaultValue();
            boolean hasValue = argument != null;
            Object value;
            Value argumentValue = argument != null ? argument.getValue() : null;
            if (argumentValue instanceof VariableReference) {
                String variableName = ((VariableReference) argumentValue).getName();
                hasValue = coercedVariableValues.containsKey(variableName);
                value = coercedVariableValues.get(variableName);
            } else {
                value = argumentValue;
            }
            if (!hasValue && argumentDefinition.hasSetDefaultValue()) {
                //TODO: default value needs to be coerced
                coercedValues.put(argumentName, defaultValue);
            } else if (isNonNull(argumentType) && (!hasValue || value == null)) {
                throw new RuntimeException();
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(argumentName, null);
                } else if (argumentValue instanceof VariableReference) {
                    coercedValues.put(argumentName, value);
                } else {
                    value = coerceValueAst(codeRegistry.getFieldVisibility(), argumentType, argument.getValue(), coercedVariableValues);
                    coercedValues.put(argumentName, value);
                }
            } else {
                // nullable type && hasValue == false && hasDefaultValue == false
                // meaning no value was provided for argumentName
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


    @SuppressWarnings("unchecked")
    private Object coerceValue(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, String inputName, GraphQLType graphQLType, Object value, Deque<Object> nameStack) {
        nameStack.addLast(inputName);
        try {
            if (isNonNull(graphQLType)) {
                Object returnValue =
                        coerceValue(fieldVisibility, variableDefinition, inputName, unwrapOne(graphQLType), value, nameStack);
                if (returnValue == null) {
                    throw new NonNullableValueCoercedAsNullException(variableDefinition, inputName, Arrays.asList(nameStack.toArray()), graphQLType);
                }
                return returnValue;
            }

            if (value == null) {
                return null;
            }

            if (graphQLType instanceof GraphQLScalarType) {
                return coerceValueForScalar((GraphQLScalarType) graphQLType, value);
            } else if (graphQLType instanceof GraphQLEnumType) {
                return coerceValueForEnum((GraphQLEnumType) graphQLType, value);
            } else if (graphQLType instanceof GraphQLList) {
                return coerceValueForList(fieldVisibility, variableDefinition, inputName, (GraphQLList) graphQLType, value, nameStack);
            } else if (graphQLType instanceof GraphQLInputObjectType) {
                if (value instanceof Map) {
                    return coerceValueForInputObjectType(fieldVisibility, variableDefinition, (GraphQLInputObjectType) graphQLType, (Map<String, Object>) value, nameStack);
                } else {
                    throw CoercingParseValueException.newCoercingParseValueException()
                            .message("Expected type 'Map' but was '" + value.getClass().getSimpleName() +
                                    "'. Variables for input objects must be an instance of type 'Map'.")
                            .path(Arrays.asList(nameStack.toArray()))
                            .build();
                }
            } else {
                return assertShouldNeverHappen("unhandled type %s", graphQLType);
            }
        } catch (CoercingParseValueException e) {
            if (e.getLocations() != null) {
                throw e;
            }
            throw CoercingParseValueException.newCoercingParseValueException()
                    .message("Variable '" + inputName + "' has an invalid value : " + e.getMessage())
                    .extensions(e.getExtensions())
                    .cause(e.getCause())
                    .sourceLocation(variableDefinition.getSourceLocation())
                    .path(Arrays.asList(nameStack.toArray()))
                    .build();
        } finally {
            nameStack.removeLast();
        }

    }

    private Object coerceValueForInputObjectType(GraphqlFieldVisibility fieldVisibility,
                                                 VariableDefinition variableDefinition,
                                                 GraphQLInputObjectType inputObjectType,
                                                 Map<String, Object> inputMap,
                                                 Deque<Object> nameStack) {
//        Map<String, Object> result = new LinkedHashMap<>();
        List<GraphQLInputObjectField> fields = fieldVisibility.getFieldDefinitions(inputObjectType);
        List<String> fieldNames = map(fields, GraphQLInputObjectField::getName);
        for (String inputFieldName : inputMap.keySet()) {
            if (!fieldNames.contains(inputFieldName)) {
                throw new InputMapDefinesTooManyFieldsException(inputObjectType, inputFieldName);
            }
        }

        Map<String, Object> coercedValues = new LinkedHashMap<>();

        List<GraphQLInputObjectField> inputFieldTypes = fieldVisibility.getFieldDefinitions(inputObjectType);
        for (GraphQLInputObjectField inputFieldDefinition : inputFieldTypes) {

            GraphQLInputType fieldType = inputFieldDefinition.getType();
            String fieldName = inputFieldDefinition.getName();
            Object defaultValue = inputFieldDefinition.getDefaultValue();
            boolean hasValue = inputMap.containsKey(fieldName);
            Object value;
            Object fieldValue = inputMap.getOrDefault(fieldName, null);
            value = fieldValue;
            if (!hasValue && inputFieldDefinition.hasSetDefaultValue()) {
                //TODO: default value should be coerced
                coercedValues.put(fieldName, defaultValue);
            } else if (isNonNull(fieldType) && (!hasValue || value == null)) {
                nameStack.addLast(fieldName);
                throw new NonNullableValueCoercedAsNullException(variableDefinition, fieldName, Arrays.asList(nameStack.toArray()), fieldType);
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(fieldName, null);
                } else if (fieldValue instanceof VariableReference) {
                    coercedValues.put(fieldName, value);
                } else {
                    value = coerceValue(fieldVisibility,
                            variableDefinition,
                            inputFieldDefinition.getName(),
                            fieldType,
                            value,
                            nameStack);
                    coercedValues.put(fieldName, value);
                }
            } else {
                // nullable type && hasValue == false && hasDefaultValue == false
                // meaning no value was provided for this field
            }
        }
        return coercedValues;


//        for (GraphQLInputObjectField inputField : fields) {
//            if (inputMap.containsKey(inputField.getName()) || alwaysHasValue(inputField)) {
//                // getOrDefault will return a null value if its present in the map as null
//                // defaulting only applies if the key is missing - we want this
//                Object inputValue = inputMap.getOrDefault(inputField.getName(), inputField.getDefaultValue());
//                Object coerceValue = coerceValue(fieldVisibility, variableDefinition,
//                        inputField.getName(),
//                        inputField.getType(),
//                        inputValue,
//                        nameStack);
//                result.put(inputField.getName(), coerceValue == null ? inputField.getDefaultValue() : coerceValue);
//            }
//        }
//        return result;
    }

    private boolean alwaysHasValue(GraphQLInputObjectField inputField) {
        return inputField.getDefaultValue() != null
                || isNonNull(inputField.getType());
    }

    private Object coerceValueForScalar(GraphQLScalarType graphQLScalarType, Object value) {
        return graphQLScalarType.getCoercing().parseValue(value);
    }

    private Object coerceValueForEnum(GraphQLEnumType graphQLEnumType, Object value) {
        return graphQLEnumType.parseValue(value);
    }

    private List coerceValueForList(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, String inputName, GraphQLList graphQLList, Object value, Deque<Object> nameStack) {
        if (value instanceof Iterable) {
            List<Object> result = new ArrayList<>();
            for (Object val : (Iterable) value) {
                result.add(coerceValue(fieldVisibility, variableDefinition, inputName, graphQLList.getWrappedType(), val, nameStack));
            }
            return result;
        } else {
            return Collections.singletonList(coerceValue(fieldVisibility, variableDefinition, inputName, graphQLList.getWrappedType(), value, nameStack));
        }
    }

    private Object coerceValueAst(GraphqlFieldVisibility fieldVisibility, GraphQLType type, Value inputValue, Map<String, Object> variables) {
        if (inputValue instanceof VariableReference) {
            return variables.get(((VariableReference) inputValue).getName());
        }
        if (inputValue instanceof NullValue) {
            return null;
        }
        if (type instanceof GraphQLScalarType) {
            return parseLiteral(inputValue, ((GraphQLScalarType) type).getCoercing(), variables);
        }
        if (isNonNull(type)) {
            return coerceValueAst(fieldVisibility, unwrapOne(type), inputValue, variables);
        }
        if (type instanceof GraphQLInputObjectType) {
            return coerceValueAstForInputObject(fieldVisibility, (GraphQLInputObjectType) type, (ObjectValue) inputValue, variables);
        }
        if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).parseLiteral(inputValue);
        }
        if (isList(type)) {
            return coerceValueAstForList(fieldVisibility, (GraphQLList) type, inputValue, variables);
        }
        return null;
    }

    private Object parseLiteral(Value inputValue, Coercing coercing, Map<String, Object> variables) {
        // the CoercingParseLiteralException exception that could happen here has been validated earlier via ValidationUtil
        return coercing.parseLiteral(inputValue, variables);
    }

    private Object coerceValueAstForList(GraphqlFieldVisibility fieldVisibility, GraphQLList graphQLList, Value value, Map<String, Object> variables) {
        if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            List<Object> result = new ArrayList<>();
            for (Value singleValue : arrayValue.getValues()) {
                result.add(coerceValueAst(fieldVisibility, graphQLList.getWrappedType(), singleValue, variables));
            }
            return result;
        } else {
            return Collections.singletonList(coerceValueAst(fieldVisibility, graphQLList.getWrappedType(), value, variables));
        }
    }

    private Object coerceValueAstForInputObject(GraphqlFieldVisibility fieldVisibility,
                                                GraphQLInputObjectType type,
                                                ObjectValue inputValue,
                                                Map<String, Object> coercedVariableValues) {
        Map<String, Object> coercedValues = new LinkedHashMap<>();

        Map<String, ObjectField> inputFieldsByName = mapObjectValueFieldsByName(inputValue);

        List<GraphQLInputObjectField> inputFieldTypes = fieldVisibility.getFieldDefinitions(type);
        for (GraphQLInputObjectField inputFieldType : inputFieldTypes) {

            GraphQLInputType fieldType = inputFieldType.getType();
            String fieldName = inputFieldType.getName();
            ObjectField field = inputFieldsByName.get(fieldName);
            Object defaultValue = inputFieldType.getDefaultValue();
            boolean hasValue = field != null;
            Object value;
            Value fieldValue = field != null ? field.getValue() : null;
            if (fieldValue instanceof VariableReference) {
                String variableName = ((VariableReference) fieldValue).getName();
                hasValue = coercedVariableValues.containsKey(variableName);
                value = coercedVariableValues.get(variableName);
            } else {
                value = fieldValue;
            }
            if (!hasValue && inputFieldType.hasSetDefaultValue()) {
                //TODO: default value should be coerced
                coercedValues.put(fieldName, defaultValue);
            } else if (isNonNull(fieldType) && (!hasValue || value == null)) {
                throw new NonNullableValueCoercedAsNullException(inputFieldType);
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(fieldName, null);
                } else if (fieldValue instanceof VariableReference) {
                    coercedValues.put(fieldName, value);
                } else {
                    value = coerceValueAst(fieldVisibility, fieldType, fieldValue, coercedVariableValues);
                    coercedValues.put(fieldName, value);
                }
            } else {
                // nullable type && hasValue == false && hasDefaultValue == false
                // meaning no value was provided for this field
            }
        }
        return coercedValues;
    }

    private void assertNonNullInputField(GraphQLInputObjectField inputTypeField) {
        if (isNonNull(inputTypeField.getType())) {
            throw new NonNullableValueCoercedAsNullException(inputTypeField);
        }
    }

    private Map<String, ObjectField> mapObjectValueFieldsByName(ObjectValue inputValue) {
        Map<String, ObjectField> inputValueFieldsByName = new LinkedHashMap<>();
        for (ObjectField objectField : inputValue.getObjectFields()) {
            inputValueFieldsByName.put(objectField.getName(), objectField);
        }
        return inputValueFieldsByName;
    }
}
