package graphql.execution;

import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.collect.ImmutableMapWithNullValues;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;

import java.util.Map;

@PublicApi
public class TypeResolutionParameters {

    private final GraphQLInterfaceType graphQLInterfaceType;
    private final GraphQLUnionType graphQLUnionType;
    private final MergedField field;
    private final Object value;
    private final ImmutableMapWithNullValues<String, Object> argumentValues;
    private final GraphQLSchema schema;
    private final Object context;
    private final GraphQLContext graphQLContext;

    private TypeResolutionParameters(Builder builder) {
        this.graphQLInterfaceType = builder.graphQLInterfaceType;
        this.graphQLUnionType = builder.graphQLUnionType;
        this.field = builder.field;
        this.value = builder.value;
        this.argumentValues = builder.argumentValues;
        this.schema = builder.schema;
        this.context = builder.context;
        this.graphQLContext = builder.graphQLContext;
    }

    public GraphQLInterfaceType getGraphQLInterfaceType() {
        return graphQLInterfaceType;
    }

    public GraphQLUnionType getGraphQLUnionType() {
        return graphQLUnionType;
    }

    public MergedField getField() {
        return field;
    }

    public Object getValue() {
        return value;
    }

    public Map<String, Object> getArgumentValues() {
        return argumentValues;
    }

    public GraphQLSchema getSchema() {
        return schema;
    }

    public static Builder newParameters() {
        return new Builder();
    }

    /**
     * @return the legacy context object
     *
     * @deprecated use {@link #getGraphQLContext()} instead
     */
    @Deprecated
    public Object getContext() {
        return context;
    }

    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }

    public static class Builder {

        private MergedField field;
        private GraphQLInterfaceType graphQLInterfaceType;
        private GraphQLUnionType graphQLUnionType;
        private Object value;
        private ImmutableMapWithNullValues<String, Object> argumentValues;
        private GraphQLSchema schema;
        private Object context;
        private GraphQLContext graphQLContext;

        public Builder field(MergedField field) {
            this.field = field;
            return this;
        }

        public Builder graphQLInterfaceType(GraphQLInterfaceType graphQLInterfaceType) {
            this.graphQLInterfaceType = graphQLInterfaceType;
            return this;
        }

        public Builder graphQLUnionType(GraphQLUnionType graphQLUnionType) {
            this.graphQLUnionType = graphQLUnionType;
            return this;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public Builder argumentValues(Map<String, Object> argumentValues) {
            this.argumentValues = ImmutableMapWithNullValues.copyOf(argumentValues);
            return this;
        }

        public Builder schema(GraphQLSchema schema) {
            this.schema = schema;
            return this;
        }

        @Deprecated
        public Builder context(Object context) {
            this.context = context;
            return this;
        }

        public Builder graphQLContext(GraphQLContext context) {
            this.graphQLContext = context;
            return this;
        }

        public TypeResolutionParameters build() {
            return new TypeResolutionParameters(this);
        }
    }
}
