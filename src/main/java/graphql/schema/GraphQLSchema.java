package graphql.schema;


import graphql.Directives;
import graphql.schema.validation.InvalidSchemaException;
import graphql.schema.validation.ValidationError;
import graphql.schema.validation.Validator;

import java.util.*;

import static graphql.Assert.assertNotNull;

public class GraphQLSchema {

    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;
    private final Map<String, GraphQLType> typeMap;
    private Set<GraphQLType> additionalTypes;

    public GraphQLSchema(GraphQLObjectType queryType) {
        this(queryType, null, Collections.emptySet());
    }

    public Set<GraphQLType> getAdditionalTypes() {
        return additionalTypes;
    }

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, Set<GraphQLType> additionalTypes) {
        assertNotNull(additionalTypes, "additionalTypes can't be null");
        assertNotNull(queryType, "queryType can't be null");
        this.queryType = queryType;
        this.mutationType = mutationType;
        this.additionalTypes = additionalTypes;
        typeMap = new SchemaUtil().allTypes(this, additionalTypes);
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

    public static Builder newSchema() {
        return new Builder();
    }

    public static class Builder {
        private GraphQLObjectType queryType;
        private GraphQLObjectType mutationType;

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

        public GraphQLSchema build() {
            return build(Collections.emptySet());
        }

        public GraphQLSchema build(Set<GraphQLType> additionalTypes) {
            assertNotNull(additionalTypes, "additionalTypes can't be null");
            GraphQLSchema graphQLSchema = new GraphQLSchema(queryType, mutationType, additionalTypes);
            new SchemaUtil().replaceTypeReferences(graphQLSchema);
            Collection<ValidationError> errors = new Validator().validateSchema(graphQLSchema);
            if (errors.size() > 0) {
                throw new InvalidSchemaException(errors);
            }
            return graphQLSchema;
        }
    }
}
