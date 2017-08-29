package graphql.schema;


import graphql.Directives;
import graphql.schema.validation.InvalidSchemaException;
import graphql.schema.validation.SchemaValidationError;
import graphql.schema.validation.SchemaValidator;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertNotNull;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;

public class GraphQLSchema {


    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;
    private final GraphQLObjectType subscriptionType;
    private final Map<String, GraphQLType> typeMap;
    private final Set<GraphQLType> additionalTypes;
    private final Set<GraphQLDirective> directives;
    private final GraphqlFieldVisibility fieldVisibility;


    public GraphQLSchema(GraphQLObjectType queryType) {
        this(queryType, null, Collections.emptySet());
    }

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, Set<GraphQLType> additionalTypes) {
        this(queryType, mutationType, null, additionalTypes);
    }

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, GraphQLObjectType subscriptionType, Set<GraphQLType> dictionary) {
        this(queryType, mutationType, subscriptionType, dictionary, Collections.emptySet(), DEFAULT_FIELD_VISIBILITY);
    }

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, GraphQLObjectType subscriptionType, Set<GraphQLType> dictionary, Set<GraphQLDirective> directives, GraphqlFieldVisibility fieldVisibility) {
        assertNotNull(dictionary, "dictionary can't be null");
        assertNotNull(queryType, "queryType can't be null");
        assertNotNull(directives, "directives can't be null");
        assertNotNull(fieldVisibility, "fieldVisibility can't be null");
        this.queryType = queryType;
        this.mutationType = mutationType;
        this.subscriptionType = subscriptionType;
        this.fieldVisibility = fieldVisibility;
        this.additionalTypes = dictionary;
        this.directives = new HashSet<>(Arrays.asList(Directives.IncludeDirective, Directives.SkipDirective));
        this.directives.addAll(directives);
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

    public GraphqlFieldVisibility getFieldVisibility() {
        return fieldVisibility;
    }

    public List<GraphQLDirective> getDirectives() {
        return new ArrayList<>(directives);
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

    /**
     * @return a new schema builder
     */
    public static Builder newSchema() {
        return new Builder();
    }

    /**
     * This allows you to build a schema from an existing schema.  It copies everything from the existing
     * schema and then allows you to replace them.
     *
     * @param existingSchema the existing schema
     *
     * @return a new schema builder
     */
    public static Builder newSchema(GraphQLSchema existingSchema) {
        return new Builder()
                .query(existingSchema.getQueryType())
                .mutation(existingSchema.getMutationType())
                .subscription(existingSchema.getSubscriptionType())
                .fieldVisibility(existingSchema.getFieldVisibility());
    }

    public static class Builder {
        private GraphQLObjectType queryType;
        private GraphQLObjectType mutationType;
        private GraphQLObjectType subscriptionType;
        private GraphqlFieldVisibility fieldVisibility = DEFAULT_FIELD_VISIBILITY;

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

        public Builder fieldVisibility(GraphqlFieldVisibility fieldVisibility) {
            this.fieldVisibility = fieldVisibility;
            return this;
        }

        public GraphQLSchema build() {
            return build(Collections.emptySet(), Collections.emptySet());
        }

        public GraphQLSchema build(Set<GraphQLType> additionalTypes) {
            return build(additionalTypes, Collections.emptySet());
        }

        public GraphQLSchema build(Set<GraphQLType> additionalTypes, Set<GraphQLDirective> additionalDirectives) {
            assertNotNull(additionalTypes, "additionalTypes can't be null");
            assertNotNull(additionalDirectives, "additionalDirectives can't be null");
            GraphQLSchema graphQLSchema = new GraphQLSchema(queryType, mutationType, subscriptionType, additionalTypes, additionalDirectives, fieldVisibility);
            new SchemaUtil().replaceTypeReferences(graphQLSchema);
            Collection<SchemaValidationError> errors = new SchemaValidator().validateSchema(graphQLSchema);
            if (errors.size() > 0) {
                throw new InvalidSchemaException(errors);
            }
            return graphQLSchema;
        }
    }
}
