package graphql.execution;


import graphql.GraphQLException;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.schema.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        GraphQLType type = SchemaUtil.findType(schema, variableDefinition.getType().getName());

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
        }
        return null;
    }

    private Object coerceValueAst(GraphQLType type, Value inputValue, Map<String, Object> variables) {
        if (type instanceof GraphQLScalarType) {
            return ((GraphQLScalarType) type).getCoercing().coerceLiteral(inputValue);
        }
        return null;
    }

    private Object coerceValueForScalar(GraphQLScalarType graphQLScalarType, Object value) {
        return graphQLScalarType.getCoercing().coerce(value);
    }
}
