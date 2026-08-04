package graphql.schema.property

import graphql.Directives
import graphql.Scalars
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLScalarType
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.shuffle
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

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

/**
 * Filter an Arb<IntRange> to only yield non-empty IntRange values.
 * This is different from [Arb.Companion.intRange], which can yield empty
 * IntRange values.
 */
fun Arb<IntRange>.nonEmpty(): Arb<IntRange> = filterNot { it.isEmpty() }

/** Return a new Arb containing only non-null values. */
@Suppress("UNCHECKED_CAST")
fun <T> Arb<T?>.filterNotNull(): Arb<T> = filter { it != null }.map { it as T }

/** Generate an Arb that zips values of this arb with the values of another Arb. */
fun <T, U> Arb<T>.zip(other: Arb<U>): Arb<Pair<T, U>> = Arb.bind(this, other) { t, u -> t to u }

/**
 * Get a boolean from this random source, with probability of a true
 * value being equal to the provided weight.
 */
fun RandomSource.sampleWeight(weight: Double): Boolean =
    when (weight) {
        0.0 -> false
        1.0 -> true
        else -> random.nextDouble(0.0, 1.0) <= weight
    }

/**
 * Return an integer describing how many times the provided [CompoundingWeight]
 * was sampled before it hit its `max` sample count or returned false.
 */
fun RandomSource.count(weight: CompoundingWeight): Int {
    tailrec fun loop(count: Int): Int =
        if (count == weight.max || !sampleWeight(weight.weight)) count else loop(count + 1)
    return loop(0)
}

/** Suspend for a value of milliseconds bounded by [latencyMillis]. */
internal suspend fun RandomSource.maybeDelay(latencyMillis: LongRange) {
    if (latencyMillis.last > 0) delay(Arb.long(latencyMillis).next(this))
}

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

fun RandomSource.fork(): RandomSource = RandomSource.seeded(random.nextLong())

/**
 * Return subsets of a given arb. Subsets will have a size determined by the provided
 * range, or if no range is provided, then subsets will contain between 0 and size-of-the-input-set
 * elements.
 */
fun <T> Arb<Set<T>>.subset(range: IntRange? = null): Arb<Set<T>> =
    flatMap { values ->
        val first = min(max(range?.first ?: 0, 0), values.size)
        val last = min(max(range?.last ?: values.size, 0), values.size)
        Arb.pair(Arb.shuffle(values.toList()), Arb.int(first..last))
            .map { (shuffled, count) -> shuffled.take(count).toSet() }
    }

/**
 * Return an Arb<Set> describing subsets of this Set.
 * @see Arb<Set<T>>.subset
 */
fun <T> Set<T>.arbSubset(range: IntRange? = null): Arb<Set<T>> = Arb.constant(this).subset(range)

/**
 * This method is a replacement for [Arb.Companion.choose]. This method will
 * never pick an Arb with a 0 weight. [Arb.Companion.choose] can, via edge cases, select an Arb
 * that is assigned a weight of 0.
 */
fun <T> Arb.Companion.weightedChoose(
    weightedArb: Pair<Double, Arb<T>>,
    fallbackArb: Arb<T>
): Arb<T> {
    val weight = weightedArb.first.also {
        WeightValidator(it)?.let { message -> throw IllegalArgumentException(message) }
    }
    return when (weight) {
        0.0 -> fallbackArb
        1.0 -> weightedArb.second
        else -> {
            val intWeight = (weight * 1_000).toInt()
            Arb.choose(intWeight to weightedArb.second, (1_000 - intWeight) to fallbackArb)
        }
    }
}

/**
 * [Arb.Companion.choose] can, via edge cases, select an Arb that is assigned a weight of 0.
 * This method is a replacement for [Arb.Companion.choose] that will never pick an Arb with a 0 weight.
 */
fun <T> Arb.Companion.weightedChoose(arbs: List<Pair<Double, Arb<T>>>): Arb<T> {
    val weightedArbs = arbs
        .filter { (weight, _) -> weight > 0.0 }
        .map { (weight, arb) -> (weight * 1_000).toInt() to arb }
    require(weightedArbs.isNotEmpty())
    return if (weightedArbs.size == 1) {
        weightedArbs.first().second
    } else {
        Arb.choose(weightedArbs[0], weightedArbs[1], *weightedArbs.drop(2).toTypedArray())
    }
}

/** A unit arb, that always returns Unit. */
fun Arb.Companion.unit(): Arb<Unit> = Arb.of(Unit)

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
