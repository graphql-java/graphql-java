package graphql.arbitrary

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLScalarType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.constant
import org.junit.jupiter.api.Test

class GraphQLExternalInputValueGenTest {
    @Test
    fun IDValueGenFactory() {
        val schema = parseTestSchema("type Query { echo(id: ID): ID }")
        val idType = schema.getType("ID") as GraphQLScalarType
        val factory = IDValueGen.Factory { IDValueGen { "configured-id" } }
        val config = Config(
            ExplicitNullValueWeight(0.0),
            IDValueGenFactory(factory)
        )

        GraphQLExternalInputValueGen(schema, config, RandomSource.seeded(1))
            .gen(idType)
            .shouldBe("configured-id")
    }

    @Test
    fun ExplicitNullValueWeight() {
        val schema = parseTestSchema("type Query { x: Int }")
        val scalar = schema.getType("Int") as GraphQLScalarType
        val config = Config(ExplicitNullValueWeight(1.0))
        val nullable = GraphQLExternalInputValueGen(schema, config, RandomSource.seeded(1))
        nullable.gen(scalar).shouldBe(null)

        val nonNull = GraphQLExternalInputValueGen(schema, config, RandomSource.seeded(1))
        (nonNull.gen(GraphQLNonNull.nonNull(scalar)) is Int).shouldBe(true)
    }

    @Test
    fun `lists honor configured size and item nullability`() {
        val schema = parseTestSchema("type Query { x: Int }")
        val scalar = schema.getType("Int") as GraphQLInputType
        val type = GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(scalar)))
        val config = Config(
            ExplicitNullValueWeight(1.0),
            ListValueSize(3..3)
        )
        val value = GraphQLExternalInputValueGen(schema, config, RandomSource.seeded(2))
            .gen(type) as List<*>
        value.size.shouldBe(3)
        value.all { it is Int }.shouldBe(true)
    }

    @Test
    fun `oneOf values contain exactly one non-null member`() {
        val schema = parseTestSchema(
            "input Choice @oneOf { text: String, number: Int } type Query { x(choice: Choice): Int }"
        )
        val type = schema.getType("Choice") as GraphQLInputObjectType
        val config = Config(ExplicitNullValueWeight(1.0))
        val value = GraphQLExternalInputValueGen(schema, config, RandomSource.seeded(3))
            .gen(GraphQLNonNull.nonNull(type)) as Map<*, *>
        value.size.shouldBe(1)
        (value.values.single() != null).shouldBe(true)
    }

    @Test
    fun `recursive input values stop at MaxValueDepth`() {
        val schema = parseTestSchema(
            "input Recursive { next: Recursive } type Query { x(value: Recursive): Int }"
        )
        val type = schema.getType("Recursive") as GraphQLInputObjectType
        val config = Config(
            ExplicitNullValueWeight(0.0),
            ImplicitNullValueWeight(0.0),
            MaxValueDepth(2)
        )
        val value = GraphQLExternalInputValueGen(schema, config, RandomSource.seeded(4))
            .gen(GraphQLNonNull.nonNull(type)) as Map<*, *>
        val nested = value["next"] as Map<*, *>
        nested.shouldBe(mapOf("next" to null))
    }

    @Test
    fun `custom scalars require and use ScalarValueOverrides`() {
        val schema = parseTestSchema("scalar Custom type Query { x(value: Custom): Int }")
        val scalar = schema.getType("Custom") as GraphQLScalarType
        shouldThrow<UnsupportedOperationException> {
            GraphQLExternalInputValueGen(schema, Config.default, RandomSource.seeded(5))
                .gen(scalar)
        }

        val config = Config(
            ExplicitNullValueWeight(0.0),
            ScalarValueOverrides(mapOf("Custom" to Arb.constant("custom-value")))
        )
        GraphQLExternalInputValueGen(schema, config, RandomSource.seeded(5))
            .gen(scalar)
            .shouldBe("custom-value")
    }
}
