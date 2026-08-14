@file:Suppress("ForbiddenImport")

package graphql.arbitrary

import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CycleGroupsTest : ArbPropertyBase() {
    @Test
    fun `Empty`(): Unit =
        runBlocking {
            Arb.string().forAll { CycleGroups.Empty[it].isEmpty() }
        }

    @Test
    fun `no input types`() {
        val schema = "type Foo { x: Int }".asPermissiveTestSchema
        val all = CycleGroups.allInputCycles(schema)
        val hard = CycleGroups.mandatoryInputCycles(schema)

        assertEquals(CycleGroups.Empty, all)
        assertEquals(CycleGroups.Empty, hard)
    }

    @Test
    fun `acyclic input types`() {
        val schema = """
            input A { x: Int }
            input B { a: A }
        """.asPermissiveTestSchema
        val all = CycleGroups.allInputCycles(schema)
        val hard = CycleGroups.mandatoryInputCycles(schema)

        assertEquals(CycleGroups.Empty, all)
        assertEquals(CycleGroups.Empty, hard)
    }

    @Test
    fun `nullable self-loop`() {
        val schema = "input Inp { inp: Inp }".asPermissiveTestSchema
        val all = CycleGroups.allInputCycles(schema)
        val hard = CycleGroups.mandatoryInputCycles(schema)

        assertEquals(mapOf("Inp" to setOf("Inp")), all.map)
        assertEquals(CycleGroups.Empty, hard)
    }

    @Test
    fun `nullable 2-type cycle`() {
        val schema = """
            input A { b: B }
            input B { a: A }
        """.asPermissiveTestSchema
        val all = CycleGroups.allInputCycles(schema)
        val hard = CycleGroups.mandatoryInputCycles(schema)

        assertEquals(mapOf("A" to setOf("A", "B"), "B" to setOf("A", "B")), all.map)
        assertEquals(CycleGroups.Empty, hard)
    }

    @Test
    fun `nullable 2-type cycle with observer`() {
        val schema = """
            input A { b: B }
            input B { a: A }
            input C { a: A }
        """.asPermissiveTestSchema
        val all = CycleGroups.allInputCycles(schema)
        val hard = CycleGroups.mandatoryInputCycles(schema)

        assertEquals(mapOf("A" to setOf("A", "B"), "B" to setOf("A", "B")), all.map)
        assertEquals(CycleGroups.Empty, hard)
    }

    @Test
    fun `non-nullable cycle with lists`() {
        val schema = """
            input A { b: [B!]! }
            input B { a: A! }
        """.asPermissiveTestSchema
        val all = CycleGroups.allInputCycles(schema)
        val hard = CycleGroups.mandatoryInputCycles(schema)

        assertEquals(mapOf("A" to setOf("A", "B"), "B" to setOf("A", "B")), all.map)
        assertEquals(CycleGroups.Empty, hard)
    }

    @Test
    fun `non-null recursion`() {
        // note that this type is schematically invalid
        val schema = "input Inp { inp: Inp! }".asPermissiveTestSchema
        val all = CycleGroups.allInputCycles(schema)
        val hard = CycleGroups.mandatoryInputCycles(schema)

        assertEquals(mapOf("Inp" to setOf("Inp")), all.map)
        assertEquals(mapOf("Inp" to setOf("Inp")), hard.map)
    }

    @Test
    fun `non-null co-recursion`() {
        // note that these types are schematically invalid
        val schema = """
            input A { b: B! }
            input B { a: A! }
        """.asPermissiveTestSchema
        val all = CycleGroups.allInputCycles(schema)
        val hard = CycleGroups.mandatoryInputCycles(schema)

        assertEquals(mapOf("A" to setOf("A", "B"), "B" to setOf("A", "B")), all.map)
        assertEquals(mapOf("A" to setOf("A", "B"), "B" to setOf("A", "B")), hard.map)
    }

    @Test
    fun `co-recursion with mixed nullability`() {
        val schema = """
            input A { b: B! }
            input B { a: A }
        """.asPermissiveTestSchema
        val all = CycleGroups.allInputCycles(schema)
        val hard = CycleGroups.mandatoryInputCycles(schema)

        assertEquals(mapOf("A" to setOf("A", "B"), "B" to setOf("A", "B")), all.map)
        assertEquals(CycleGroups.Empty, hard)
    }

    @Test
    fun `co-recursion with nullable oneof`() {
        val schema = """
            input A @oneOf { b: B }
            input B { a: A }
        """.asPermissiveTestSchema
        val all = CycleGroups.allInputCycles(schema)
        val hard = CycleGroups.mandatoryInputCycles(schema)

        assertEquals(mapOf("A" to setOf("A", "B"), "B" to setOf("A", "B")), all.map)
        assertEquals(CycleGroups.Empty, hard)
    }

    @Test
    fun `co-recursion with non-nullable oneof`() {
        val schema = """
            input A @oneOf { b:B, escape:Int }
            input B { a:A! }
        """.asPermissiveTestSchema
        val all = CycleGroups.allInputCycles(schema)
        val hard = CycleGroups.mandatoryInputCycles(schema)

        assertEquals(mapOf("A" to setOf("A", "B"), "B" to setOf("A", "B")), all.map)
        assertEquals(mapOf("A" to setOf("A", "B"), "B" to setOf("A", "B")), hard.map)
    }
}
