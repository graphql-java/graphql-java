package graphql.execution;

import graphql.Assert;
import graphql.language.FragmentDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.LinkedHashMap;
import java.util.Map;

public class FieldCollectorParameters {
    private final GraphQLSchema graphQLSchema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final Map<String, Object> variables;
    private final GraphQLObjectType objectType;

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return fragmentsByName;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public GraphQLObjectType getObjectType() {
        return objectType;
    }

    private FieldCollectorParameters(GraphQLSchema graphQLSchema, Map<String, Object> variables, Map<String, FragmentDefinition> fragmentsByName, GraphQLObjectType objectType) {
        this.fragmentsByName = fragmentsByName;
        this.graphQLSchema = graphQLSchema;
        this.variables = variables;
        this.objectType = objectType;
    }

    public static Builder newParameters(GraphQLSchema graphQLSchema, GraphQLObjectType objectType) {
        Assert.assertNotNull(graphQLSchema, "You must provide a schema");
        Assert.assertNotNull(objectType, "You must provide an object type");
        return new Builder().schema(graphQLSchema).objectType(objectType);
    }

    public static class Builder {
        private GraphQLSchema graphQLSchema;
        private Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        private Map<String, Object> variables = new LinkedHashMap<>();
        private GraphQLObjectType objectType;

        public Builder schema(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = graphQLSchema;
            return this;
        }

        public Builder objectType(GraphQLObjectType objectType) {
            this.objectType = objectType;
            return this;
        }

        public Builder fragments(Map<String, FragmentDefinition> fragmentsByName) {
            this.fragmentsByName.putAll(fragmentsByName);
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public FieldCollectorParameters build() {
            return new FieldCollectorParameters(graphQLSchema, variables, fragmentsByName, objectType);
        }

    }
}
