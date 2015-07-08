package graphql.schema;


public class GraphQLFieldArgument {

    private final String name;
    private final GraphQLInputType graphQLInputType;
    private final Object defaultValue;

    public GraphQLFieldArgument(String name, GraphQLInputType graphQLInputType, Object defaultValue) {
        this.name = name;
        this.graphQLInputType = graphQLInputType;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public GraphQLInputType getGraphQLInputType() {
        return graphQLInputType;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }
}
