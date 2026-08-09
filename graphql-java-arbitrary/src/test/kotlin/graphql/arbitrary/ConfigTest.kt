@file:Suppress("ForbiddenImport")

package graphql.arbitrary

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.intRange
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.withEdgecases
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConfigTest : ArbPropertyBase() {
    @Test
    fun `Config constructor validates values`(): Unit =
        runBlocking {
            val alwaysValid = ConfigKey(0) { null }
            val alwaysInvalid = ConfigKey(0) { "msg" }
            Arb.int().forAll { i ->
                val putValid =
                    Result.runCatching {
                        Config(alwaysValid(i))
                    }

                val putInvalid =
                    Result.runCatching {
                        Config(alwaysInvalid(i))
                    }
                putValid.isSuccess && putInvalid.isFailure
            }
        }

    @Test
    fun `Config_plus config overrides values`() {
        val k1 = ConfigKey(1, Unvalidated)
        val k2 = ConfigKey(2, Unvalidated)

        val c1 = Config(k1(11), k2(22))
        val c2 = Config(k1(111))
        val combined = c1 + c2

        assertEquals(111, combined[k1])
        assertEquals(22, combined[k2])
    }

    @Test
    fun `invoked ConfigKey returns a validated Config`() {
        val key = ConfigKey(0, IntValidator(0..10))

        val config = key(7)

        assertEquals(7, config[key])
        assertThrows(InvalidConfigValue::class.java) {
            key(11)
        }
    }

    @Test
    fun `Config_get with missing key returns default value`(): Unit =
        runBlocking {
            Arb.int().forAll { i ->
                val key = ConfigKey(i, Unvalidated)
                Config.default.get(key) == i
            }
        }

    @Test
    fun `Config_get with present key returns configured value`(): Unit =
        runBlocking {
            val key = ConfigKey<Any>(0, Unvalidated)
            Arb.int().forAll { x ->
                val cfg = Config(key(x))
                cfg[key] == x
            }
        }

    @Test
    fun `setTo returns configurations that can be combined`() {
        val k1 = ConfigKey(0, Unvalidated)
        val k2 = ConfigKey("", Unvalidated)
        val k3 = ConfigKey(1.0f, Unvalidated)
        val config = Config(
            k1 setTo 2,
            k2 setTo "a"
        )

        assertEquals(2, config[k1])
        assertEquals("a", config[k2])
        assertEquals(1.0f, config[k3])
    }

    @Test
    fun `Config constructor combines invoked keys`() {
        val k1 = ConfigKey(0, IntValidator(0..10))
        val k2 = ConfigKey("", Unvalidated)

        val config = Config(
            k1(2),
            k2("a")
        )

        assertEquals(2, config[k1])
        assertEquals("a", config[k2])
    }

    @Test
    fun `WeightValidator rejects values outside of 0 and 1`(): Unit =
        runBlocking {
            Arb
                .double()
                .withEdgecases(listOf(-1.0, 0.0, 1.0))
                .forAll { d ->
                    val result =
                        Result.runCatching {
                            Config.validateOrThrow(WeightValidator, d)
                        }

                    if (d < 0.0 || d > 1.0) {
                        result.isFailure
                    } else {
                        result.isSuccess
                    }
                }
        }

    @Test
    fun `CompoundingWeightValidator rejects invalid values`(): Unit =
        runBlocking {
            val cw =
                Arb.bind(
                    Arb.double().withEdgecases(listOf(0.0, 1.0)),
                    Arb.int()
                ) { weight, max ->
                    CompoundingWeight(weight, max)
                }

            cw.checkAll {
                val passed =
                    Result
                        .runCatching {
                            Config.validateOrThrow(CompoundingWeightValidator, it)
                        }.isSuccess

                if (it.weight < 0.0 && passed) {
                    markFailure()
                } else if (it.weight > 1.0 && passed) {
                    markFailure()
                } else if (it.max < 0 && passed) {
                    markFailure()
                } else {
                    markSuccess()
                }
            }
        }

    @Test
    fun `IntValidator rejects values outside of range`(): Unit =
        runBlocking {
            Arb
                .pair(
                    Arb.intRange(Int.MIN_VALUE until Int.MAX_VALUE),
                    Arb.int()
                ).forAll { (range, i) ->
                    val result =
                        Result.runCatching {
                            Config.validateOrThrow(IntValidator(range), i)
                        }

                    if (range.contains(i)) {
                        result.isSuccess
                    } else {
                        result.isFailure
                    }
                }
        }

    @Test
    fun `IntRangeValidator rejects values outside of domain`(): Unit =
        runBlocking {
            Arb
                .pair(
                    Arb.intRange(Int.MIN_VALUE until Int.MAX_VALUE),
                    Arb.intRange(Int.MIN_VALUE until Int.MAX_VALUE)
                ).forAll { (domain, range) ->
                    val result =
                        Result.runCatching {
                            Config.validateOrThrow(IntRangeValidator(domain), range)
                        }

                    if (range.isEmpty()) {
                        result.isFailure
                    } else if (domain.first <= range.first && domain.last >= range.last) {
                        result.isSuccess
                    } else {
                        result.isFailure
                    }
                }
        }

    @Test
    fun `Unvalidated accepts all values`(): Unit =
        runBlocking {
            Arb
                .choice(
                    Arb.int(),
                    Arb.string(),
                    Arb.double(),
                    Arb.char()
                ).forAll {
                    Result
                        .runCatching {
                            Config.validateOrThrow(Unvalidated, it)
                        }.isSuccess
                }
        }
}
