@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import graphql.ParseAndValidate
import graphql.schema.idl.SchemaParser
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class GraphQLTestDataTest : KotestPropertyBase() {
    @Test
    fun `graphQLTestData generates round-trippable schemas and valid queries`(): Unit =
        runBlocking {
            val config = Config.default + (SchemaSize to 10)
            Arb.graphQLTestData(config).checkAll(30) { data ->
                SchemaParser().parse(data.sdl)
                val schema = parseTestSchema(data.sdl)
                val input = graphql.ExecutionInput.newExecutionInput()
                    .query(data.query)
                    .variables(data.variables)
                    .build()
                ParseAndValidate.parseAndValidate(schema, input).isFailure.shouldBe(false)
            }
        }

    @Test
    fun `minimum query length filters generated test data`(): Unit =
        runBlocking {
            val config = Config.default +
                (SchemaSize to 5) +
                (MinQueryLength to 10)
            Arb.graphQLTestData(config).checkAll(10) { data ->
                (data.query.length >= 10).shouldBe(true)
            }
        }

    @Test
    fun `GraphQLTestDataStats counts schema and query constructs`() {
        val data = GraphQLTestData(
            sdl = "type Obj { value: Int! } type Query { obj: Obj, values: [Int] }",
            query = "query Q(\$include: Boolean!) { alias: obj @include(if: \$include) { ...F } } fragment F on Obj { value }",
            variables = mapOf("include" to true)
        )
        val stats = GraphQLTestDataStats(data)
        stats.schemaStats["object definitions"].shouldBe(8)
        stats.schemaStats["list-typed object fields"].shouldBe(11)
        stats.queryStats["aliased selections"].shouldBe(1)
        stats.queryStats["fragment definitions"].shouldBe(1)
        stats.queryStats["external variables"].shouldBe(1)
        stats.format().lines().shouldContain("    external variables: 1")
    }
}
