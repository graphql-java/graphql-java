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
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.Assert.assertShouldNeverHappen;

@Internal
public class ValuesResolver {

    /**
     * The http://facebook.github.io/graphql/#sec-Coercing-Variable-Values says :
     *
     * <pre>
     * 1. Let coercedValues be an empty unordered Map.
     * 2. Let variableDefinitions be the variables defined by operation.
     * 3. For each variableDefinition in variableDefinitions:
     *      a. Let variableName be the name of variableDefinition.
     *      b. Let variableType be the expected type of variableDefinition.
     *      c. Let defaultValue be the default value for variableDefinition.
     *      d. Let value be the value provided in variableValues for the name variableName.
     *      e. If value does not exist (was not provided in variableValues):
     *          i. If defaultValue exists (including null):
     *              1. Add an entry to coercedValues named variableName with the value defaultValue.
     *          ii. Otherwise if variableType is a Non‐Nullable type, throw a query error.
     *          iii. Otherwise, continue to the next variable definition.
     *      f. Otherwise, if value cannot be coerced according to the input coercion rules of variableType, throw a query error.
     *      g. Let coercedValue be the result of coercing value according to the input coercion rules of variableType.
     *      h. Add an entry to coercedValues named variableName with the value coercedValue.
     * 4. Return coercedValues.
     * </pre>
     *
     * @param schema              the schema
     * @param variableDefinitions the variable definitions
     * @param variableValues      the supplied variables
     *
     * @return coerced variable values as a map
     */
    public Map<String, Object> coerceArgumentValues(GraphQLSchema schema, List<VariableDefinition> variableDefinitions, Map<String, Object> variableValues) {
        Map<String, Object> coercedValues = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            String variableName = variableDefinition.getName();
            GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());

