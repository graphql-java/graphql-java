package graphql.schema.property

import graphql.schema.GraphQLSchema
import graphql.schema.idl.FastSchemaGenerator
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser

internal fun parseTestSchema(
    sdl: String,
    runtimeWiring: RuntimeWiring = RuntimeWiring.MOCKED_WIRING
): GraphQLSchema =
    FastSchemaGenerator().makeExecutableSchema(
        SchemaParser().parse(sdl),
        runtimeWiring
    )
