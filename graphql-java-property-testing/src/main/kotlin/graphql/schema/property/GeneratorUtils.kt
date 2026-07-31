package graphql.schema.property

import graphql.Directives
import graphql.Scalars
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLScalarType
import io.kotest.property.Arb
import io.kotest.property.Gen
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.shuffle
import kotlin.math.max
import kotlin.math.min

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

/** Return true with probability [weight]. */
internal fun RandomSource.sampleWeight(weight: Double): Boolean =
    when (weight) {
        0.0 -> false
        1.0 -> true
        else -> random.nextDouble(0.0, 1.0) <= weight
    }

/** Return how many times [weight] sampled true before reaching its configured maximum. */
internal fun RandomSource.count(weight: CompoundingWeight): Int {
    tailrec fun loop(count: Int): Int =
        if (count == weight.max || !sampleWeight(weight.weight)) count else loop(count + 1)
    return loop(0)
}

/** Convert a generator into an infinite sequence using [randomSource]. */
internal fun <T> Gen<T>.asSequence(randomSource: RandomSource): Sequence<T> =
    generate(randomSource).map { it.value }

/**
 * Generate subsets of this set. The subset size is constrained by [range], or ranges from zero
 * through the input set size when no range is provided.
 */
internal fun <T> Set<T>.arbSubset(range: IntRange? = null): Arb<Set<T>> =
    Arb.constant(this).flatMap { values ->
        val first = min(max(range?.first ?: 0, 0), values.size)
        val last = min(max(range?.last ?: values.size, 0), values.size)
        Arb.pair(Arb.shuffle(values.toList()), Arb.int(first..last))
            .map { (shuffled, count) -> shuffled.take(count).toSet() }
    }

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
