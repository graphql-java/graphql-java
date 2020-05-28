package graphql.schema;

import graphql.PublicApi;

import java.util.List;

/**
 * A GraphQLType which can implement interfaces
 */
@PublicApi
public interface GraphQLImplementingType extends GraphQLFieldsContainer {
    /**
     * @return This returns GraphQLInterface or GraphQLTypeReference instances, if the type
     * references are not resolved yet. After they are resolved it contains only GraphQLInterface.
     * Reference resolving happens when a full schema is built.
     */
    List<GraphQLNamedOutputType> getInterfaces();
}
