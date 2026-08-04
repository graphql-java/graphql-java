@file:OptIn(ExperimentalTime::class)
@file:Suppress("ForbiddenImport")

package graphql.schema.property

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CallRecorderTest {
    @Test
    fun `records a successful invocation`() =
        runBlocking {
            val recorder = CallRecorder<Int, String> { it.toString() }
            recorder(42)

            assertEquals(1, recorder.log.size)
            assertEquals(42, recorder.log[0].arg)
            assertTrue(recorder.log[0].result.isSuccess)
            assertEquals("42", recorder.log[0].result.getOrThrow())
        }

    @Test
    fun `re-throws exception and records the failure`() {
        val ex = RuntimeException("boom")
        val recorder = CallRecorder<Int, String> { throw ex }

        val thrown = assertThrows<RuntimeException> { runBlocking { recorder(1) } }

        assertEquals(ex, thrown)
        assertEquals(1, recorder.log.size)
        assertFalse(recorder.log[0].result.isSuccess)
        assertEquals(ex, recorder.log[0].result.exceptionOrNull())
    }

    @Test
    fun `records multiple invocations in call order`() =
        runBlocking {
            val recorder = CallRecorder<Int, Int> { it * 2 }
            recorder(1)
            recorder(2)
            recorder(3)

            assertEquals(3, recorder.log.size)
            assertEquals(listOf(1, 2, 3), recorder.log.map { it.arg })
            assertEquals(listOf(2, 4, 6), recorder.log.map { it.result.getOrThrow() })
        }

    @Test
    fun `log returns a stable snapshot`() =
        runBlocking {
            val recorder = CallRecorder<Int, Int> { it }
            recorder(1)
            val snapshot = recorder.log

            recorder(2)

            assertEquals(1, snapshot.size, "snapshot should not reflect subsequent invocations")
            assertEquals(2, recorder.log.size)
        }

    @Test
    fun `records elapsed time`() =
        runBlocking {
            val recorder = CallRecorder<Unit, Unit> { delay(50) }
            recorder(Unit)

            assertTrue(recorder.time >= 50.milliseconds) {
                "expected time >= 50ms, got ${recorder.time}"
            }
        }

    @Test
    fun `Entry toString contains arg result and time`() =
        runBlocking {
            val recorder = CallRecorder<Int, String> { it.toString() }
            recorder(99)
            val str = recorder.singleEntry().toString()

            assertTrue("arg=99" in str)
            assertTrue("result=" in str)
            assertTrue("time=" in str)
        }

    @Nested
    inner class SingleEntryTests {
        @Test
        fun `returns the entry when exactly one invocation was recorded`() =
            runBlocking {
                val recorder = CallRecorder<Int, Int> { it }
                recorder(7)

                assertEquals(7, recorder.singleEntry().arg)
                assertEquals(7, recorder.singleEntry().result.getOrThrow())
                assertEquals(recorder.singleEntry().time, recorder.time)
            }

        @Test
        fun `throws with count and entry details when log is not exactly 1`() =
            runBlocking {
                val recorder = CallRecorder<Int, Int> { it }

                val emptyEx = assertThrows<IllegalArgumentException> { recorder.singleEntry() }
                assertTrue(emptyEx.message!!.contains("0"), "error message should include the entry count")

                recorder(10)
                recorder(20)
                val multiEx = assertThrows<IllegalArgumentException> { recorder.singleEntry() }
                assertTrue(multiEx.message!!.contains("2"), "error message should include the entry count")
                assertTrue("arg=10" in multiEx.message!!)
                assertTrue("arg=20" in multiEx.message!!)
            }
    }

    @Nested
    inner class SyncCallRecorderTests {
        @Test
        fun `records a successful invocation`() {
            val recorder = CallRecorder.sync<Int, String> { it.toString() }
            recorder(42)

            assertEquals(1, recorder.log.size)
            assertEquals(42, recorder.log[0].arg)
            assertTrue(recorder.log[0].result.isSuccess)
            assertEquals("42", recorder.log[0].result.getOrThrow())
        }

        @Test
        fun `re-throws exception and records the failure`() {
            val ex = RuntimeException("boom")
            val recorder = CallRecorder.sync<Int, String> { throw ex }

            val thrown = assertThrows<RuntimeException> { recorder(1) }

            assertEquals(ex, thrown)
            assertEquals(1, recorder.log.size)
            assertFalse(recorder.log[0].result.isSuccess)
            assertEquals(ex, recorder.log[0].result.exceptionOrNull())
        }

        @Test
        fun `records multiple invocations in call order`() {
            val recorder = CallRecorder.sync<Int, Int> { it * 2 }
            recorder(1)
            recorder(2)
            recorder(3)

            assertEquals(listOf(1, 2, 3), recorder.log.map { it.arg })
            assertEquals(listOf(2, 4, 6), recorder.log.map { it.result.getOrThrow() })
        }
    }
}
