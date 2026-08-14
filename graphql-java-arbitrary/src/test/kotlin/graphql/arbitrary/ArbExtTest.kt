@file:Suppress("ForbiddenImport")
@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package graphql.arbitrary

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.intRange
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.take
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ArbExtTest : ArbPropertyBase() {
    @Test
    fun `Gen_asSequence`(): Unit =
        runBlocking {
            Arb
                .list(Arb.int(), 0..10)
                .map { list ->
                    val arb = Arb.constant(list)
                    val seq = arb.asSequence(RandomSource.default())
                    arb to seq
                }.forAll { (arb, seq) ->
                    seq.first() == arb.next(randomSource)
                }
        }

    @Test
    fun `Arb-set subset`(): Unit =
        runBlocking {
            Arb
                .set(Arb.int())
                .forAll { set ->
                    val subset = Arb.constant(set).subset().bind()
                    set.containsAll(subset)
                }
        }

    @Test
    fun `Arb-set subset with range`(): Unit =
        runBlocking {
            Arb
                .pair(
                    Arb.set(Arb.int()),
                    Arb.intRange(0 until Int.MAX_VALUE).nonEmpty()
                ).checkAll { (set, range) ->
                    val subset =
                        Arb
                            .constant(set)
                            .subset(range)
                            .bind()

                    if (range.first > set.size && subset.size != set.size) {
                        markFailure()
                    } else if (range.last > set.size && subset.size != set.size) {
                        markFailure()
                    } else {
                        markSuccess()
                    }
                }
        }

    @Test
    fun filterNotNull(): Unit =
        runBlocking {
            val range = 0..100
            Arb
                .int(range)
                .orNull()
                .filterNotNull()
                .forAll { it in range }
        }

    @Test
    fun zip(): Unit =
        runBlocking {
            Arb
                .pair(Arb.int(), Arb.string())
                .flatMap { (int, string) ->
                    Arb
                        .constant(int)
                        .zip(Arb.constant(string))
                        .map { pair -> pair to (int to string) }
                }.forAll { (pair1, pair2) ->
                    pair1 == pair2
                }
        }

    @Test
    fun `weightedChoose -- with fallback`(): Unit =
        runBlocking {
            val weighted = Arb.constant(true)
            val fallback = Arb.constant(false)

            Arb
                .element(setOf(0.0, 1.0))
                .flatMap { weight ->
                    val arb = Arb.weightedChoose(weight to weighted, fallback)
                    arb.zip(Arb.constant(weight))
                }.forAll { (choseWeighted, weight) ->
                    (weight == 1.0) == choseWeighted
                }
        }

    @Test
    fun `weightedChoose -- list`(): Unit =
        runBlocking {
            // test that weights can sum to over 1.0 and that 0.0 weights are never chosen
            val arb = Arb.weightedChoose(
                listOf(
                    0.0 to Arb.constant(0),
                    1.0 to Arb.constant(1),
                    1.0 to Arb.constant(2),
                    0.0 to Arb.constant(3),
                )
            )

            arb.forAll { it == 1 || it == 2 }
        }

    @Test
    fun `weightedChoose -- empty list -- empty`() {
        assertThrows<IllegalArgumentException> {
            Arb.weightedChoose<Unit>(emptyList())
        }
        assertThrows<IllegalArgumentException> {
            Arb.weightedChoose(listOf(0.0 to Arb.unit()))
        }
    }

    @Test
    fun `weightedChoose -- singleton list`() {
        val arb = Arb.unit()
        assertEquals(arb, Arb.weightedChoose(listOf(0.1 to arb)))
    }

    @Test
    fun `Arb unit`(): Unit = runBlocking { Arb.unit().forAll { it == Unit } }

    @Test
    fun `RandomSource sampleWeight`(): Unit =
        runBlocking {
            // always true
            arbitrary { rs -> rs.sampleWeight(1.0) }
                .forAll { it }

            // always false
            arbitrary { rs -> rs.sampleWeight(0.0) }
                .forNone { it }
        }

    @Test
    fun `RandomSource count`(): Unit =
        runBlocking {
            arbitrary { rs -> rs.count(CompoundingWeight.Never) }
                .forAll { it == 0 }

            Arb.int(0..100).forAll { i ->
                val count = randomSource.count(CompoundingWeight(1.0, i))
                count == i
            }
        }

    @Test
    fun `RandomSource fork`() {
        val seed = 123L
        val expectedSeed = RandomSource.seeded(seed).random.nextLong()

        assertEquals(expectedSeed, RandomSource.seeded(seed).fork().seed)
    }

    @Test
    fun `maybeDelay`(): Unit =
        runBlocking {
            // no delay
            run {
                val time = measureTime {
                    randomSource.maybeDelay(0.asLongRange())
                }
                // pick a number greater than 0 to accommodate slow CI machines
                assertTrue(time < 50.milliseconds)
            }

            // delay
            run {
                val time = measureTime {
                    randomSource.maybeDelay(100.asLongRange())
                }
                assertTrue(time >= 100.milliseconds)
            }
        }

    @Test
    fun `Arb_flatten`(): Unit =
        runBlocking {
            val chunkSize = 10
            val arb = Arb
                .int(0..10)
                .map { i ->
                    Arb
                        .char('a'..'z')
                        .map { c -> i to c }
                        .take(chunkSize, randomSource)
                        .toList()
                }.flatten()

            arb
                .take(chunkSize * 1_000, randomSource)
                .chunked(chunkSize)
                .forEach { chunk ->
                    val firsts = chunk.map { it.first }
                    assertEquals(1, firsts.distinct().size)
                }
        }

    @Test
    fun `Arb_flatten -- edge case delegates to underlying`() {
        val arb = Arb.constant(listOf(1, 2, 3)).flatten()
        val edge = arb.edgecase(RandomSource.seeded(0))
        // edgecase returns first element from underlying's edgecase iterator
        assertTrue(edge != null && edge.value in listOf(1, 2, 3))
    }

    @Test
    fun `Arb_mapNotNull`(): Unit =
        runBlocking {
            Arb.int(0..100)
                .mapNotNull {
                    it.takeIf { it % 2 == 0 }?.toString()
                }.forAll {
                    (it.toInt() % 2 == 0)
                }
        }

    @Test
    fun `Any_failProperty -- failure message includes seed when provided`() {
        // no seed value is provided
        assertThrows<AssertionError> {
            failProperty("msg")
        }.let {
            assertTrue(it.message?.contains("Property failed") ?: false)
            assertTrue(it.message?.contains("msg") ?: false)
        }

        // provide a seed value
        val explicitSeed = UUID.randomUUID().leastSignificantBits
        assertThrows<AssertionError> {
            failProperty("msg", seed = explicitSeed)
        }.let {
            assertTrue(it.message?.contains(explicitSeed.toString()) ?: false)
        }
    }

    @Test
    fun `Arb_flatten -- items within a chunk are consecutive`() {
        val arb = Arb.of(listOf(1, 1), listOf(2, 2)).flatten()
        val values = arb.asSequence(randomSource).take(4).toList()
        // each pair of consecutive items must come from the same chunk
        assertEquals(values[0], values[1])
        assertEquals(values[2], values[3])
    }

    @Test
    fun `Arb_flatten -- skips empty chunks`() {
        // Alternates between empty and non-empty lists; flatten must not throw
        val arb = Arb.of(emptyList(), listOf("a"), emptyList(), listOf("b")).flatten()
        val values = arb.asSequence(randomSource).take(10).toList()
        assertTrue(values.all { it == "a" || it == "b" })
    }

    @Test
    fun `mapNotNull drops nulls`() {
        val arb = Arb.of(1, 2, 3, 4, 5).mapNotNull { if (it % 2 == 0) it else null }
        val values = arb.asSequence(randomSource).take(100).toSet()
        assertEquals(setOf(2, 4), values)
    }
}
