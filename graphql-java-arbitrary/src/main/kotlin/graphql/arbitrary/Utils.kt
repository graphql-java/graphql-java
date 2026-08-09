package graphql.arbitrary

import graphql.Directives
import graphql.Scalars
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLScalarType
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.constant

internal val builtinScalars: Map<String, GraphQLScalarType> =
    listOf(
        Scalars.GraphQLBoolean,
        Scalars.GraphQLID,
        Scalars.GraphQLInt,
        Scalars.GraphQLFloat,
        Scalars.GraphQLString
    ).associateBy { it.name }

internal val builtinDirectives: Map<String, GraphQLDirective> =
    Directives.BUILT_IN_DIRECTIVES.associateBy { it.name }

/** Return an IntRange containing only this Int. */
fun Int.asIntRange(): IntRange = IntRange(this, this)

/** Return a LongRange containing only this Int. */
fun Int.asLongRange(): LongRange = toLong().asLongRange()

/** Return a LongRange containing only this Long. */
fun Long.asLongRange(): LongRange = LongRange(this, this)

/** Throw [ResolverException] if sampling the weight described by [key] returns true. */
internal fun maybeThrowResolverException(
    cfg: Config,
    key: ConfigKey<Double>,
    rs: RandomSource
) {
    if (rs.sampleWeight(cfg[key])) throw ResolverException(key)
}

class ResolverException(val key: ConfigKey<*>) : Exception() {
    override val message: String =
        "This is a synthetic ResolverException configured by ${key.javaClass.name}"
}

/**
 * Return an Arb<Set> describing subsets of this Set.
 * @see Arb<Set<T>>.subset
 */
fun <T> Set<T>.arbSubset(range: IntRange? = null): Arb<Set<T>> = Arb.constant(this).subset(range)

/**
 * Transform a Collection of Arb<T> into an Arb of List<T>.
 *
 * Example:
 *   val list = listOf(Arb.of(1), Arb.of(2), Arb.of(3))
 *   val items = list.collect().next(rs)   // listOf(1, 2, 3)
 */
fun <T> Collection<Arb<T>>.collect(): Arb<List<T>> = Arb.bind(toList()) { it }

/**
 * Select interfaces that may be implemented together without introducing unrelated fields with
 * the same name. Related interfaces may share inherited field definitions.
 */
internal fun Set<GraphQLInterfaceType>.nonConflicting(): Set<GraphQLInterfaceType> {
    val groups = relatedInterfaceGroups(this)
    val usedFields = mutableSetOf<String>()
    val result = linkedSetOf<GraphQLInterfaceType>()
    groups.forEach { group ->
        val fieldNames = group.flatMap { it.fields }.map { it.name }
        if (fieldNames.none(usedFields::contains)) {
            result += group.first()
            usedFields += fieldNames
        }
    }
    return result
}

private fun relatedInterfaceGroups(
    interfaces: Set<GraphQLInterfaceType>
): List<Set<GraphQLInterfaceType>> {
    val remaining = interfaces.toMutableSet()
    val groups = mutableListOf<Set<GraphQLInterfaceType>>()
    while (remaining.isNotEmpty()) {
        val seed = remaining.first()
        val group = relatedInterfaces(seed, interfaces)
        groups += group
        remaining -= group
    }
    return groups
}

private fun relatedInterfaces(
    seed: GraphQLInterfaceType,
    candidates: Set<GraphQLInterfaceType>
): Set<GraphQLInterfaceType> {
    val result = linkedSetOf(seed)
    var changed = true
    while (changed) {
        changed = false
        candidates.forEach { candidate ->
            if (candidate in result) return@forEach
            if (isRelated(candidate, result)) {
                result += candidate
                changed = true
            }
        }
    }
    return result
}

private fun isRelated(
    candidate: GraphQLInterfaceType,
    group: Set<GraphQLInterfaceType>
): Boolean {
    val candidateParents = candidate.interfaces.map { it.name }.toSet()
    if (group.any { it.name in candidateParents }) return true
    return group.any { existing ->
        candidate.name in existing.interfaces.map { it.name }
    }
}
