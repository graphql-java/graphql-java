package graphql.normalized;

import graphql.Assert;
import graphql.Internal;
import graphql.language.FragmentDefinition;
import graphql.schema.GraphQLSchema;

import java.util.LinkedHashMap;
import java.util.Map;

@Internal
public class FieldCollectorNormalizedQueryParams {
    private final GraphQLSchema graphQLSchema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final Map<String, Object> variables;

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return fragmentsByName;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }


    private FieldCollectorNormalizedQueryParams(GraphQLSchema graphQLSchema,
                                                Map<String, Object> variables,
                                                Map<String, FragmentDefinition> fragmentsByName) {
        this.fragmentsByName = fragmentsByName;
        this.graphQLSchema = graphQLSchema;
        this.variables = variables;
    }

    public static Builder newParameters() {
        return new Builder();
    }

    public static class Builder {
        private GraphQLSchema graphQLSchema;
        private final Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        private final Map<String, Object> variables = new LinkedHashMap<>();

        /**
         * @see FieldCollectorNormalizedQueryParams#newParameters()
         */
        private Builder() {

        }

        public Builder schema(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = graphQLSchema;
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

        public FieldCollectorNormalizedQueryParams build() {
            Assert.assertNotNull(graphQLSchema, () -> "You must provide a schema");
            return new FieldCollectorNormalizedQueryParams(graphQLSchema, variables, fragmentsByName);
        }

    }
}
