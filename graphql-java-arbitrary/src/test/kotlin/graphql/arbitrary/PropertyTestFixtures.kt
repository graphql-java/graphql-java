package graphql.arbitrary

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

// JMB TODO: what is this? Try removing
/**
 * Build the incomplete schema snippets used by Viaduct's original unit tests without depending on
 * Viaduct's permissive SchemaFactory. Missing operation roots are supplied only for test setup.
 */
internal val String.asPermissiveTestSchema: GraphQLSchema
    get() {
        val normalized = replace("extend type Query", "type Query")
            .replace("extend type Mutation", "type Mutation")
            .replace("extend type Subscription", "type Subscription")
        val withQuery = if (Regex("(?m)^\\s*type\\s+Query\\b").containsMatchIn(normalized)) {
            normalized
        } else {
            "$normalized\ntype Query { propertyTestPlaceholder: Boolean }"
        }
        return parseTestSchema(withQuery)
    }