            try {
                // 3.e
                if (!variableValues.containsKey(variableName)) {
                    Value defaultValue = variableDefinition.getDefaultValue();
                    if (defaultValue != null) {
                        // 3.e.i
                        Object coercedValue = coerceValueAst(variableType, variableDefinition.getDefaultValue(), null);
                        coercedValues.put(variableName, coercedValue);
                    } else if (isNonNullType(variableType)) {
                        // 3.e.ii
                        throw new NonNullableValueCoercedAsNullException(variableDefinition, variableType);
                    }
                } else {
                    Object value = variableValues.get(variableName);
                    // 3.f
                    Object coercedValue = getVariableValue(variableDefinition, variableType, value);
                    // 3.g
                    coercedValues.put(variableName, coercedValue);
                }
            } catch (CoercingParseValueException e) {
                throw new CoercingParseValueException(e.getMessage(), e.getCause(), variableDefinition.getSourceLocation());
            }
        }

        return coercedValues;
    }

    private Object getVariableValue(VariableDefinition variableDefinition, GraphQLType variableType, Object value) {

        if (value == null && variableDefinition.getDefaultValue() != null) {
            return coerceValueAst(variableType, variableDefinition.getDefaultValue(), null);
        }

        return coerceValue(variableDefinition, variableType, value);
    }

    private boolean isNonNullType(GraphQLType variableType) {
        return variableType instanceof GraphQLNonNull;
    }

    public Map<String, Object> getArgumentValues(List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        if (argumentTypes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Argument> argumentMap = argumentMap(arguments);
        for (GraphQLArgument fieldArgument : argumentTypes) {
            String argName = fieldArgument.getName();
            Argument argument = argumentMap.get(argName);
            Object value;
            if (argument != null) {
                value = coerceValueAst(fieldArgument.getType(), argument.getValue(), variables);
            } else {
                value = fieldArgument.getDefaultValue();
            }
            // only put an arg into the result IF they specified a variable at all or
            // the default value ended up being something non null
            if (argumentMap.containsKey(argName) || value != null) {
                result.put(argName, value);
            }
        }
        return result;
    }


    private Map<String, Argument> argumentMap(List<Argument> arguments) {
        Map<String, Argument> result = new LinkedHashMap<>();
        for (Argument argument : arguments) {
            result.put(argument.getName(), argument);
        }
        return result;
    }


    @SuppressWarnings("unchecked")
    private Object coerceValue(VariableDefinition variableDefinition, GraphQLType graphQLType, Object value) {
        if (graphQLType instanceof GraphQLNonNull) {
            Object returnValue = coerceValue(variableDefinition, ((GraphQLNonNull) graphQLType).getWrappedType(), value);
            if (returnValue == null) {
                throw new NonNullableValueCoercedAsNullException(variableDefinition, graphQLType);
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
            return coerceValueForList(variableDefinition, (GraphQLList) graphQLType, value);
        } else if (graphQLType instanceof GraphQLInputObjectType) {
            if (value instanceof Map) {
                return coerceValueForInputObjectType(variableDefinition, (GraphQLInputObjectType) graphQLType, (Map<String, Object>) value);
            } else {
                throw new CoercingParseValueException("Variables for GraphQLInputObjectType must be an instance of a Map according to the graphql specification.  The offending object was a " + value.getClass().getName());
            }
        } else {
            return assertShouldNeverHappen("unhandled type " + graphQLType);
        }
    }

    private Object coerceValueForInputObjectType(VariableDefinition variableDefinition, GraphQLInputObjectType inputObjectType, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<GraphQLInputObjectField> fields = inputObjectType.getFields();
        List<String> fieldNames = fields.stream().map(GraphQLInputObjectField::getName).collect(Collectors.toList());
        for (String inputFieldName : input.keySet()) {
            if (!fieldNames.contains(inputFieldName)) {
                throw new InputMapDefinesTooManyFieldsException(inputObjectType, inputFieldName);
            }
        }

        for (GraphQLInputObjectField inputField : fields) {
            if (input.containsKey(inputField.getName()) || alwaysHasValue(inputField)) {
                Object value = coerceValue(variableDefinition, inputField.getType(), input.get(inputField.getName()));
                result.put(inputField.getName(), value == null ? inputField.getDefaultValue() : value);
            }
        }
        return result;
    }

    private boolean alwaysHasValue(GraphQLInputObjectField inputField) {
        return inputField.getDefaultValue() != null
                || inputField.getType() instanceof GraphQLNonNull;
    }

    private Object coerceValueForScalar(GraphQLScalarType graphQLScalarType, Object value) {
        return graphQLScalarType.getCoercing().parseValue(value);
    }

    private Object coerceValueForEnum(GraphQLEnumType graphQLEnumType, Object value) {
        return graphQLEnumType.getCoercing().parseValue(value);
    }

    private List coerceValueForList(VariableDefinition variableDefinition, GraphQLList graphQLList, Object value) {
        if (value instanceof Iterable) {
            List<Object> result = new ArrayList<>();
            for (Object val : (Iterable) value) {
                result.add(coerceValue(variableDefinition, graphQLList.getWrappedType(), val));
            }
            return result;
        } else {
            return Collections.singletonList(coerceValue(variableDefinition, graphQLList.getWrappedType(), value));
        }
    }

    private Object coerceValueAst(GraphQLType type, Value inputValue, Map<String, Object> variables) {
        if (inputValue instanceof VariableReference) {
            return variables.get(((VariableReference) inputValue).getName());
        }
        if (inputValue instanceof NullValue) {
            return null;
        }
        if (type instanceof GraphQLScalarType) {
            return ((GraphQLScalarType) type).getCoercing().parseLiteral(inputValue);
        }
        if (type instanceof GraphQLNonNull) {
            return coerceValueAst(((GraphQLNonNull) type).getWrappedType(), inputValue, variables);
        }
        if (type instanceof GraphQLInputObjectType) {
            return coerceValueAstForInputObject((GraphQLInputObjectType) type, (ObjectValue) inputValue, variables);
        }
        if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).getCoercing().parseLiteral(inputValue);
        }
        if (type instanceof GraphQLList) {
            return coerceValueAstForList((GraphQLList) type, inputValue, variables);
        }
        return null;
    }

    private Object coerceValueAstForList(GraphQLList graphQLList, Value value, Map<String, Object> variables) {
        if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            List<Object> result = new ArrayList<>();
            for (Value singleValue : arrayValue.getValues()) {
                result.add(coerceValueAst(graphQLList.getWrappedType(), singleValue, variables));
            }
            return result;
        } else {
            return Collections.singletonList(coerceValueAst(graphQLList.getWrappedType(), value, variables));
        }
    }

    private Object coerceValueAstForInputObject(GraphQLInputObjectType type, ObjectValue inputValue, Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, ObjectField> inputValueFieldsByName = mapObjectValueFieldsByName(inputValue);

        for (GraphQLInputObjectField inputTypeField : type.getFields()) {
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
                    fieldObject = coerceValueAst(inputTypeField.getType(), fieldInputValue, variables);
                }

                if (fieldObject == null) {
                    if (!field.getValue().isEqualTo(NullValue.Null)) {
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
        if (inputTypeField.getType() instanceof GraphQLNonNull) {
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
