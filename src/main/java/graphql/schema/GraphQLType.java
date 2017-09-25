package graphql.schema;


import graphql.PublicApi;

/**
 * All types in graphql have a name
 */
@PublicApi
public interface GraphQLType {
    /**
     * @return the name of the type which MUST fit within the regular expression {@code [_A-Za-z][_0-9A-Za-z]*}
     */
    String getName();
}
