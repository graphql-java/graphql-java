@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import graphql.ParseAndValidate
import graphql.parser.Parser
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.constant
import io.kotest.property.checkAll

class GraphQLExecutionInputsTest : FunSpec({
    val schema = parseTestSchema(
        """
            scalar Custom
            input Input { a: Int, b: [Int!]!, c: Nested }
            input Nested { value: String }
            type Query { x(a: Int!): Int, custom(value: Custom!): Int, input(value: Input!): Int }
        """.trimIndent()
    )

    test("AnonymousOperationWeight omits only the unambiguous operation name") {
        val scalarOverride = ScalarValueOverrides to mapOf("Custom" to Arb.constant("custom-value"))
        val oneOperation = Config.default + scalarOverride +
            (OperationCount to 1..1) +
            (AnonymousOperationWeight to 1.0)
        checkAll(PropTestConfig(iterations = 50), Arb.graphQLExecutionInput(schema, oneOperation)) { input ->
            input.operationName.shouldBe(null)
        }

        val twoOperations = Config.default + scalarOverride +
            (OperationCount to 2..2) +
            (AnonymousOperationWeight to 1.0)
        checkAll(PropTestConfig(iterations = 50), Arb.graphQLExecutionInput(schema, twoOperations)) { input ->
            (input.operationName != null).shouldBe(true)
        }
    }

    test("ImplicitNullValueWeight omits only variables that may be omitted") {
        val document = Parser().parseDocument(
            "query Q(\$a: Boolean, \$b: Boolean = false, \$c: Boolean!, \$d: Boolean! = false) { x(a: 1) }"
        )
        val omitted = Config.default + (ImplicitNullValueWeight to 1.0)
        checkAll(PropTestConfig(iterations = 50), Arb.graphQLExecutionInput(schema, document, omitted)) { input ->
            input.variables.keys.shouldBe(setOf("c"))
        }

        val retained = Config.default + (ImplicitNullValueWeight to 0.0)
        checkAll(PropTestConfig(iterations = 50), Arb.graphQLExecutionInput(schema, document, retained)) { input ->
            input.variables.keys.shouldBe(setOf("a", "b", "c", "d"))
        }
    }

    test("custom scalar external values use ScalarValueOverrides") {
        val document = Parser().parseDocument("query Q(\$value: Custom!) { custom(value: \$value) }")
        val config = Config.default +
            (ScalarValueOverrides to mapOf("Custom" to Arb.constant("custom-value")))
        checkAll(PropTestConfig(iterations = 20), Arb.graphQLExecutionInput(schema, document, config)) { input ->
            input.variables.shouldBe(mapOf("value" to "custom-value"))
        }
    }

    test("generated ExecutionInputs parse and validate") {
        val config = Config.default +
            (ScalarValueOverrides to mapOf("Custom" to Arb.constant("custom-value")))
        checkAll(PropTestConfig(iterations = 200), Arb.graphQLExecutionInput(schema, config)) { input ->
            ParseAndValidate.parseAndValidate(schema, input).errors.shouldBeEmpty()
        }
    }
})
