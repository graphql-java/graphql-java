package graphql.schema;

/**
 * environment for {@link DataFetcher data fetcher}.
 */
public interface DataFetchingEnvironment {

    Object getSource();

    Map<String, Object> getArguments();

    boolean containsArgument(String name);

    <T> T getArgument(String name);

    Object getContext();

    List<Field> getFields();

    GraphQLOutputType getFieldType();

    GraphQLType getParentType();

    GraphQLSchema getGraphQLSchema();
}
