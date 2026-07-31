package graphql.schema.property

import graphql.ExecutionInput
import graphql.parser.Parser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class PropertyTestUtilsTest : FunSpec({
    test("flatten preserves order within each generated iterable") {
        val values = Arb.constant(listOf(1, 2, 3)).flatten()
            .asSequence(RandomSource.seeded(1))
            .take(9)
            .toList()
        values.shouldBe(listOf(1, 2, 3, 1, 2, 3, 1, 2, 3))
    }

    test("mapNotNull drops null transformations") {
        val values = Arb.of(-2, -1, 0, 1, 2)
            .mapNotNull { value -> value.takeIf { it > 0 } }
            .asSequence(RandomSource.seeded(2))
            .take(30)
            .toList()
        values.all { it > 0 }.shouldBe(true)
    }

    test("failProperty includes its seed and cause") {
        val cause = IllegalStateException("cause")
        val error = shouldThrow<AssertionError> { failProperty("details", cause, 42) }
        error.message.shouldContain("Property failed with seed 42")
        error.message.shouldContain("details")
        error.cause.shouldBe(cause)
    }

    test("minViolation returns a failing generated value") {
        val output = PrintStream(ByteArrayOutputStream())
        val violation = Arb.constant(7)
            .withCheck<Int> { error("failure") }
            .minViolation(Comparator.naturalOrder(), RandomSource.seeded(9), iter = 5, out = output)
        violation?.value.shouldBe(7)
        violation?.seed.shouldBe(9)
    }

    test("seedMarch reports the first deterministic failing seed") {
        val output = PrintStream(ByteArrayOutputStream())
        val violation = Arb.constant("value")
            .withCheck<String> { error("failure") }
            .seedMarch(startingSeed = 15, maxIter = 2, printEvery = 0, out = output)
        violation?.value.shouldBe("value")
        violation?.seed.shouldBe(15)
    }

    test("document and execution input comparators count AST descendants") {
        val small = Parser().parseDocument("{ x }")
        val large = Parser().parseDocument("{ x { y z } }")
        (DocumentComparator.compare(small, large) < 0).shouldBe(true)

        val smallInput = ExecutionInput.newExecutionInput().query("{ x }").build()
        val largeInput = ExecutionInput.newExecutionInput().query("{ x { y z } }").build()
        (ExecutionInputComparator.compare(smallInput, largeInput) < 0).shouldBe(true)
    }
})
