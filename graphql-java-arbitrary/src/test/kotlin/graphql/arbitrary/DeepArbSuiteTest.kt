package graphql.arbitrary

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.constant
import io.kotest.property.asSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeepArbSuiteTest : DeepArbSuite<Int>(iterations = 1) {
    override val comparator: Comparator<Int> = Comparator.naturalOrder()

    override val checkedArb: CheckedArb<Int> =
        Arb.constant(1).withCheck {
            assertEquals(1, it)
        }

    @Test
    fun `check all uses configured seed and checks each sample once`() {
        var observedSeed: Long? = null
        var checkCount = 0
        val recordingArb = object : Arb<Int>() {
            override fun edgecase(rs: RandomSource): io.kotest.property.Sample<Int>? = null

            override fun sample(rs: RandomSource): Sample<Int> {
                observedSeed = rs.seed
                return 1.asSample()
            }
        }
        val suite = object : DeepArbSuite<Int>(seed = 123L, iterations = 1) {
            override val comparator: Comparator<Int> = Comparator.naturalOrder()
            override val checkedArb = recordingArb.withCheck { checkCount += 1 }
        }

        suite.`check all`()

        assertEquals(123L, observedSeed)
        assertEquals(1, checkCount)
    }
}
