@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.intRange
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll

class ConfigTest : FunSpec({
    test("Config validates entries") {
        val valid = ConfigKey(0) { null }
        val invalid = ConfigKey(0) { "invalid" }
        checkAll(PropTestConfig(iterations = 100), Arb.int()) { value ->
            (Config.default + (valid to value))[valid].shouldBe(value)
            shouldThrow<InvalidConfigValue> { Config.default + (invalid to value) }
                .message.shouldBe("invalid")
        }
    }

    test("a Config overrides another Config") {
        val first = ConfigKey(1, Unvalidated)
        val second = ConfigKey(2, Unvalidated)
        val base = Config.default + (first to 11) + (second to 22)
        val combined = base + (Config.default + (first to 111))
        combined[first].shouldBe(111)
        combined[second].shouldBe(22)
    }

    test("missing keys return defaults and present keys return configured values") {
        checkAll(PropTestConfig(iterations = 100), Arb.int(), Arb.int()) { default, configured ->
            val key = ConfigKey(default, Unvalidated)
            Config.default[key].shouldBe(default)
            (Config.default + (key to configured))[key].shouldBe(configured)
        }
    }

    test("WeightValidator accepts exactly the inclusive unit interval") {
        checkAll(PropTestConfig(iterations = 200), Arb.double()) { value ->
            val valid = runCatching { Config.validateOrThrow(WeightValidator, value) }.isSuccess
            valid.shouldBe(value in 0.0..1.0)
        }
        runCatching { Config.validateOrThrow(WeightValidator, 0.0) }.isSuccess.shouldBe(true)
        runCatching { Config.validateOrThrow(WeightValidator, 1.0) }.isSuccess.shouldBe(true)
    }

    test("CompoundingWeightValidator rejects invalid weights and maxima") {
        val values = Arb.pair(Arb.double(), Arb.int())
        checkAll(PropTestConfig(iterations = 200), values) { (weight, maximum) ->
            val valid = runCatching {
                Config.validateOrThrow(CompoundingWeightValidator, CompoundingWeight(weight, maximum))
            }.isSuccess
            valid.shouldBe(weight in 0.0..1.0 && maximum >= 0)
        }
    }

    test("IntValidator accepts only values in its domain") {
        checkAll(PropTestConfig(iterations = 200), Arb.intRange(-10_000..10_000), Arb.int()) { domain, value ->
            val valid = runCatching { Config.validateOrThrow(IntValidator(domain), value) }.isSuccess
            valid.shouldBe(value in domain)
        }
    }

    test("IntRangeValidator accepts non-empty subranges") {
        val ranges = Arb.intRange(-10_000..10_000)
        checkAll(PropTestConfig(iterations = 200), ranges, ranges) { domain, range ->
            val valid = runCatching { Config.validateOrThrow(IntRangeValidator(domain), range) }.isSuccess
            valid.shouldBe(!range.isEmpty() && domain.first <= range.first && domain.last >= range.last)
        }
    }

    test("Unvalidated accepts unrelated values") {
        Config.validateOrThrow(Unvalidated, 1)
        Config.validateOrThrow(Unvalidated, "value")
        Config.validateOrThrow(Unvalidated, Any())
        Unvalidated(Unit).shouldBe(null)
    }
})
