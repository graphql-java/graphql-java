package graphql.schema;


public class GraphQLSchema {

    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;

    public GraphQLSchema(GraphQLObjectType queryType) {
        this.queryType = queryType;
        this.mutationType = null;
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
}
