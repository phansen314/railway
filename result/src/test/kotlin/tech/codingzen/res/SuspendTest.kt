package tech.codingzen.res

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Suspend support is **not** a separate API: every combinator and `rail { }` is `inline`,
 * so a suspend lambda works from any suspend call site, and `CancellationException` is
 * already fatal ([isFatal]) so it never seals into a Defect. These tests pin that the
 * library composes correctly under coroutines with no `railS`/`mapS`/runtime dep.
 */
class SuspendTest {

    // --- suspend stand-ins (force real suspension points) -------------------

    private suspend fun fetch(n: Int): Res<Int, String> {
        delay(1) // a genuine suspension point between binds
        return ok(n)
    }

    private suspend fun failing(e: String): Res<Int, String> {
        delay(1)
        return fail(e)
    }

    private suspend fun doubleIt(n: Int): Int {
        delay(1)
        return n * 2
    }

    // --- rail { } under suspend --------------------------------------------

    @Test
    fun `rail with suspend binds across suspension points returns Ok`() = runTest {
        val r = rail<Int, String> {
            val a = fetch(2).bind()
            val b = fetch(3).bind()
            a + b
        }
        assertTrue(r.isOk)
        assertEquals(5, r.getOrNull())
    }

    @Test
    fun `bind on a suspend-produced failure short-circuits`() = runTest {
        var reached = false
        val r = rail<Int, String> {
            val a = failing("e").bind()
            reached = true
            a
        }
        assertTrue(r.isFailure)
        assertEquals("e", r.failureOrNull())
        assertFalse(reached, "statements after a failing bind must not run")
    }

    @Test
    fun `raise inside a suspend rail produces a Failure`() = runTest {
        val r = rail<Int, String> {
            fetch(1).bind()
            raise("boom")
        }
        assertTrue(r.isFailure)
        assertEquals("boom", r.failureOrNull())
    }

    @Test
    fun `non-fatal throw in a suspend rail body seals to Defect`() = runTest {
        val boom = RuntimeException("boom")
        val r = rail<Int, String> {
            fetch(1).bind()
            throw boom
        }
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }

    // --- combinator chaining with suspend lambdas --------------------------

    @Test
    fun `flatMap and map accept suspend lambdas from a suspend context`() = runTest {
        val r = ok<Int>(2)
            .flatMap { fetch(it + 1) }   // suspend
            .map { doubleIt(it) }        // suspend
        assertTrue(r.isOk)
        assertEquals(6, r.getOrNull())
    }

    @Test
    fun `suspend lambda throwing non-fatal in map seals to Defect`() = runTest {
        val boom = RuntimeException("real")
        val r = ok<Int>(1).map<Int, String, Int> { delay(1); throw boom }
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }

    // --- cancellation safety (the headline guarantee) ----------------------

    @Test
    fun `cancellation across a suspension point in rail propagates, not sealed to Defect`() = runTest {
        // withTimeout throws TimeoutCancellationException (a CancellationException) at the
        // delay. If rail sealed it, this would RETURN a Defect Res; instead it must THROW.
        assertFailsWith<TimeoutCancellationException> {
            rail<Int, String> {
                withTimeout(50) {
                    delay(1_000)
                    ok(1).bind()
                }
            }
        }
    }

    @Test
    fun `cancellation in a suspend map lambda propagates, not sealed to Defect`() = runTest {
        assertFailsWith<TimeoutCancellationException> {
            ok<Int>(1).map {
                withTimeout(50) { delay(1_000); it }
            }
        }
    }

    @Test
    fun `directly thrown CancellationException in a suspend rail propagates`() = runTest {
        assertFailsWith<CancellationException> {
            rail<Int, String> {
                fetch(1).bind()
                throw CancellationException("cancelled")
            }
        }
    }

    // --- context frames under suspend --------------------------------------

    @Test
    fun `withFrame region framing works inside a suspend rail`() = runTest {
        val r = rail<Int, String> {
            withFrame("loading user") {
                failing("db down").bind()
            }
        }
        assertTrue(r.isFailure)
        assertEquals("db down", r.failureOrNull())
        assertTrue(r.contextSummary().contains("loading user"))
    }

    @Test
    fun `context frame survives a suspend mapFailure`() = runTest {
        val r = failing("raw")
            .context { "while fetching" }
            .mapFailure { delay(1); "wrapped:$it" } // suspend lambda
        assertTrue(r.isFailure)
        assertEquals("wrapped:raw", r.failureOrNull())
        assertTrue(r.contextSummary().contains("while fetching"))
    }
}
