@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import graphql.ExceptionWhileDataFetching
import graphql.execution.NonNullableFieldWasNullError
import graphql.Scalars
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.RuntimeWiring
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class GraphQLRuntimeWiringsTest : FunSpec({
    test("the same seed and input always return the same result") {
        val schema = parseTestSchema(
            "interface I { x: Int } type A implements I { x: Int } type Query { x: Int, values: [Int], i: I }"
        )
        val input = graphql.ExecutionInput.newExecutionInput()
            .query("{ x values i { __typename x } }")
            .build()
        checkAll(PropTestConfig(iterations = 50), Arb.long()) { seed ->
            val graphQL = arbitraryGraphQL(schema, seed)
            val results = List(5) { graphQL.execute(input).toSpecification() }
            results.distinct().size.shouldBe(1)
        }
    }

    test("different response keys are independently salted") {
        val schema = parseTestSchema("type Query { x: Int! }")
        checkAll(PropTestConfig(iterations = 50), Arb.long()) { seed ->
            val result = arbitraryGraphQL(schema, seed).execute("{ x a: x b: x c: x }")
            val data = requireNotNull(result.getData<Map<String, Int>>())
            data.size.shouldBe(4)
            (data.values.distinct().size > 1).shouldBe(true)
        }
    }

    test("abstract resolvers return concrete schema instances and __typename") {
        val schema = parseTestSchema(
            "type A { x: Int } type B { x: Int } union U = A | B type Query { u: U }"
        )
        val config = Config.default + (ExplicitNullValueWeight to 0.0)
        checkAll(PropTestConfig(iterations = 50), Arb.long()) { seed ->
            val result = arbitraryGraphQL(schema, seed, config).execute("{ u { __typename } }")
            result.errors.shouldBeEmpty()
            val data = requireNotNull(result.getData<Map<String, Map<String, String>>>())
            (data.getValue("u").getValue("__typename") in setOf("A", "B")).shouldBe(true)
        }
    }

    test("NullNonNullableWeight injects non-null failures") {
        val schema = parseTestSchema("type Query { x: Int! }")
        val disabled = Config.default + (NullNonNullableWeight to 0.0)
        arbitraryGraphQL(schema, 1, disabled).execute("{ x }").errors.shouldBeEmpty()

        val enabled = Config.default + (NullNonNullableWeight to 1.0)
        val errors = arbitraryGraphQL(schema, 1, enabled).execute("{ x }").errors
        errors.any { it is NonNullableFieldWasNullError }.shouldBe(true)
    }

    test("ExplicitNullValueWeight and ListValueSize shape fetched values") {
        val schema = parseTestSchema("type Query { nullable: Int, values: [Int!]! }")
        val config = Config.default +
            (ExplicitNullValueWeight to 1.0) +
            (ListValueSize to 3..3)
        val result = arbitraryGraphQL(schema, 2, config).execute("{ nullable values }")
        val data = requireNotNull(result.getData<Map<String, Any?>>())
        data["nullable"].shouldBe(null)
        (data["values"] as List<*>).size.shouldBe(3)
    }

    test("ResolverExceptionWeight injects observable resolver failures") {
        val schema = parseTestSchema("type Query { x: Int }")
        val disabled = Config.default + (ResolverExceptionWeight to 0.0)
        arbitraryGraphQL(schema, 3, disabled).execute("{ x }").errors.shouldBeEmpty()

        val enabled = Config.default + (ResolverExceptionWeight to 1.0)
        val errors = arbitraryGraphQL(schema, 3, enabled).execute("{ x }").errors
        errors.shouldNotBeEmpty()
        val exception = (errors.first() as ExceptionWhileDataFetching).exception
        (exception is ResolverInjectedException).shouldBe(true)
    }

    test("custom scalar outputs use ScalarValueOverrides") {
        val customScalar = GraphQLScalarType.newScalar(Scalars.GraphQLString)
            .name("Custom")
            .build()
        val wiring = RuntimeWiring.newRuntimeWiring().scalar(customScalar).build()
        val schema = parseTestSchema("scalar Custom type Query { x: Custom! }", wiring)
        val config = Config.default +
            (ScalarValueOverrides to mapOf("Custom" to Arb.constant("custom-value")))
        val result = arbitraryGraphQL(schema, 4, config).execute("{ x }")
        result.errors.shouldBeEmpty()
        result.getData<Map<String, String>>().shouldBe(mapOf("x" to "custom-value"))
    }
})
