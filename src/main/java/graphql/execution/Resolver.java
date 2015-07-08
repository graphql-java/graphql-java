package graphql.execution;


import graphql.language.VariableDefinition;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.SchemaUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Resolver {


    public Map<String, Object> getVariables(GraphQLSchema schema, List<VariableDefinition> variableDefinitions, Map<String, Object> inputs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            result.put(variableDefinition.getName(), getVariableValue(schema, variableDefinition, inputs.get(variableDefinition.getName())));
        }
        return result;
    }

    private Object getVariableValue(GraphQLSchema schema, VariableDefinition variableDefinition, Object inputValue) {
        GraphQLType type = SchemaUtil.findType(schema, variableDefinition.getType().getName());
        return coerceValue(type, inputValue);
    }

    private Object coerceValue(GraphQLType graphQLType, Object value) {
        if (graphQLType instanceof GraphQLScalarType) {
            return coerceValueForScalar((GraphQLScalarType) graphQLType, value);
        }
        return null;
    }

    private Object coerceValueForScalar(GraphQLScalarType graphQLScalarType, Object value) {
        return graphQLScalarType.getCoercing().coerce(value);
    }
}
