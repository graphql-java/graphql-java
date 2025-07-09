package graphql.normalized;

import graphql.PublicApi;

/**
 * A {@link ParsedNormalizedOperation} represents a parsed and normalized operation in GraphQL.
 * It is used to handle the execution of GraphQL operations according to the specification,
 * including merging duplicate fields and handling type detection for fields that may correspond
 * to multiple object types.
 */
@PublicApi
public interface ParsedNormalizedOperation {
}
