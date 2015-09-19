package graphql.schema;


import static graphql.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import graphql.Directives;

public class GraphQLSchema {

    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;
    private final Map<String, GraphQLType> typeMap;
    private Set<GraphQLType> dictionary;

    public GraphQLSchema(GraphQLObjectType queryType) {
        this(queryType, null, null);
    }

    public Set<GraphQLType> getDictionary() {
      return dictionary;
    }

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, Set<GraphQLType> dictionary) {
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

        public Builder query(GraphQLObjectType queryType) {
            this.queryType = queryType;
            return this;
        }

        public Builder mutation(GraphQLObjectType mutationType) {
            this.mutationType = mutationType;
            return this;
        }

        public GraphQLSchema build() {
          return build(null);
      }

        public GraphQLSchema build(Set<GraphQLType> dictionary) {
          GraphQLSchema graphQLSchema = new GraphQLSchema(queryType, mutationType, dictionary);
          new SchemaUtil().replaceTypeReferences(graphQLSchema);
          return graphQLSchema;
      }


    }

}
