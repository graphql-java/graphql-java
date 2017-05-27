package graphql.execution;


import graphql.GraphQLException;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
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

public class ValuesResolver {


    public Map<String, Object> getVariableValues(GraphQLSchema schema, List<VariableDefinition> variableDefinitions, Map<String, Object> args) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            String varName = variableDefinition.getName();
            // we transfer the variable as field arguments if its present as value
            if (args.containsKey(varName)) {
                Object arg = args.get(varName);
                Object variableValue = getVariableValue(schema, variableDefinition, arg);
                result.put(varName, variableValue);
            }
        }
        return result;
    }


    public Map<String, Object> getArgumentValues(List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
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


    private Object getVariableValue(GraphQLSchema schema, VariableDefinition variableDefinition, Object inputValue) {
        GraphQLType type = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());

        //noinspection ConstantConditions
        if (!isValid(type, inputValue)) {
            throw new GraphQLException("Invalid value for type");
        }

        if (inputValue == null && variableDefinition.getDefaultValue() != null) {
            return coerceValueAst(type, variableDefinition.getDefaultValue(), null);
        }

        return coerceValue(type, inputValue);
    }

    private boolean isValid(GraphQLType type, Object inputValue) {
        return true;
    }

    private Object coerceValue(GraphQLType graphQLType, Object value) {
        if (graphQLType instanceof GraphQLNonNull) {
            Object returnValue = coerceValue(((GraphQLNonNull) graphQLType).getWrappedType(), value);
            if (returnValue == null) {
                throw new NonNullableValueCoercedAsNullException(graphQLType);
            }
            return returnValue;
        }

        if (value == null) return null;

        if (graphQLType instanceof GraphQLScalarType) {
            return coerceValueForScalar((GraphQLScalarType) graphQLType, value);
        } else if (graphQLType instanceof GraphQLEnumType) {
            return coerceValueForEnum((GraphQLEnumType) graphQLType, value);
        } else if (graphQLType instanceof GraphQLList) {
            return coerceValueForList((GraphQLList) graphQLType, value);
        } else if (graphQLType instanceof GraphQLInputObjectType && value instanceof Map) {
            //noinspection unchecked
            return coerceValueForInputObjectType((GraphQLInputObjectType) graphQLType, (Map<String, Object>) value);
        } else if (graphQLType instanceof GraphQLInputObjectType) {
            return value;
        } else {
            throw new GraphQLException("unknown type " + graphQLType);
        }
    }

    private Object coerceValueForInputObjectType(GraphQLInputObjectType inputObjectType, Map<String, Object> input) {
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
                Object value = coerceValue(inputField.getType(), input.get(inputField.getName()));
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

    private List coerceValueForList(GraphQLList graphQLList, Object value) {
        if (value instanceof Iterable) {
            List<Object> result = new ArrayList<>();
            for (Object val : (Iterable) value) {
                result.add(coerceValue(graphQLList.getWrappedType(), val));
            }
            return result;
        } else {
            return Collections.singletonList(coerceValue(graphQLList.getWrappedType(), value));
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
            throw new NonNullableValueCoercedAsNullException(inputTypeField.getType());
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
