package graphql.schema.property

import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaPrinter
import io.kotest.property.Arb
import io.kotest.property.arbitrary.map

/** A generated schema paired with its printable SDL representation. */
internal data class GeneratedSchema(
    val schema: GraphQLSchema,
    val sdl: String
) {
    override fun toString(): String = sdl
}

/** Generate graphql-java schemas together with the SDL used in property failure output. */
internal fun Arb.Companion.generatedGraphQLSchema(
    config: Config = Config.default
): Arb<GeneratedSchema> =
    graphQLSchema(config).map { schema ->
        GeneratedSchema(schema, SchemaPrinter().print(schema))
    }
