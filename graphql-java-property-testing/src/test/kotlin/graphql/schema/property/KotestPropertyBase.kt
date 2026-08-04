@file:OptIn(ExperimentalKotest::class)

package graphql.schema.property

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Gen
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.PropertyTesting
import io.kotest.property.RandomSource
import io.kotest.property.checkAll
import kotlin.random.Random
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Abstract base class for kotest-property test suites.
 *
 * Provides a per-instance [seed] that is threaded through all property test invocations
 * via [PropTestConfig], avoiding the global mutable state of [io.kotest.property.PropertyTesting.defaultSeed].
 *
 * To manage test performance, test suites that extend KotestPropertyBase will be run with
 * [ExecutionMode.CONCURRENT]. This will run each test method in a separate thread, in its own
 * instance of the test suite class.
 * This requires opting in to concurrent junit execution in the build system of the test runner:
 * - gradle:
 *     Set these properties in the build.gradle.kts of the module that owns the test suite:
 *     ```kotlin
 *     tasks.withType<Test>().configureEach {
 *       systemProperty("junit.jupiter.execution.parallel.enabled", "true")
 *       systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
 *       systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
 *     }
 *     ```
 *
 * - bazel:
 *      configure your java_test target with `enable_parallel_test_execution`:
 *      ```
 *       java_test(
 *          name = "my-tests",
 *          enable_parallel_test_execution = True,  #keep
 *          ...
 *       )
 *      ```
 *
 * @param seed override the testing seed. This is useful for debugging property test failures.
 * To preserve the randomness of property testing, subclasses should not permanently override
 * the seed value.
 * @param iterations a default number of iterations to apply to [forAll], [checkAll],
 * [forNone], etc
 */
@Execution(ExecutionMode.CONCURRENT)
abstract class KotestPropertyBase(
    val seed: Long = Random.nextLong(),
    val iterations: Int = PropertyTesting.defaultIterationCount
) {
    /** A [RandomSource] seeded with this instance's [seed], for use in explicit generator calls. */
    val randomSource: RandomSource = RandomSource.seeded(seed)

    init {
        println("[KotestPropertyBase] ${this::class.simpleName} seed=$seed")
    }

    private fun config(iterations: Int? = this.iterations): PropTestConfig =
        PropTestConfig(
            seed = seed,
            iterations = iterations,
            // The original suites deliberately use assume() for roughly half of some generated
            // documents. Kotest 6 enforces its 20% default discard ceiling for those properties.
            maxDiscardPercentage = 90
        )

    /** Apply [io.kotest.property.forAll] using the configured seed and iterations */
    suspend fun <A> Gen<A>.forAll(property: suspend PropertyContext.(A) -> Boolean) = io.kotest.property.forAll(config(), this, property)

    /** Apply [io.kotest.property.forAll] using the configured seed and iterations */
    suspend fun <A> Gen<A>.forAll(
        iterations: Int,
        property: suspend PropertyContext.(A) -> Boolean
    ) = io.kotest.property.forAll(config(iterations), this, property)

    /** Apply [io.kotest.property.checkAll] using the configured seed and iterations */
    suspend fun <A> Gen<A>.checkAll(property: suspend PropertyContext.(A) -> Unit) = io.kotest.property.checkAll(config(), this, property)

    /** Apply [io.kotest.property.checkAll] using the configured seed and iterations */
    suspend fun <A> Gen<A>.checkAll(
        iterations: Int,
        property: suspend PropertyContext.(A) -> Unit
    ) = io.kotest.property.checkAll(iterations, config(), this, property)

    /** Apply [io.kotest.property.forNone] using the configured seed and iterations */
    suspend fun <A> Gen<A>.forNone(property: suspend PropertyContext.(A) -> Boolean) = io.kotest.property.forNone(config(), this, property)

    /** Apply [io.kotest.property.forNone] using the configured seed and iterations */
    suspend fun <A> Gen<A>.forNone(
        iterations: Int,
        property: suspend PropertyContext.(A) -> Boolean
    ) = io.kotest.property.forNone(config(iterations), this, property)

    /** assert that all values of an Arb pass the invariant checks defined by a provided function */
    suspend fun <T> Gen<T>.checkInvariants(
        iter: Int? = null,
        fn: PropertyContext.(T, FailureCollector) -> Unit
    ) {
        val cfg = config(iter)
        checkAll(cfg) {
            val check = FailureCollector()
            this.fn(it, check)
            check.assertEmpty("\n")
            markSuccess()
        }
    }

    /** Convert an arb to an infinite [kotlin.sequences.Sequence] */
    fun <T> Gen<T>.asSequence(): Sequence<T> = generate(randomSource).map { it.value }
}
