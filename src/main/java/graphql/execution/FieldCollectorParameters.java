package graphql.execution;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.language.FragmentDefinition;
import graphql.schema.ExecutableSchema;
import graphql.schema.SchemaObject;

import java.util.Map;

/**
 * Internal because FieldCollector is internal.
 */
@Internal
public class FieldCollectorParameters {
    private final ExecutableSchema schema;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final Map<String, Object> variables;
    private final SchemaObject objectType;
    private final GraphQLContext graphQLContext;

    public ExecutableSchema getSchema() {
        return schema;
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return fragmentsByName;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public SchemaObject getObjectType() {
        return objectType;
    }

    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }

    private FieldCollectorParameters(Builder builder) {
        this.fragmentsByName = builder.fragmentsByName;
        this.schema = builder.schema;
        this.variables = builder.variables;
        this.objectType = builder.objectType;
        this.graphQLContext = builder.graphQLContext;
    }

    public static Builder newParameters() {
        return new Builder();
    }

    public static class Builder {
        private ExecutableSchema schema;
        private Map<String, FragmentDefinition> fragmentsByName;
        private Map<String, Object> variables;
        private SchemaObject objectType;
        private GraphQLContext graphQLContext = GraphQLContext.getDefault();

        /**
         * @see FieldCollectorParameters#newParameters()
         */
        private Builder() {

        }

        public Builder schema(ExecutableSchema schema) {
            this.schema = schema;
            return this;
        }

        public Builder objectType(SchemaObject objectType) {
            this.objectType = objectType;
            return this;
        }

        public Builder graphQLContext(GraphQLContext graphQLContext) {
            this.graphQLContext = graphQLContext;
            return this;
        }

        public Builder fragments(Map<String, FragmentDefinition> fragmentsByName) {
            this.fragmentsByName = fragmentsByName;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public FieldCollectorParameters build() {
            Assert.assertNotNull(schema, "You must provide a schema");
            return new FieldCollectorParameters(this);
        }

    }
}
