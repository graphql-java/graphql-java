package graphql.arbitrary.graphqljava

import graphql.arbitrary.ArbPropertyBase
import graphql.arbitrary.arbGraphQL
import graphql.arbitrary.graphQLSchema
import graphql.introspection.IntrospectionQuery
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.schema.idl.UnExecutableSchemaGenerator
import io.kotest.property.Arb
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphQLJavaSchemaPropertyTest : ArbPropertyBase(iterations = 100) {
    @Test
    fun `schema printer output can be parsed and rebuilt`(): Unit =
        runBlocking {
            val printer = SchemaPrinter()
            Arb.graphQLSchema(graphQLJavaPropertyConfig).checkAll { schema ->
                val printedSchema = printer.print(schema)
                val typeRegistry = SchemaParser().parse(printedSchema)
                val rebuiltSchema = UnExecutableSchemaGenerator.makeUnExecutableSchema(typeRegistry)

                assertEquals(printedSchema, printer.print(rebuiltSchema))
            }
        }

    @Test
    fun `generated schemas support introspection`(): Unit =
        runBlocking {
            Arb.graphQLSchema(graphQLJavaPropertyConfig).checkAll { schema ->
                val result = arbGraphQL(schema, seed, graphQLJavaPropertyConfig)
                    .execute(IntrospectionQuery.INTROSPECTION_QUERY)

                assertTrue(result.errors.isEmpty()) {
                    result.errors.joinToString("\n")
                }
                val data = requireNotNull(result.getData<Map<String, Any?>>())
                assertTrue(data.containsKey("__schema"))
            }
        }
}
