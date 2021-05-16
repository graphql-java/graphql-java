package graphql.schema;

import graphql.PublicApi;

/**
 * Used by @{@link GraphQLArgument} and {@link GraphQLInputObjectField} to represent different value states.
 */
@PublicApi
public enum ValueState {
    /**
     * Value was never set aka not provided
     */
    NOT_SET,
    /**
     * The value is an Ast literal
     */
    LITERAL,
    /**
     * The value is an external value
     */
    EXTERNAL_VALUE,
    /**
     * This is only used to preserve backward compatibility (for now): it is a value which is assumed to
     * be already coerced.
     * This will be removed at one point.
     */
    INTERNAL_VALUE
}