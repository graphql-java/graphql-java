package graphql.schema;


public class GraphQLSchema {

    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;

    public GraphQLSchema(GraphQLObjectType queryType) {
        this(queryType, null);
    }


    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType) {
        this.queryType = queryType;
        this.mutationType = mutationType;
    }

    public GraphQLObjectType getQueryType() {
        return queryType;
    }


    public GraphQLObjectType getMutationType() {
        return mutationType;
    }


    public boolean isSupportingMutations() {
        return mutationType != null;
    }

    public static class Builder {
        private GraphQLObjectType queryType;
        private GraphQLObjectType mutationType;

        public Builder setQueryType(GraphQLObjectType queryType) {
            this.queryType = queryType;
            return this;
        }

        public Builder setMutationType(GraphQLObjectType mutationType) {
            this.mutationType = mutationType;
            return this;
        }

        public GraphQLSchema build() {
            return new GraphQLSchema(queryType, mutationType);
        }
    }
}
