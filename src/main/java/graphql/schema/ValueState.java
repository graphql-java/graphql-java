package graphql.schema;

import graphql.PublicApi;

/**
 * Used by @{@link GraphQLArgument} and {@link GraphQLInputObjectField}
 */
@PublicApi
public enum ValueState {
    NOT_SET,
    LITERAL,
    EXTERNAL_VALUE,
    INTERNAL_VALUE // this is deprecated and should not be used going forward, will be removed in the future
}