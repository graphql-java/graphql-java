@file:Suppress("ForbiddenImport")
@file:OptIn(ExperimentalTime::class)

package graphql.schema.property

import graphql.ExecutionInput
import graphql.language.AstPrinter
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.Type
import graphql.language.TypeName
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.intRange
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.kotest.property.forAll
import io.kotest.property.forNone
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class UtilTest : KotestPropertyBase() {
    @Test
    fun `Int asIntRange`(): Unit =
        runBlocking {
            Arb.int().forAll { i ->
                val range = i.asIntRange()
                val isEmpty = range.isEmpty()
                val containsI = range.contains(i)
                val checkLo = if (i > Int.MIN_VALUE) !range.contains(i - 1) else true
                val checkHi = if (i < Int.MAX_VALUE) !range.contains(i + 1) else true

                !isEmpty && checkLo && containsI && checkHi
            }
        }

    @Test
    fun `Int asLongRange`(): Unit =
        runBlocking {
            Arb.int().forAll { i ->
                val range = i.asLongRange()
                val isEmpty = range.isEmpty()
                val containsI = range.contains(i)
                val checkLo = !range.contains(i.toLong() - 1)
                val checkHi = !range.contains(i.toLong() + 1)

                !isEmpty && checkLo && containsI && checkHi
            }
        }

    @Test
    fun `Long asLongRange()`(): Unit =
        runBlocking {
            Arb.long().forAll { l ->
                val range = l.asLongRange()
                val isEmpty = range.isEmpty()
                val containsL = range.contains(l)
                val checkLo = if (l > Long.MIN_VALUE) !range.contains(l - 1) else true
                val checkHi = if (l < Long.MAX_VALUE) !range.contains(l + 1) else true

                !isEmpty && checkLo && containsL && checkHi
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
    fun `Set arbSubset`(): Unit =
        runBlocking {
            // without range
            Arb
                .set(Arb.int())
                .forAll { set ->
                    val subset = set.arbSubset().bind()
                    set.containsAll(subset)
                }

            // with range
            Arb
                .set(Arb.int())
                .forAll { set ->
                    val subset = set.arbSubset(set.size.asIntRange()).bind()
                    set == subset
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
    fun collect(): Unit =
        runBlocking {
            listOf(Arb.int(), Arb.string(), Arb.char())
                .collect()
                .forAll { l ->
                    l[0] is Int && l[1] is String && l[2] is Char
                }
        }

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
    fun `GraphQLTypes can be roundtripped through Type`() {
        val sdl = """
            type Query {
              a:Int
              b:Enum
              c:[[Int]]
              d:[[Int!]!]!
              e: U
              f(inp:Inp!):Int
            }
            union U = Query
            enum Enum { A, B }
            input Inp {
                x:Int!
                inp:Inp
            }
        """.trimIndent()
        val doc = sdl.asDocument
        val schema = sdl.asSchema

        val types = doc.allChildrenOfType<Type<*>>()
        // sanity
        assertTrue(types.isNotEmpty())

        types.forEach { t1 ->
            val t2 = t1.asSchemaType(schema).asAstType()
            assertTypesEqual(t1, t2)
        }
    }

    @Test
    fun `DocumentComparator and ExecutionInputComparator -- sorts documents by node count`() {
        val q1 = "{ someVeryLongFieldName }"
        val q2 = "{ x { y } }"

        // DocumentComparator
        let {
            val d1 = q1.asDocument
            val d2 = q2.asDocument
            assertEquals(-1, DocumentComparator.compare(d1, d2))
        }

        // ExecutionInputComparator
        let {
            val e1 = ExecutionInput.newExecutionInput(q1).build()
            val e2 = ExecutionInput.newExecutionInput(q2).build()
            assertEquals(-1, ExecutionInputComparator.compare(e1, e2))
        }
    }

    @Test
    fun `String asSchema`() {
        val schema = "type Query { x:Int }".asSchema
        assertNotNull(schema.queryType.getField("x"))
    }

    @Test
    fun `String asDocument`() {
        val sdl = "type Query {x: Int}"
        assertEquals(sdl, AstPrinter.printAstCompact(sdl.asDocument))
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
    fun `maybeThrowResolverException`(): Unit =
        runBlocking {
            // does not throw
            val key = object : ConfigKey<Double>(0.0, WeightValidator) {}
            assertDoesNotThrow {
                maybeThrowResolverException(Config.default, key, randomSource)
            }

            // throws
            val err = assertThrows<ResolverException> {
                maybeThrowResolverException(Config.default + (key to 1.0), key, randomSource)
            }
            assertEquals(key, err.key)
            assertTrue(key.javaClass.name in err.message)
        }
}
private fun assertTypesEqual(
    t1: Type<*>,
    t2: Type<*>
) {
    when (t1) {
        is TypeName -> {
            t2.shouldBeInstanceOf<TypeName>()
            assertEquals(t1.name, t2.name)
        }
        is NonNullType -> {
            t2.shouldBeInstanceOf<NonNullType>()
            assertTypesEqual(t1.type, t2.type)
        }
        is ListType -> {
            t2.shouldBeInstanceOf<ListType>()
            assertTypesEqual(t1.type, t2.type)
        }
        else -> throw IllegalArgumentException("unknown Type: $t1")
    }
}
