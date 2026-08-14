package graphql.arbitrary

import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLUnionType

// JMB TODO: maybe bring over full type relations
/** Computes the fragment-spread relationships between composite schema types. */
internal class TypeRelations(private val schema: GraphQLSchema) {
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
