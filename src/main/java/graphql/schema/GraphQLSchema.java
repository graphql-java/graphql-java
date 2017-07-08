package graphql.schema;


import graphql.Directives;
import graphql.introspection.IntrospectionTypeProvider;
import graphql.schema.validation.InvalidSchemaException;
import graphql.schema.validation.SchemaValidationError;
import graphql.schema.validation.SchemaValidator;

import java.util.*;

import static graphql.Assert.assertNotNull;

public class GraphQLSchema {

    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;
    private final GraphQLObjectType subscriptionType;
    private final Map<String, GraphQLType> typeMap;
    private final IntrospectionTypeProvider introspectionTypeProvider;
    private Set<GraphQLType> additionalTypes;

    public GraphQLSchema(GraphQLObjectType queryType) {
        this(queryType, null, Collections.emptySet());
    }

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, Set<GraphQLType> additionalTypes) {
        this(queryType, mutationType, null, additionalTypes);
    }

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, GraphQLObjectType subscriptionType, Set<GraphQLType> dictionary) {this(queryType, mutationType, subscriptionType, dictionary, IntrospectionTypeProvider.DEFAULT);}

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, GraphQLObjectType subscriptionType, Set<GraphQLType> dictionary, IntrospectionTypeProvider introspectionTypeProvider) {
        assertNotNull(dictionary, "dictionary can't be null");
        assertNotNull(queryType, "queryType can't be null");
        this.queryType = queryType;
        this.mutationType = mutationType;
        this.subscriptionType = subscriptionType;
        this.additionalTypes = dictionary;
        this.introspectionTypeProvider = introspectionTypeProvider;
        typeMap = new SchemaUtil().allTypes(this, dictionary);
    }

    public Set<GraphQLType> getAdditionalTypes() {
        return additionalTypes;
    }

    public GraphQLType getType(String typeName) {
        return typeMap.get(typeName);
    }

    public List<GraphQLType> getAllTypesAsList() {
        return new ArrayList<>(typeMap.values());
    }

    public GraphQLObjectType getQueryType() {
        return queryType;
    }

    public GraphQLObjectType getMutationType() {
        return mutationType;
    }

    public GraphQLObjectType getSubscriptionType() {
        return subscriptionType;
    }

    public List<GraphQLDirective> getDirectives() {
        return Arrays.asList(Directives.IncludeDirective, Directives.SkipDirective);
    }

    public GraphQLDirective getDirective(String name) {
        for (GraphQLDirective directive : getDirectives()) {
            if (directive.getName().equals(name)) return directive;
        }
        return null;
    }

    public boolean isSupportingMutations() {
        return mutationType != null;
    }

    public boolean isSupportingSubscriptions() {
        return subscriptionType != null;
    }

    public IntrospectionTypeProvider getIntrospectionTypeProvider() {return introspectionTypeProvider; }

    public static Builder newSchema() {
        return new Builder();
    }

    public static class Builder {
        protected GraphQLObjectType queryType;
        protected GraphQLObjectType mutationType;
        protected GraphQLObjectType subscriptionType;
        protected IntrospectionTypeProvider introspectionTypeProvider = IntrospectionTypeProvider.DEFAULT;

        public Builder query(GraphQLObjectType.Builder builder) {
            return query(builder.build());
        }

        public Builder query(GraphQLObjectType queryType) {
            this.queryType = queryType;
            return this;
        }

        public Builder mutation(GraphQLObjectType.Builder builder) {
            return mutation(builder.build());
        }

        public Builder mutation(GraphQLObjectType mutationType) {
            this.mutationType = mutationType;
            return this;
        }

        public Builder subscription(GraphQLObjectType.Builder builder) {
            return subscription(builder.build());
        }

        public Builder subscription(GraphQLObjectType subscriptionType) {
            this.subscriptionType = subscriptionType;
            return this;
        }

        public Builder introspectionTypeProvider(IntrospectionTypeProvider introspectionTypeProvider) {
            this.introspectionTypeProvider = introspectionTypeProvider;
            return this;
        }

        public GraphQLSchema build() {
            return build(Collections.emptySet());
        }

        protected GraphQLSchema instantiateType(Set<GraphQLType> additionalTypes) {
            return new GraphQLSchema(queryType, mutationType, subscriptionType, additionalTypes, introspectionTypeProvider);
        }

        public GraphQLSchema build(Set<GraphQLType> additionalTypes) {
            assertNotNull(additionalTypes, "additionalTypes can't be null");
            GraphQLSchema graphQLSchema = instantiateType(additionalTypes);
            new SchemaUtil().replaceTypeReferences(graphQLSchema);
            Collection<SchemaValidationError> errors = new SchemaValidator().validateSchema(graphQLSchema);
            if (errors.size() > 0) {
                throw new InvalidSchemaException(errors);
            }
            return graphQLSchema;
        }
    }
}
