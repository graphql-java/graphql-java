package graphql.schema.property

import graphql.introspection.Introspection.DirectiveLocation
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLUnionType

/** A cache of relationships used while generating documents for a GraphQL schema. */
internal interface Schemas {
    val schema: GraphQLSchema
    val rels: SchemaRelationships
    val directivesByLocation: Map<DirectiveLocation, Set<GraphQLDirective>>

    companion object {
        operator fun invoke(schema: GraphQLSchema): Schemas = DefaultSchemas(schema)
    }
}

private class DefaultSchemas(
    override val schema: GraphQLSchema
) : Schemas {
    override val rels = SchemaRelationships(schema)

    override val directivesByLocation: Map<DirectiveLocation, Set<GraphQLDirective>> =
        schema.directives
            .flatMap { directive ->
                directive.validLocations().map { location -> directive to location }
            }
            .groupBy({ it.second }, { it.first })
            .mapValues { (_, directives) -> directives.toSet() }
}

/** Computes the fragment-spread relationships between composite schema types. */
internal class SchemaRelationships(private val schema: GraphQLSchema) {
    private val compositeTypes = schema.allTypesAsList.filterIsInstance<GraphQLCompositeType>()

    /** Return the types that may be used as a type condition within [parentType]. */
    fun spreadableTypes(parentType: GraphQLCompositeType): Set<GraphQLCompositeType> =
        compositeTypes.filterTo(linkedSetOf()) { candidate ->
            isSpreadable(parentType, candidate)
        }

    /**
     * Return whether [fragmentType] may be spread within [parentType], as defined by the
     * Possible Fragment Spreads validation rule.
     */
    fun isSpreadable(
        parentType: GraphQLCompositeType,
        fragmentType: GraphQLCompositeType
    ): Boolean {
        if (parentType == fragmentType) return true
        return possibleObjectTypes(parentType).any(possibleObjectTypes(fragmentType)::contains)
    }

    /** Return every concrete object type that may represent [type] at execution time. */
    fun possibleObjectTypes(type: GraphQLCompositeType): Set<GraphQLObjectType> =
        when (type) {
            is GraphQLObjectType -> setOf(type)
            is GraphQLInterfaceType -> schema.getImplementations(type).orEmpty().toSet()
            is GraphQLUnionType -> type.types.filterIsInstance<GraphQLObjectType>().toSet()
            else -> emptySet()
        }
}
