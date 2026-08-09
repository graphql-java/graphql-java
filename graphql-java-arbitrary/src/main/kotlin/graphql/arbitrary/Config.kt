package graphql.arbitrary

/**
 * A container for configuration.
 * `Config.default` will return default values for any provided [ConfigKey]
 *
 * Example:
 * ```
 *   // write a config with a value
 *   val key: ConfigKey<Int> = ...
 *   val cfg = Config(key(10))
 *
 *   // read a config value
 *   val value = cfg[key]
 * ```
 */
class Config private constructor(
    private val map: Map<ConfigKey<*>, Any?>
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: ConfigKey<T>): T = map[key] as? T ?: key.default

    /**
     * Return a new Config with the provided key-value pair.
     *
     * Values that are invalid for the given ConfigKey will be
     * raised as an IllegalArgumentException
     */
    operator fun <T> plus(pair: Pair<ConfigKey<T>, T>): Config =
        this + Config(pair.first, pair.second)

    /** Return a copy of this Config, overridden by any configurations in [overrides] */
    operator fun plus(overrides: Config): Config = Config(map + overrides.map)


    companion object {
        /** A [Config] that returns default values for any [ConfigKey] */
        val default = Config(emptyMap())

        internal fun <T> validateOrThrow(validator: Validator<T>, t: T) {
            validator(t)?.let { msg ->
                throw InvalidConfigValue(msg, t)
            }
        }

        /**
         * Construct a Config by combining the provided configurations.
         *
         * Example:
         * ```kotlin
         *
         * Config(
         *   configKey1(value1),
         *   configKey2(value2)
         * )
         * ```
         */
        operator fun invoke(vararg configs: Config): Config =
            configs.fold(default, Config::plus)

        operator fun <T> invoke(key: ConfigKey<T>, value: T): Config {
            validateOrThrow(key.validate, value)
            return Config(mapOf(key to value))
        }
    }
}

class InvalidConfigValue(msg: String, val value: Any?) : Exception(msg)

/** Validator returns a descriptive error string for invalid inputs */
typealias Validator<T> = (t: T) -> String?

/**
 * Validates that Double values are between 0.0 and 1.0, inclusive
 */
object WeightValidator : Validator<Double> {
    override fun invoke(x: Double): String? =
        if (x !in 0.0..1.0) {
            "Value must be between 0.0 and 1.0"
        } else {
            null
        }
}

/**
 * Describes a weight that may be sampled repeatedly, up to `max` times
 *
 * For example, if field argument size were described using `CompoundingWeight(.1, 3)`,
 * then we would expect approximately
 * - 10% of fields would define at least 1 argument,
 * - 1% of fields would define at least 2 arguments,
 * - .1% of fields would define 3 arguments,
 * - no fields would define 4 or more arguments
 */
data class CompoundingWeight(val weight: Double, val max: Int) {
    companion object {
        /** A [CompoundingWeight] that will be false every time it is sampled */
        val Never: CompoundingWeight = CompoundingWeight(0.0, 0)

        /** A CompoundingWeight that will be true the first time it is sampled and false every other time */
        val Once: CompoundingWeight = CompoundingWeight(1.0, 1)

        /** A CompoundingWeight that will always return true */
        val Always: CompoundingWeight = CompoundingWeight(1.0, Int.MAX_VALUE)
    }
}

/**
 * Validates that a [CompoundingWeight] has a weight between 0.0 and 1.0
 * and a max less than or equal to configured value
 */
object CompoundingWeightValidator : Validator<CompoundingWeight> {
    override fun invoke(x: CompoundingWeight): String? {
        if (!(0..Int.MAX_VALUE).contains(x.max)) {
            return "Maximum must be in 0..Int.MAX_VALUE"
        }
        return WeightValidator.invoke(x.weight)
    }
}

/**
 * Validates that Int values are within an IntRange domain
 */
class IntValidator(val domain: IntRange) : Validator<Int> {
    override fun invoke(x: Int): String? =
        if (!domain.contains(x)) {
            "Value must be between ${domain.first} and ${domain.last}"
        } else {
            null
        }
}

/**
 * Validates that all possible values of a provided IntRange values are
 * also within a specified IntRange domain
 */
class IntRangeValidator(val domain: IntRange) : Validator<IntRange> {
    override fun invoke(x: IntRange): String? =
        if (domain.first > x.first || domain.last < x.last) {
            "Range must be between ${domain.first} and ${domain.last}"
        } else if (x.isEmpty()) {
            "Range must not be empty, got $x"
        } else {
            null
        }
}

/** A Validator that accepts all input */
object Unvalidated : Validator<Any> {
    override fun invoke(t: Any): String? = null
}

/** Base class for identifying configurable values. */
open class ConfigKey<T>(
    val default: T,
    val validate: Validator<T>
) {
    operator fun invoke(value: T): Config = Config(this, value)
}

infix fun <T> ConfigKey<T>.setTo(value: T): Config =
    Config(this, value)
