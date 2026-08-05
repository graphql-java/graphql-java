@file:OptIn(ExperimentalTime::class)

package graphql.schema.property

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.plusAssign
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

/**
 * Wraps a function [fn] (suspending or blocking) and records each invocation, capturing the
 * argument, result, and elapsed time. Useful for asserting how a resolver or callback was
 * invoked during a test.
 */
sealed class CallRecorder<T, R> {
    /** A single recorded invocation of [fn]. */
    data class Entry<T, R>(val arg: T, val result: Result<R>, val time: Duration) {
        override fun toString(): String = "arg=$arg result=$result time=$time"
    }

    private val _log = CopyOnWriteArrayList<Entry<T, R>>()

    /** All recorded invocations, in the order they were made. */
    val log: List<Entry<T, R>> get() = _log.toList()

    /**
     * The argument of the single recorded invocation.
     * Throws if [log] does not contain exactly 1 entry.
     */
    val arg: T get() = singleEntry().arg

    /**
     * The result of the single recorded invocation.
     * Throws if [log] does not contain exactly 1 entry.
     */
    val result: Result<R> get() = singleEntry().result

    /**
     * The elapsed time of the single recorded invocation.
     * Throws if [log] does not contain exactly 1 entry.
     */
    val time: Duration get() = singleEntry().time

    /**
     * Returns the single entry in [log].
     *
     * Throws [IllegalArgumentException] if [log] does not contain exactly 1 entry.
     * Use [log] directly when more than one invocation is expected.
     */
    fun singleEntry(): Entry<T, R> {
        require(_log.size == 1) {
            buildString {
                appendLine("Expected only 1 entry in the recorder log, but found ${_log.size}")
                for (entry in _log) {
                    appendLine(entry)
                }
            }
        }
        return _log[0]
    }

    protected fun record(
        arg: T,
        timedValue: TimedValue<Result<R>>
    ): R {
        _log += Entry(arg, timedValue.value, timedValue.duration)
        return timedValue.value.getOrThrow()
    }

    companion object {
        operator fun <T, R> invoke(fn: suspend (T) -> R): AsyncCallRecorder<T, R> = AsyncCallRecorder(fn)

        fun <T, R> sync(fn: (T) -> R): SyncCallRecorder<T, R> = SyncCallRecorder(fn)
    }
}

class AsyncCallRecorder<T, R>(private val fn: suspend (T) -> R) : CallRecorder<T, R>() {
    /**
     * Invokes [fn] with [arg], records the result and elapsed time, then returns the
     * value or rethrows any exception thrown by [fn].
     */
    suspend operator fun invoke(arg: T): R {
        val timedValue = measureTimedValue {
            runCatching {
                fn(arg)
            }
        }
        return record(arg, timedValue)
    }
}

class SyncCallRecorder<T, R>(private val fn: (T) -> R) : CallRecorder<T, R>() {
    /**
     * Invokes [fn] with [arg], records the result and elapsed time, then returns the
     * value or rethrows any exception thrown by [fn].
     */
    operator fun invoke(arg: T): R {
        val timedValue = measureTimedValue {
            runCatching {
                fn(arg)
            }
        }
        return record(arg, timedValue)
    }
}
