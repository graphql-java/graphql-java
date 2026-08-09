@file:Suppress("ForbiddenImport")

package graphql.arbitrary

import graphql.language.AstPrinter
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.Type
import graphql.language.TypeName
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class UtilTest : ArbPropertyBase() {
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
    fun collect(): Unit =
        runBlocking {
            listOf(Arb.int(), Arb.string(), Arb.char())
                .collect()
                .forAll { l ->
                    l[0] is Int && l[1] is String && l[2] is Char
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
    fun `maybeThrowResolverException`(): Unit =
        runBlocking {
            // does not throw
            val key = object : ConfigKey<Double>(0.0, WeightValidator) {}
            assertDoesNotThrow {
                maybeThrowResolverException(Config.default, key, randomSource)
            }

            // throws
            val err = assertThrows<ResolverException> {
                maybeThrowResolverException(Config(key(1.0)), key, randomSource)
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
