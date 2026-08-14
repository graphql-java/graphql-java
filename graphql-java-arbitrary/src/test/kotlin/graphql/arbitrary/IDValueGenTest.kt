package graphql.arbitrary

import io.kotest.matchers.shouldBe
import io.kotest.property.RandomSource
import org.junit.jupiter.api.Test

class IDValueGenTest {
    @Test
    fun `default factory uses ArbString`() {
        val schema = parseTestSchema("type Query { id: ID }")
        val config = Config(StringValueSize(7.asIntRange()))
        val generator = config[IDValueGenFactory](
            IDValueGen.Factory.Params(schema, config, RandomSource.seeded(1))
        )

        generator.gen().length.shouldBe(7)
    }
}
