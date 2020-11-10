package graphql.execution;


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
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.ArrayList;
import java.util.Collections;
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
     * @return coerced variable values as a map
     */
    public Map<String, Object> coerceVariableValues(GraphQLSchema schema, List<VariableDefinition> variableDefinitions, Map<String, Object> variableValues) {
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        Map<String, Object> coercedValues = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            String variableName = variableDefinition.getName();
            GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());

            if (!variableValues.containsKey(variableName)) {
                Value defaultValue = variableDefinition.getDefaultValue();
                if (defaultValue != null) {
                    Object coercedValue = coerceValueAst(fieldVisibility, variableType, defaultValue, null);
                    coercedValues.put(variableName, coercedValue);
                } else if (isNonNull(variableType)) {
                    throw new NonNullableValueCoercedAsNullException(variableDefinition, variableType);
                }
            } else {
                Object value = variableValues.get(variableName);
                Object coercedValue = coerceValue(fieldVisibility, variableDefinition, variableDefinition.getName(), variableType, value);
                coercedValues.put(variableName, coercedValue);
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

    private Map<String, Object> getArgumentValuesImpl(GraphQLCodeRegistry codeRegistry, List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        if (argumentTypes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Argument> argumentMap = argumentMap(arguments);
        for (GraphQLArgument fieldArgument : argumentTypes) {
            String argName = fieldArgument.getName();
            Argument argument = argumentMap.get(argName);
            Object value = null;
            if (argument != null) {
                value = coerceValueAst(codeRegistry.getFieldVisibility(), fieldArgument.getType(), argument.getValue(), variables);
            }
            if (value == null
                    && !(argument != null && argument.getValue() instanceof NullValue)
                    && !(argument != null && argument.getValue() instanceof VariableReference && variables.containsKey(((VariableReference) argument.getValue()).getName()))
            ) {
                value = fieldArgument.getDefaultValue();
            }
            boolean wasValueProvided = false;
            if (argumentMap.containsKey(argName)) {
                if (argument.getValue() instanceof VariableReference) {
                    wasValueProvided = variables.containsKey(((VariableReference) argument.getValue()).getName());
                } else {
                    wasValueProvided = true;
                }
            }
            if (fieldArgument.hasSetDefaultValue()) {
                wasValueProvided = true;
            }
            if (wasValueProvided) {
                result.put(argName, value);
            }
        }
        return result;
    }


    private Map<String, Argument> argumentMap(List<Argument> arguments) {
        Map<String, Argument> result = new LinkedHashMap<>(arguments.size());
        for (Argument argument : arguments) {
            result.put(argument.getName(), argument);
        }
        return result;
    }


    @SuppressWarnings("unchecked")
    private Object coerceValue(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, String inputName, GraphQLType graphQLType, Object value) {
        try {
            if (isNonNull(graphQLType)) {
                Object returnValue =
                        coerceValue(fieldVisibility, variableDefinition, inputName, unwrapOne(graphQLType), value);
                if (returnValue == null) {
                    throw new NonNullableValueCoercedAsNullException(variableDefinition, inputName, graphQLType);
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
                return coerceValueForList(fieldVisibility, variableDefinition, inputName, (GraphQLList) graphQLType, value);
            } else if (graphQLType instanceof GraphQLInputObjectType) {
                if (value instanceof Map) {
                    return coerceValueForInputObjectType(fieldVisibility, variableDefinition, (GraphQLInputObjectType) graphQLType, (Map<String, Object>) value);
                } else {
                    throw CoercingParseValueException.newCoercingParseValueException()
                            .message("Expected type 'Map' but was '" + value.getClass().getSimpleName() +
                                    "'. Variables for input objects must be an instance of type 'Map'.")
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
                    .build();
        }

    }

    private Object coerceValueForInputObjectType(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, GraphQLInputObjectType inputObjectType, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<GraphQLInputObjectField> fields = fieldVisibility.getFieldDefinitions(inputObjectType);
        List<String> fieldNames = map(fields, GraphQLInputObjectField::getName);
        for (String inputFieldName : input.keySet()) {
            if (!fieldNames.contains(inputFieldName)) {
                throw new InputMapDefinesTooManyFieldsException(inputObjectType, inputFieldName);
            }
        }

        for (GraphQLInputObjectField inputField : fields) {
            if (input.containsKey(inputField.getName()) || alwaysHasValue(inputField)) {
                Object value = coerceValue(fieldVisibility, variableDefinition,
                        inputField.getName(),
                        inputField.getType(),
                        input.get(inputField.getName()));
                result.put(inputField.getName(), value == null ? inputField.getDefaultValue() : value);
            }
        }
        return result;
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

    private List coerceValueForList(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, String inputName, GraphQLList graphQLList, Object value) {
        if (value instanceof Iterable) {
            List<Object> result = new ArrayList<>();
            for (Object val : (Iterable) value) {
                result.add(coerceValue(fieldVisibility, variableDefinition, inputName, graphQLList.getWrappedType(), val));
            }
            return result;
        } else {
            return Collections.singletonList(coerceValue(fieldVisibility, variableDefinition, inputName, graphQLList.getWrappedType(), value));
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

    private Object coerceValueAstForInputObject(GraphqlFieldVisibility fieldVisibility, GraphQLInputObjectType type, ObjectValue inputValue, Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, ObjectField> inputValueFieldsByName = mapObjectValueFieldsByName(inputValue);

        List<GraphQLInputObjectField> inputFields = fieldVisibility.getFieldDefinitions(type);
        for (GraphQLInputObjectField inputTypeField : inputFields) {
            if (inputValueFieldsByName.containsKey(inputTypeField.getName())) {
                boolean putObjectInMap = true;

                ObjectField field = inputValueFieldsByName.get(inputTypeField.getName());
                Value fieldInputValue = field.getValue();

                Object fieldObject = null;
                if (fieldInputValue instanceof VariableReference) {
                    String varName = ((VariableReference) fieldInputValue).getName();
                    if (!variables.containsKey(varName)) {
                        putObjectInMap = false;
                    } else {
                        fieldObject = variables.get(varName);
                    }
                } else {
                    fieldObject = coerceValueAst(fieldVisibility, inputTypeField.getType(), fieldInputValue, variables);
                }

                if (fieldObject == null) {
                    if (!(field.getValue() instanceof NullValue)) {
                        fieldObject = inputTypeField.getDefaultValue();
                    }
                }
                if (putObjectInMap) {
                    result.put(field.getName(), fieldObject);
                } else {
                    assertNonNullInputField(inputTypeField);
                }
            } else if (inputTypeField.getDefaultValue() != null) {
                result.put(inputTypeField.getName(), inputTypeField.getDefaultValue());
            } else {
                assertNonNullInputField(inputTypeField);
            }
        }
        return result;
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
