package graphql.execution;


import graphql.GraphQLException;
import graphql.language.*;
import graphql.schema.*;

import java.util.*;

public class Resolver {


    public Map<String, Object> getVariableValues(GraphQLSchema schema, List<VariableDefinition> variableDefinitions, Map<String, Object> inputs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            result.put(variableDefinition.getName(), getVariableValue(schema, variableDefinition, inputs.get(variableDefinition.getName())));
        }
        return result;
    }


    public Map<String, Object> getArgumentValues(List<GraphQLFieldArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Argument> argumentMap = argumentMap(arguments);
        for (GraphQLFieldArgument fieldArgument : argumentTypes) {
            Argument argument = argumentMap.get(fieldArgument.getName());
            Object value = coerceValueAst(fieldArgument.getType(), argument.getValue(), variables);
            result.put(argument.getName(), value);
        }
        return result;
    }

    public Object getDirectiveValue(GraphQLDirective graphQLDirective, List<Directive> directives, Map<String, Object> variables) {
        // type of diretive?
        for (Directive directive : directives) {
            if (directive.getName().equals(graphQLDirective.getName())) {
                return coerceValueAst(null/*TODO:fix*/, directive.getValue(), variables);
            }
        }
        return null;

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
        if (graphQLType instanceof GraphQLScalarType) {
            return coerceValueForScalar((GraphQLScalarType) graphQLType, value);
        } else if (graphQLType instanceof GraphQLEnumType) {
            return coerceValueForEnum((GraphQLEnumType) graphQLType, value);
        } else if (graphQLType instanceof GraphQLList) {
            return coerceValueForList((GraphQLList) graphQLType, value);
        } else if (graphQLType instanceof GraphQLInputObjectType) {
            return coerceValueForInputObjectField((GraphQLInputObjectType) graphQLType, (Map<String, Object>) value);
        } else if (graphQLType instanceof GraphQLNonNull) {
            return coerceValue(((GraphQLNonNull) graphQLType).getWrappedType(), value);
        }
        return null;
    }

    private Object coerceValueForInputObjectField(GraphQLInputObjectType inputObjectType, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (GraphQLInputObjectField inputField : inputObjectType.getFields()) {
            Object value = coerceValue(inputField.getType(), input.get(inputField.getName()));
            result.put(inputField.getName(), value == null ? inputField.getDefaultValue() : value);

        }
        return result;
    }

    private Object coerceValueForScalar(GraphQLScalarType graphQLScalarType, Object value) {
        return graphQLScalarType.getCoercing().coerce(value);
    }

    private Object coerceValueForEnum(GraphQLEnumType graphQLEnumType, Object value) {
        return graphQLEnumType.getCoercing().coerce(value);
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
        if(inputValue instanceof VariableReference){
           return variables.get(((VariableReference) inputValue).getName());
        }
        if (type instanceof GraphQLScalarType) {
            return ((GraphQLScalarType) type).getCoercing().coerceLiteral(inputValue);
        }
        return null;
    }


}
