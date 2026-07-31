@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll

class GeneratorUtilsTest : FunSpec({
    test("sampleWeight handles deterministic boundary weights") {
        checkAll(PropTestConfig(iterations = 100), arbitrary { randomSource -> randomSource.sampleWeight(1.0) }) {
            it.shouldBe(true)
        }
        checkAll(PropTestConfig(iterations = 100), arbitrary { randomSource -> randomSource.sampleWeight(0.0) }) {
            it.shouldBe(false)
        }
    }

    test("count observes Never, Once, and configured maxima") {
        checkAll(PropTestConfig(iterations = 100), Arb.int(0..100), Arb.int()) { maximum, seed ->
            val randomSource = RandomSource.seeded(seed.toLong())
            randomSource.count(CompoundingWeight.Never).shouldBe(0)
            randomSource.count(CompoundingWeight.Once).shouldBe(1)
            randomSource.count(CompoundingWeight(1.0, maximum)).shouldBe(maximum)
        }
    }

    test("arbSubset returns only source elements and clamps its size range") {
        checkAll(PropTestConfig(iterations = 100), Arb.set(Arb.int(), 0..30)) { values ->
            val subset = values.arbSubset().bind()
            values.containsAll(subset).shouldBe(true)
            values.arbSubset(values.size..values.size).bind().shouldBe(values)
            values.arbSubset((values.size + 1)..(values.size + 10)).bind().shouldBe(values)
        }
    }

    test("schema and AST type wrappers round trip") {
        val schema = parseTestSchema(
            "type Query { a: Int, b: [[Int]], c: [[Int!]!]! }"
        )
        val types = listOf("a", "b", "c").map { name -> schema.queryType.getFieldDefinition(name).type }
        types.forEach { type ->
            val ast = type.asAstType()
            sameType(type, ast.asSchemaType(schema)).shouldBe(true)
        }
    }

    test("nonConflicting keeps related interfaces together and rejects unrelated field collisions") {
        val schema = parseTestSchema(
            """
                interface Parent { x: Int }
                interface Child implements Parent { x: Int, y: Int }
                interface Conflict { x: String }
                interface Independent { z: Int }
                type Obj implements Parent & Child { x: Int, y: Int }
                type Query { obj: Obj }
            """.trimIndent()
        )
        val interfaces = setOf("Parent", "Child", "Conflict", "Independent")
            .mapTo(linkedSetOf()) { schema.getType(it) as GraphQLInterfaceType }
        val selected = interfaces.nonConflicting()
        selected.map { it.name }.contains("Independent").shouldBe(true)
        (selected.map { it.name }.intersect(setOf("Parent", "Child", "Conflict")).size == 1).shouldBe(true)
    }
})

private fun sameType(first: GraphQLType, second: GraphQLType): Boolean =
    first.toString() == second.toString()
