package graphql.introspection;

import graphql.schema.GraphQLFieldDefinition;

/**
 * This interface allows a different implementation of Introspection
 * to be used.  The graphql schema as defined by the specification
 * is used by default.
 *
 * See <a href="http://facebook.github.io/graphql/#sec-Introspection">http://facebook.github.io/graphql/#sec-Introspection</a>
 */
public interface IntrospectionSupport {

    GraphQLFieldDefinition __Schema();

    GraphQLFieldDefinition __Type();

    GraphQLFieldDefinition __TypeName();
}
