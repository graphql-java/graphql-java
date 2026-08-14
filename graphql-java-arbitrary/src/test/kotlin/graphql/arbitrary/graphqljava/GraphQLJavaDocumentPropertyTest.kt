package graphql.arbitrary.graphqljava

import graphql.ExecutionInput
import graphql.ParseAndValidate
import graphql.arbitrary.ArbPropertyBase
import graphql.arbitrary.graphQLDocument
import graphql.arbitrary.graphQLSchema
import graphql.language.AstComparator
import graphql.language.AstPrinter
import graphql.parser.Parser
import io.kotest.property.Arb
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.map
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphQLJavaDocumentPropertyTest : ArbPropertyBase(iterations = 100) {
    @Test
    fun `document printer output parses to an equivalent AST`(): Unit =
        runBlocking {
            Arb.graphQLSchema(graphQLJavaPropertyConfig)
                .flatMap { schema ->
                    Arb.graphQLDocument(schema, graphQLJavaPropertyConfig)
                }.checkAll { document ->
                    val printedDocument = AstPrinter.printAst(document)
                    val parsedDocument = Parser().parseDocument(printedDocument)

                    assertTrue(AstComparator.isEqual(document, parsedDocument)) {
                        printedDocument
                    }
                }
        }

    @Test
    fun `generated documents pass graphql-java parsing and validation`(): Unit =
        runBlocking {
            Arb.graphQLSchema(graphQLJavaPropertyConfig)
                .flatMap { schema ->
                    Arb.graphQLDocument(schema, graphQLJavaPropertyConfig).map { document ->
                        schema to AstPrinter.printAst(document)
                    }
                }.checkAll { (schema, query) ->
                    val input = ExecutionInput.newExecutionInput(query).build()
                    val result = ParseAndValidate.parseAndValidate(schema, input)

                    assertFalse(result.isFailure) {
                        result.errors.joinToString("\n")
                    }
                }
        }
}
