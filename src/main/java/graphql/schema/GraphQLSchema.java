package graphql.schema;


public class GraphQLSchema {

    private GraphQLObjectType queryType;
    private GraphQLObjectType mutationType;

    public GraphQLObjectType getQueryType() {
        return queryType;
    }

    public void setQueryType(GraphQLObjectType queryType) {
        this.queryType = queryType;
    }

    public GraphQLObjectType getMutationType() {
        return mutationType;
    }

    public void setMutationType(GraphQLObjectType mutationType) {
        this.mutationType = mutationType;
    }
}
