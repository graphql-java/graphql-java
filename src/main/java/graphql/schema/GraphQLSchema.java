package graphql.schema;


import graphql.Assert;
import graphql.Directives;

import java.util.*;

import static graphql.Assert.assertNotNull;

public class GraphQLSchema {

    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;
    private final Map<String, GraphQLType> typeMap;
    private Set<GraphQLType> dictionary;

    public GraphQLSchema(GraphQLObjectType queryType) {
        this(queryType, null, Collections.<GraphQLType>emptySet());
    }

    public Set<GraphQLType> getDictionary() {
        return dictionary;
    }

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, Set<GraphQLType> dictionary) {
        assertNotNull(dictionary, "dictionary can't be null");
        assertNotNull(queryType, "queryType can't be null");
        this.queryType = queryType;
        this.mutationType = mutationType;
        this.dictionary = dictionary;
        typeMap = new SchemaUtil().allTypes(this, dictionary);
    }

    public GraphQLType getType(String typeName) {
        return typeMap.get(typeName);
    }

    public List<GraphQLType> getAllTypesAsList() {
        return new ArrayList<GraphQLType>(typeMap.values());
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
            return build(Collections.<GraphQLType>emptySet());
        }

        public GraphQLSchema build(Set<GraphQLType> dictionary) {
            Assert.assertNotNull(dictionary, "dictionary can't be null");
            GraphQLSchema graphQLSchema = new GraphQLSchema(queryType, mutationType, dictionary);
            new SchemaUtil().replaceTypeReferences(graphQLSchema);
            return graphQLSchema;
        }


    }

}
