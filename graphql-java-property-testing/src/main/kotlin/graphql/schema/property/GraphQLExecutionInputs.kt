package graphql.schema.property

import graphql.ExecutionInput
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeUtil
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of

/**
 * Generate an arbitrary GraphQL [ExecutionInput] for the provided schema and config.
 * The returned input contains an executable document and valid external variable values.
 */
fun Arb.Companion.graphQLExecutionInput(
    schema: GraphQLSchema,
    config: Config = Config.default
): Arb<ExecutionInput> =
    graphQLDocument(schema, config).flatMap { document ->
        graphQLExecutionInput(schema, document, config)
    }

/**
 * Generate an arbitrary GraphQL [ExecutionInput] for the provided document and schema.
 * The returned input contains valid external values for the selected operation's variables.
 */
fun Arb.Companion.graphQLExecutionInput(
    schema: GraphQLSchema,
    document: Document,
    config: Config = Config.default
): Arb<ExecutionInput> =
    arbitrary { randomSource ->
        GraphQLExecutionInputGen(schema, config, randomSource).generate(document)
    }

internal class GraphQLExecutionInputGen(
    private val schema: GraphQLSchema,
    private val config: Config,
    private val randomSource: RandomSource
) {
    private val valueGenerator = GraphQLExternalInputValueGenerator(schema, config, randomSource)

    fun generate(document: Document): ExecutionInput {
        val operations = document.getDefinitionsOfType(OperationDefinition::class.java)
        require(operations.isNotEmpty()) { "Document must define at least one operation" }
        val operation = Arb.of(operations).next(randomSource)
        val operationName = operationName(operation, operations.size)
        val variables = operation.variableDefinitions.fold(linkedMapOf<String, Any?>()) { values, definition ->
            val type = definition.type.asSchemaType(schema) as GraphQLInputType
            val canOmit = definition.defaultValue != null || GraphQLTypeUtil.isNullable(type)
            if (!canOmit || !randomSource.sampleWeight(config[ImplicitNullValueWeight])) {
                values[requireNotNull(definition.name)] = valueGenerator.generate(type)
            }
            values
        }

        return ExecutionInput.newExecutionInput()
            .query(AstPrinter.printAst(document))
            .apply { operationName?.let(::operationName) }
            .variables(variables)
            .build()
    }

    private fun operationName(
        operation: OperationDefinition,
        operationCount: Int
    ): String? {
        if (operationCount == 1 && randomSource.sampleWeight(config[AnonymousOperationWeight])) {
            return null
        }
        return operation.name
    }
}
