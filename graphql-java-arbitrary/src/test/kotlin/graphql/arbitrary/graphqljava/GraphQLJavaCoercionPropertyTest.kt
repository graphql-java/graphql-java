package graphql.arbitrary.graphqljava

import graphql.arbitrary.ArbPropertyBase
import graphql.execution.RawVariables
import graphql.execution.ValuesResolver
import graphql.language.OperationDefinition
import graphql.parser.Parser
import io.kotest.property.Arb
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphQLJavaCoercionPropertyTest : ArbPropertyBase(iterations = 100) {
    @Test
    fun `generated external variables can be coerced`(): Unit =
        runBlocking {
            Arb.graphQLJavaExecutionCase().checkAll { (schema, input) ->
                val document = Parser().parseDocument(input.query)
                val operation = selectOperation(
                    document.getDefinitionsOfType(OperationDefinition::class.java),
                    input.operationName
                )
                val coercedVariables = ValuesResolver.coerceVariableValues(
                    schema,
                    operation.variableDefinitions,
                    RawVariables.of(input.variables),
                    input.graphQLContext,
                    input.locale
                )

                assertTrue(coercedVariables.toMap().keys.containsAll(input.variables.keys))
            }
        }

    private fun selectOperation(
        operations: List<OperationDefinition>,
        operationName: String?
    ): OperationDefinition {
        if (operationName == null) {
            return operations.single()
        }
        return operations.single { it.name == operationName }
    }
}
