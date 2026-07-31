package graphql.schema.property

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class CycleGroupsTest : FunSpec({
    test("schemas without recursive input types have no cycle groups") {
        val schemas = listOf(
            "type Query { x: Int }",
            "input A { x: Int } input B { a: A } type Query { x(input: B): Int }"
        )
        schemas.forEach { sdl ->
            val schema = parseTestSchema(sdl)
            CycleGroups.allInputCycles(schema).isEmpty().shouldBe(true)
            CycleGroups.mandatoryInputCycles(schema).isEmpty().shouldBe(true)
        }
    }

    test("nullable self loops are optional cycles") {
        val schema = parseTestSchema("input Inp { inp: Inp } type Query { x(inp: Inp): Int }")
        CycleGroups.allInputCycles(schema)["Inp"].shouldBe(setOf("Inp"))
        CycleGroups.mandatoryInputCycles(schema)["Inp"].shouldBeEmpty()
    }

    test("nullable mutually recursive types share a cycle group") {
        val schema = parseTestSchema(
            "input A { b: B } input B { a: A } input C { a: A } type Query { x(c: C): Int }"
        )
        val all = CycleGroups.allInputCycles(schema)
        all["A"].shouldBe(setOf("A", "B"))
        all["B"].shouldBe(setOf("A", "B"))
        all["C"].shouldBeEmpty()
        CycleGroups.mandatoryInputCycles(schema).isEmpty().shouldBe(true)
    }

    test("non-null lists break mandatory recursion") {
        val schema = parseTestSchema(
            "input A { b: [B!]! } input B { a: A! } type Query { x(a: A): Int }"
        )
        CycleGroups.allInputCycles(schema)["A"].shouldBe(setOf("A", "B"))
        CycleGroups.mandatoryInputCycles(schema).isEmpty().shouldBe(true)
    }

    test("mixed-nullability recursion is not mandatory") {
        val schema = parseTestSchema(
            "input A { b: B! } input B { a: A } type Query { x(a: A): Int }"
        )
        CycleGroups.allInputCycles(schema)["A"].shouldBe(setOf("A", "B"))
        CycleGroups.mandatoryInputCycles(schema).isEmpty().shouldBe(true)
    }

    test("nullable oneOf recursion is optional") {
        val schema = parseTestSchema(
            "input A @oneOf { b: B } input B { a: A } type Query { x(a: A): Int }"
        )
        CycleGroups.allInputCycles(schema)["A"].shouldBe(setOf("A", "B"))
        CycleGroups.mandatoryInputCycles(schema).isEmpty().shouldBe(true)
    }

    test("oneOf fields participate in mandatory cycles when their host is required") {
        val schema = parseTestSchema(
            "input A @oneOf { b: B, escape: Int } input B { a: A! } type Query { x(a: A): Int }"
        )
        val mandatory = CycleGroups.mandatoryInputCycles(schema)
        mandatory["A"].shouldBe(setOf("A", "B"))
        mandatory["B"].shouldBe(setOf("A", "B"))
    }
})
