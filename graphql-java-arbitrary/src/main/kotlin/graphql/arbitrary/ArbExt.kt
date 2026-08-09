@file:Suppress("ForbiddenImport")

package graphql.arbitrary

import io.kotest.property.Arb
import io.kotest.property.Gen
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choose
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
import io.kotest.property.asSample
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

/** Convert an arb to an infinite [kotlin.sequences.Sequence] */
fun <T> Gen<T>.asSequence(rs: RandomSource): Sequence<T> = generate(rs).map { it.value }

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

fun RandomSource.fork(): RandomSource = RandomSource.seeded(random.nextLong())

/**
 * Flatten this Arb into an Arb of the inner item type.
 * The new Arb will return items in the same order as produced by the original Arb.
 *
 * The underlying Arb must eventually produce a non-empty Iterable; if every sample is empty,
 * [Arb.sample] will loop indefinitely.
 */
fun <T> Arb<Iterable<T>>.flatten(): Arb<T> = Flatten(map { it.iterator() })

/** transform this Arb using [fn], dropping any null values returned by [fn] */
@JvmName("mapNotNull")
fun <T, R> Arb<T>.mapNotNull(fn: (T) -> R?): Arb<R> = map(fn).filter { it != null }.map { it!! }

internal class Flatten<T>(
    val underlying: Arb<Iterator<T>>,
) : Arb<T>() {
    private var chunk: Iterator<T>? = null

    override fun edgecase(rs: RandomSource): Sample<T>? {
        // Don't interrupt an active chunk — items within a chunk must remain consecutive.
        if (chunk?.hasNext() == true) return null
        val iter = underlying.edgecase(rs)?.value ?: return null
        if (!iter.hasNext()) return null
        chunk = iter
        return chunk!!.next().asSample()
    }

    override fun sample(rs: RandomSource): Sample<T> {
        while (chunk == null || chunk?.hasNext() == false) {
            chunk = underlying.sample(rs).value
        }
        return chunk!!.next().asSample()
    }
}

/**
 * Throw a property check failure.
 * The [seed] parameter is included in the error message for reproducibility.
 */
fun failProperty(
    message: String,
    cause: Throwable? = null,
    seed: Long? = null
): Unit =
    throw AssertionError(
        buildString {
            if (seed != null) {
                appendLine("Property failed with seed $seed")
            } else {
                appendLine("Property failed")
            }
            append(message)
        },
        cause
    )
