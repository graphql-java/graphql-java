package graphql.arbitrary

import graphql.introspection.Introspection.DirectiveLocation
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLSchema

/** A cache of relationships used while generating documents for a GraphQL schema. */
internal interface Schemas {
    val schema: GraphQLSchema
    val rels: TypeRelations
    val directivesByLocation: Map<DirectiveLocation, Set<GraphQLDirective>>

    private class Impl(override val schema: GraphQLSchema) : Schemas {
        override val rels = TypeRelations(schema)

        override val directivesByLocation: Map<DirectiveLocation, Set<GraphQLDirective>> =
            schema.directives
                .flatMap { directive ->
                    directive.validLocations().map { location -> directive to location }
                }
                .groupBy({ it.second }, { it.first })
                .mapValues { (_, directives) -> directives.toSet() }
    }

    companion object {
        /** create a Schemas from the provided [schema] */
        operator fun invoke(schema: GraphQLSchema): Schemas = Impl(schema)
    }
}
