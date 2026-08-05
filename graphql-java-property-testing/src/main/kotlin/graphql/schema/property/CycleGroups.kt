package graphql.schema.property

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeUtil

/**
 * Represents groups of input object types that participate in recursive cycles.
 *
 * Types within a group are mutually reachable from each other through the edges
 * defined by the factory method that created this instance (e.g., [mandatoryInputCycles] or [allInputCycles]).
 * Each group represents a Strongly Connected Component (SCC) in the input object dependency graph.
 */
internal class CycleGroups(val map: Map<String, Set<String>>) {
    operator fun get(name: String): Set<String> = map[name].orEmpty()

    fun isEmpty(): Boolean = map.isEmpty()

    override fun toString(): String = map.toString()

    companion object {
        val Empty: CycleGroups = CycleGroups(emptyMap())

        /**
         * Returns a [CycleGroups] that identifies cycles formed by mandatory input edges.
         *
         * A mandatory edge represents a structural dependency that must be satisfied
         * to produce a valid input value. This includes:
         * - Non-nullable fields with no default value in a standard input object.
         * - All fields of a `@oneOf` input object (as the `@oneOf` constraint requires
         *   exactly one field to be selected).
         *
         * List-typed fields (even non-nullable ones) are not considered mandatory edges
         * because an empty list successfully satisfies the requirement without recursing.
         *
         * These edges define paths that a generator may be "forced" to follow to produce
         * a well-formed value. A cycle of mandatory edges is only valid in a GraphQL
         * schema if it contains at least one `@oneOf` input object that provides a
         * non-cyclic "exit" field (such as a scalar or an optional dependency) to
         * terminate the recursion.
         */
        fun mandatoryInputCycles(schema: GraphQLSchema): CycleGroups =
            build(schema) { inputObject ->
                inputObject.fields.mapNotNullTo(linkedSetOf()) { field ->
                    if (field.hasSetDefaultValue()) return@mapNotNullTo null
                    val unwrapped = GraphQLTypeUtil.unwrapAll(field.type)
                    if (unwrapped !is GraphQLInputObjectType) return@mapNotNullTo null
                    if (inputObject.isOneOf) return@mapNotNullTo unwrapped.name
                    val type = field.type
                    if (type is GraphQLNonNull && type.wrappedType !is GraphQLList) {
                        unwrapped.name
                    } else {
                        null
                    }
                }
            }

        /**
         * Returns a [CycleGroups] that identifies all recursive cycles between
         * input objects.
         *
         * This considers every reference between input objects as an edge, regardless
         * of nullability, list wrappers, or default values.
         */
        fun allInputCycles(schema: GraphQLSchema): CycleGroups =
            build(schema) { inputObject ->
                inputObject.fields.mapNotNullTo(linkedSetOf()) { field ->
                    (GraphQLTypeUtil.unwrapAll(field.type) as? GraphQLInputObjectType)?.name
                }
            }

        private fun build(
            schema: GraphQLSchema,
            neighbors: (GraphQLInputObjectType) -> Set<String>
        ): CycleGroups {
            val inputTypes = schema.allTypesAsList.filterIsInstance<GraphQLInputObjectType>()
            val adjacency = inputTypes.associate { it.name to neighbors(it) }
            val cycles = stronglyConnectedCycles(adjacency)
            return if (cycles.isEmpty()) Empty else CycleGroups(cycles)
        }

        private fun stronglyConnectedCycles(
            adjacency: Map<String, Set<String>>
        ): Map<String, Set<String>> {
            var nextIndex = 0
            val indices = mutableMapOf<String, Int>()
            val lowLinks = mutableMapOf<String, Int>()
            val stack = ArrayDeque<String>()
            val onStack = mutableSetOf<String>()
            val result = mutableMapOf<String, Set<String>>()

            fun connect(vertex: String) {
                indices[vertex] = nextIndex
                lowLinks[vertex] = nextIndex
                nextIndex++
                stack.addLast(vertex)
                onStack += vertex

                adjacency[vertex].orEmpty().forEach { neighbor ->
                    if (neighbor !in adjacency) return@forEach
                    if (neighbor !in indices) {
                        connect(neighbor)
                        lowLinks[vertex] = minOf(lowLinks.getValue(vertex), lowLinks.getValue(neighbor))
                    } else if (neighbor in onStack) {
                        lowLinks[vertex] = minOf(lowLinks.getValue(vertex), indices.getValue(neighbor))
                    }
                }

                if (lowLinks[vertex] != indices[vertex]) return
                val component = linkedSetOf<String>()
                do {
                    val item = stack.removeLast()
                    onStack -= item
                    component += item
                } while (item != vertex)

                val selfLoop = component.size == 1 && vertex in adjacency[vertex].orEmpty()
                if (component.size > 1 || selfLoop) {
                    component.forEach { result[it] = component }
                }
            }

            adjacency.keys.forEach { vertex ->
                if (vertex !in indices) connect(vertex)
            }
            return result
        }
    }
}
