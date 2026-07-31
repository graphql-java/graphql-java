@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import graphql.ParseAndValidate
import graphql.schema.idl.SchemaParser
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll

class GraphQLTestDataTest : FunSpec({
    test("graphQLTestData generates round-trippable schemas and valid queries") {
        val config = Config.default + (SchemaSize to 10)
        checkAll(PropTestConfig(iterations = 30), Arb.graphQLTestData(config)) { data ->
            SchemaParser().parse(data.sdl)
            val schema = parseTestSchema(data.sdl)
            val input = graphql.ExecutionInput.newExecutionInput()
                .query(data.query)
                .variables(data.variables)
                .build()
            ParseAndValidate.parseAndValidate(schema, input).isFailure.shouldBe(false)
        }
    }

    test("minimum query length filters generated test data") {
        val config = Config.default +
            (SchemaSize to 5) +
            (MinQueryLength to 10)
        checkAll(PropTestConfig(iterations = 10), Arb.graphQLTestData(config)) { data ->
            (data.query.length >= 10).shouldBe(true)
        }
    }

    test("GraphQLTestDataStats counts schema and query constructs") {
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
})
