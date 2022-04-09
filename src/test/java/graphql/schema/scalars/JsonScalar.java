package graphql.schema.scalars;

import graphql.Internal;
import graphql.schema.GraphQLScalarType;

import static graphql.schema.scalars.ObjectScalar.OBJECT_COERCING;

/**
 * Copied from ExtendedScalars to avoid the circular dependency but used for testing as an example of a complex scalar
 */
@Internal
public class JsonScalar {

    public static GraphQLScalarType JSON_SCALAR = GraphQLScalarType.newScalar()
            .name("JSON")
            .description("A JSON scalar")
            .coercing(OBJECT_COERCING)
            .build();
}
