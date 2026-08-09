@file:Suppress("ForbiddenImport")

package graphql.arbitrary

import graphql.ExecutionInput
import graphql.ParseAndValidate
import io.kotest.property.Arb
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GraphQLExecutionInputGenTest : ArbPropertyBase() {
    private val schema =
        """
            directive @dir(arg:Int!) repeatable on QUERY | MUTATION | SUBSCRIPTION | FIELD | FRAGMENT_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT | VARIABLE_DEFINITION
            input Input { a:Int, b:[Int!]!, c:[[[Int]]] }
            type Foo implements I1 { x:Int, next:Union, i1(inp:Input!):I1! }
            type Bar implements I1 & I2 { x:Int, y:Int, next:Union }
            union Union = Foo | Bar
            interface I1 { x:Int }
            interface I2 { y:Int }
            extend type Mutation { x:Int, y:Int }
            extend type Subscription { x:Int, y:Int }
            extend type Query { x(a:Int!):Int, u:Union, y(inp:Input!): Union }
        """.asPermissiveTestSchema

    private fun mkConfig(
        anonymousOperationWeight: Double = 0.0,
        operationCount: Int = 1,
        implicitNullValueWeight: Double = 0.0,
    ): Config =
        Config(
            AnonymousOperationWeight(anonymousOperationWeight),
            ImplicitNullValueWeight(implicitNullValueWeight),
            OperationCount(operationCount.asIntRange())
        )

    @Test
    fun AnonymousOperationWeight(): Unit =
        runBlocking {
            // disabled
            mkConfig(anonymousOperationWeight = 0.0, operationCount = 1).let { cfg ->
                Arb.graphQLExecutionInput(schema, cfg).forAll {
                    it.operationName != null
                }
            }

            // enabled, but operationCount > 1
            mkConfig(anonymousOperationWeight = 1.0, operationCount = 2).let { cfg ->
                Arb.graphQLExecutionInput(schema, cfg).forAll {
                    it.operationName != null
                }
            }

            // enabled, operationCount = 1
            mkConfig(anonymousOperationWeight = 1.0, operationCount = 1).let { cfg ->
                Arb.graphQLExecutionInput(schema, cfg).forAll {
                    it.operationName == null
                }
            }
        }

    @Test
    fun ImplicitNullValueWeight(): Unit =
        runBlocking {
            // Variables:
            //   a: nullable without default
            //   b: nullable with default
            //   c: non-nullable without default
            //   d: non-nullable with default
            val doc = """
                directive @dir(x:Boolean) on FIELD
                query Q(${'$'}a:Boolean, ${'$'}b:Boolean=false, ${'$'}c:Boolean!, ${'$'}d:Boolean!=false) {
                    x @dir(x:${'$'}a)
                    x @dir(x:${'$'}b)
                    x @dir(x:${'$'}c)
                    x @dir(x:${'$'}d)
                }
            """.trimIndent().asDocument

            // disabled: no implicit nulls means every variable will have a value
            Arb.graphQLExecutionInput(schema, doc, mkConfig(implicitNullValueWeight = 0.0)).forAll { ei ->
                ei.variables.keys == setOf("a", "b", "c", "d")
            }

            // enabled: only variables that are non-nullable without a default value will have a value
            Arb.graphQLExecutionInput(schema, doc, mkConfig(implicitNullValueWeight = 1.0)).forAll { ei ->
                ei.variables.keys == setOf("c")
            }
        }

    @Test
    fun `generates valid ExecutionInputs with a default config`(): Unit =
        runBlocking {
            Arb.graphQLExecutionInput(schema).assertAllValid()
        }

    private fun Arb<ExecutionInput>.assertAllValid() {
        val violation = withCheck {
            assertFalse(
                ParseAndValidate.parseAndValidate(schema, it).isFailure
            )
        }.minViolation(ExecutionInputComparator, randomSource, iterations)

        assertNull(violation) {
            violation!!
            val failure = requireNotNull(violation.value)

            val result = ParseAndValidate.parseAndValidate(schema, failure)
            buildString {
                append("ExecutionInput failed validation:\n")
                append("Seed: ${violation.seed}\n")
                result.errors.forEach { append("$it\n") }
                append("Operation: ${failure.operationName}\n")
                append("Variables: ${failure.variables}\n")
                append("Document:\n")
                append(failure.query)
                append("\n")
            }
        }
    }
}
