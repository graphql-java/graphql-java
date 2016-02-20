package graphql.schema;


import static graphql.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import graphql.Assert;
import graphql.Directives;

/**
 * <p>GraphQLSchema class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLSchema {

    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;
    private final Map<String, GraphQLType> typeMap;
    private Set<GraphQLType> dictionary;

    /**
     * <p>Constructor for GraphQLSchema.</p>
     *
     * @param queryType a {@link graphql.schema.GraphQLObjectType} object.
     */
    public GraphQLSchema(GraphQLObjectType queryType) {
        this(queryType, null, Collections.<GraphQLType>emptySet());
    }

    /**
     * <p>Getter for the field <code>dictionary</code>.</p>
     *
     * @return a {@link java.util.Set} object.
     */
    public Set<GraphQLType> getDictionary() {
      return dictionary;
    }

    /**
     * <p>Constructor for GraphQLSchema.</p>
     *
     * @param queryType a {@link graphql.schema.GraphQLObjectType} object.
     * @param mutationType a {@link graphql.schema.GraphQLObjectType} object.
     * @param dictionary a {@link java.util.Set} object.
     */
    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, Set<GraphQLType> dictionary) {
        assertNotNull(dictionary, "dictionary can't be null");
        assertNotNull(queryType, "queryType can't be null");
        this.queryType = queryType;
        this.mutationType = mutationType;
        this.dictionary = dictionary;
        typeMap = new SchemaUtil().allTypes(this, dictionary);
    }

    /**
     * <p>getType.</p>
     *
     * @param typeName a {@link java.lang.String} object.
     * @return a {@link graphql.schema.GraphQLType} object.
     */
    public GraphQLType getType(String typeName) {
        return typeMap.get(typeName);
    }

    /**
     * <p>getAllTypesAsList.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<GraphQLType> getAllTypesAsList() {
        return new ArrayList<GraphQLType>(typeMap.values());
    }

    /**
     * <p>Getter for the field <code>queryType</code>.</p>
     *
     * @return a {@link graphql.schema.GraphQLObjectType} object.
     */
    public GraphQLObjectType getQueryType() {
        return queryType;
    }


    /**
     * <p>Getter for the field <code>mutationType</code>.</p>
     *
     * @return a {@link graphql.schema.GraphQLObjectType} object.
     */
    public GraphQLObjectType getMutationType() {
        return mutationType;
    }

    /**
     * <p>getDirectives.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<GraphQLDirective> getDirectives() {
        return Arrays.asList(Directives.IncludeDirective, Directives.SkipDirective);
    }

    /**
     * <p>getDirective.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @return a {@link graphql.schema.GraphQLDirective} object.
     */
    public GraphQLDirective getDirective(String name) {
        for (GraphQLDirective directive : getDirectives()) {
            if (directive.getName().equals(name)) return directive;
        }
        return null;
    }


    /**
     * <p>isSupportingMutations.</p>
     *
     * @return a boolean.
     */
    public boolean isSupportingMutations() {
        return mutationType != null;
    }

    /**
     * <p>newSchema.</p>
     *
     * @return a {@link graphql.schema.GraphQLSchema.Builder} object.
     */
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
