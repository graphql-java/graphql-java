package graphql.arbitrary

import graphql.language.StringValue
import graphql.schema.GraphQLInputType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.RandomSource
import org.junit.jupiter.api.Test

class GraphQLInputValueGenTest {
    @Test
    fun IDValueGenFactory() {
        val schema = parseTestSchema("type Query { echo(id: ID): ID }")
        val idType = schema.getType("ID") as GraphQLInputType
        val factory = IDValueGen.Factory { IDValueGen { "configured-id" } }
        val config = Config(
            ExplicitNullValueWeight(0.0),
            IDValueGenFactory(factory)
        )

        val value = GraphQLInputValueGen(schema, config, RandomSource.seeded(1)).gen(idType)

        value.shouldBeInstanceOf<StringValue>().value.shouldBe("configured-id")
    }
}
