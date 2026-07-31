package graphql.schema.property

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLScalarType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.constant

class GraphQLExternalInputValueGeneratorTest : FunSpec({
    test("nullable and non-null inputs honor ExplicitNullValueWeight") {
        val schema = parseTestSchema("type Query { x: Int }")
        val scalar = schema.getType("Int") as GraphQLScalarType
        val config = Config.default + (ExplicitNullValueWeight to 1.0)
        val nullable = GraphQLExternalInputValueGenerator(schema, config, RandomSource.seeded(1))
        nullable.generate(scalar).shouldBe(null)

        val nonNull = GraphQLExternalInputValueGenerator(schema, config, RandomSource.seeded(1))
        (nonNull.generate(GraphQLNonNull.nonNull(scalar)) is Int).shouldBe(true)
    }

    test("lists honor configured size and item nullability") {
        val schema = parseTestSchema("type Query { x: Int }")
        val scalar = schema.getType("Int") as GraphQLInputType
        val type = GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(scalar)))
        val config = Config.default +
            (ExplicitNullValueWeight to 1.0) +
            (ListValueSize to 3..3)
        val value = GraphQLExternalInputValueGenerator(schema, config, RandomSource.seeded(2))
            .generate(type) as List<*>
        value.size.shouldBe(3)
        value.all { it is Int }.shouldBe(true)
    }

    test("oneOf values contain exactly one non-null member") {
        val schema = parseTestSchema(
            "input Choice @oneOf { text: String, number: Int } type Query { x(choice: Choice): Int }"
        )
        val type = schema.getType("Choice") as GraphQLInputObjectType
        val config = Config.default + (ExplicitNullValueWeight to 1.0)
        val value = GraphQLExternalInputValueGenerator(schema, config, RandomSource.seeded(3))
            .generate(GraphQLNonNull.nonNull(type)) as Map<*, *>
        value.size.shouldBe(1)
        (value.values.single() != null).shouldBe(true)
    }

    test("recursive input values stop at MaxValueDepth") {
        val schema = parseTestSchema(
            "input Recursive { next: Recursive } type Query { x(value: Recursive): Int }"
        )
        val type = schema.getType("Recursive") as GraphQLInputObjectType
        val config = Config.default +
            (ExplicitNullValueWeight to 0.0) +
            (ImplicitNullValueWeight to 0.0) +
            (MaxValueDepth to 2)
        val value = GraphQLExternalInputValueGenerator(schema, config, RandomSource.seeded(4))
            .generate(GraphQLNonNull.nonNull(type)) as Map<*, *>
        val nested = value["next"] as Map<*, *>
        nested.shouldBe(mapOf("next" to null))
    }

    test("custom scalars require and use ScalarValueOverrides") {
        val schema = parseTestSchema("scalar Custom type Query { x(value: Custom): Int }")
        val scalar = schema.getType("Custom") as GraphQLScalarType
        shouldThrow<UnsupportedOperationException> {
            GraphQLExternalInputValueGenerator(schema, Config.default, RandomSource.seeded(5))
                .generate(scalar)
        }

        val config = Config.default +
            (ExplicitNullValueWeight to 0.0) +
            (ScalarValueOverrides to mapOf("Custom" to Arb.constant("custom-value")))
        GraphQLExternalInputValueGenerator(schema, config, RandomSource.seeded(5))
            .generate(scalar)
            .shouldBe("custom-value")
    }
})
