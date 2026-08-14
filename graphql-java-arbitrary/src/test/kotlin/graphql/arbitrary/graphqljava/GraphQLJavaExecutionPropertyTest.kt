package graphql.arbitrary.graphqljava

import graphql.arbitrary.ArbPropertyBase
import graphql.arbitrary.arbGraphQL
import io.kotest.property.Arb
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphQLJavaExecutionPropertyTest : ArbPropertyBase(iterations = 100) {
    @Test
    fun `generated execution inputs execute without errors`(): Unit =
        runBlocking {
            Arb.graphQLJavaExecutionCase().checkAll { (schema, input) ->
                val result = arbGraphQL(schema, seed, graphQLJavaPropertyConfig).execute(input)

                assertTrue(result.errors.isEmpty()) {
                    buildString {
                        appendLine(result.errors.joinToString("\n"))
                        appendLine(input.query)
                        appendLine("operationName=${input.operationName}")
                        append("variables=${input.variables}")
                    }
                }
            }
        }

    @Test
    fun `repeated execution produces the same result`(): Unit =
        runBlocking {
            Arb.graphQLJavaExecutionCase().checkAll { (schema, input) ->
                val graphQL = arbGraphQL(schema, seed, graphQLJavaPropertyConfig)
                val firstResult = graphQL.execute(input).toSpecification()
                val secondResult = graphQL.execute(input).toSpecification()

                assertEquals(firstResult, secondResult)
            }
        }
}
