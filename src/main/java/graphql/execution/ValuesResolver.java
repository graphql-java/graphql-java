package graphql.execution;


import graphql.GraphQLException;
import graphql.language.*;
import graphql.schema.*;

import java.util.*;

public class ValuesResolver {


    public Map<String, Object> getVariableValues(GraphQLSchema schema, List<VariableDefinition> variableDefinitions, Map<String, Object> inputs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            result.put(variableDefinition.getName(), getVariableValue(schema, variableDefinition, inputs.get(variableDefinition.getName())));
        }
        return result;
    }


    public Map<String, Object> getArgumentValues(List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Argument> argumentMap = argumentMap(arguments);
        for (GraphQLArgument fieldArgument : argumentTypes) {
            Argument argument = argumentMap.get(fieldArgument.getName());
            Object value;
            if (argument != null) {
                value = coerceValueAst(fieldArgument.getType(), argument.getValue(), variables);
            } else {
                value = fieldArgument.getDefaultValue();
            }
            result.put(fieldArgument.getName(), value);
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
                throw new GraphQLException("Null value for NonNull type " + graphQLType);
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
            return coerceValueForInputObjectType((GraphQLInputObjectType) graphQLType, (Map<String, Object>) value);
        } else if (graphQLType instanceof GraphQLInputObjectType) {
            return value;
        } else {
            throw new GraphQLException("unknown type " + graphQLType);
        }
    }

    private Object coerceValueForInputObjectType(GraphQLInputObjectType inputObjectType, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (GraphQLInputObjectField inputField : inputObjectType.getFields()) {
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
                ObjectField field = inputValueFieldsByName.get(inputTypeField.getName());
                Object fieldValue = coerceValueAst(inputTypeField.getType(), field.getValue(), variables);
                if (fieldValue == null) {
                    fieldValue = inputTypeField.getDefaultValue();
                }
                result.put(field.getName(), fieldValue);
            } else if (inputTypeField.getDefaultValue() != null) {
                result.put(inputTypeField.getName(), inputTypeField.getDefaultValue());
            } else if (inputTypeField.getType() instanceof GraphQLNonNull) {
                // Possibly overkill; an object literal with a missing non null field shouldn't pass validation
                throw new GraphQLException("Null value for NonNull type " + inputTypeField.getType());
            }
        }
        return result;
    }

    private Map<String, ObjectField> mapObjectValueFieldsByName(ObjectValue inputValue) {
        Map<String, ObjectField> inputValueFieldsByName = new LinkedHashMap<>();
        for (ObjectField objectField : inputValue.getObjectFields()) {
            inputValueFieldsByName.put(objectField.getName(), objectField);
        }
        return inputValueFieldsByName;
    }

}
