package tech.codingzen.res

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SugarTest {

    // --- Guards -------------------------------------------------------------

    @Test
    fun `ensure passes when condition holds`() {
        val r = rail<Int, String> {
            ensure(true) { "nope" }
            42
        }
        assertEquals(42, r.getOrNull())
    }

    @Test
    fun `ensure raises when condition fails`() {
        var reached = false
        val r = rail<Int, String> {
            ensure(false) { "bad" }
            reached = true
            42
        }
        assertEquals("bad", r.failureOrNull())
        assertTrue(!reached)
    }

    @Test
    fun `ensureNotNull returns the value or raises`() {
        val ok = rail<Int, String> {
            val v = ensureNotNull<Int, String>(7) { "missing" }
            v + 1
        }
        assertEquals(8, ok.getOrNull())

        val bad = rail<Int, String> {
            val v = ensureNotNull<Int, String>(null) { "missing" }
            v + 1
        }
        assertEquals("missing", bad.failureOrNull())
    }

    // --- Taps ---------------------------------------------------------------

    @Test
    fun `taps fire only on their own rail and return the same result`() {
        var okSeen: Int? = null
        var failSeen: String? = null
        var defectSeen: Throwable? = null

        val r = ok<Int>(5)
            .onOk { okSeen = it }
            .onFailure { failSeen = it as String? }
            .onDefect { defectSeen = it }
        assertEquals(5, r.getOrNull())
        assertEquals(5, okSeen)
        assertEquals(null, failSeen)
        assertEquals(null, defectSeen)

        val f: Res<Int, String> = fail("e")
        var fSeen: String? = null
        var fOk: Int? = null
        f.onFailure { fSeen = it }.onOk { fOk = it }
        assertEquals("e", fSeen)
        assertEquals(null, fOk)
    }

    @Test
    fun `non-fatal throw in a tap routes to defect`() {
        val boom = RuntimeException("boom")
        val r = ok(1).onOk { throw boom }
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }

    @Test
    fun `fatal throw in a tap propagates`() {
        assertFailsWith<CancellationException> {
            ok(1).onOk { throw CancellationException("x") }
        }
    }

    @Test
    fun `onDefect fires on an actual defect and returns the same result`() {
        val cause = RuntimeException("d")
        var seen: Throwable? = null
        val r: Res<Int, String> = defect(cause).onDefect { seen = it }
        assertSame(cause, seen)
        assertTrue(r.isDefect)
        assertSame(cause, r.defectOrNull())
    }

    @Test
    fun `non-fatal throw in onFailure routes to defect`() {
        val boom = RuntimeException("boom")
        val r = fail<String>("e").onFailure { throw boom }
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }

    // --- Defect-narrowing ---------------------------------------------------

    @Test
    fun `catchAll maps a defect and passes ok and failure through`() {
        val cause = RuntimeException("d")
        val d: Res<Int, String> = defect(cause)
        assertEquals(99, d.catchAll { ok(99) }.getOrNull())
        assertEquals("narrowed", d.catchAll { fail("narrowed") }.failureOrNull())

        // ok / failure untouched
        assertEquals(1, ok<Int>(1).catchAll { ok(0) }.getOrNull())
        assertEquals("e", fail<String>("e").catchAll { ok(0) }.failureOrNull())
    }

    @Test
    fun `recoverDefect turns a defect into ok`() {
        val d: Res<Int, String> = defect(RuntimeException("d"))
        assertEquals(7, d.recoverDefect { 7 }.getOrNull())
        assertEquals(1, ok<Int>(1).recoverDefect { 0 }.getOrNull())
    }

    @Test
    fun `mapDefect transforms the throwable and stays a defect`() {
        val d: Res<Int, String> = defect(RuntimeException("orig"))
        val wrapped = IllegalStateException("wrapped")
        val r = d.mapDefect { wrapped }
        assertTrue(r.isDefect)
        assertSame(wrapped, r.defectOrNull())
    }

    @Test
    fun `typed catch handles the match and passes other types through`() {
        val ise: Res<Int, String> = defect(IllegalStateException("state"))
        val handled = ise.catch<IllegalStateException, Int, String> { ok(0) }
        assertEquals(0, handled.getOrNull())

        val other: Res<Int, String> = defect(IllegalArgumentException("arg"))
        val passed = other.catch<IllegalStateException, Int, String> { ok(0) }
        assertTrue(passed.isDefect)
        assertTrue(passed.defectOrNull() is IllegalArgumentException)
    }

    @Test
    fun `throw in a defect handler routes to defect`() {
        val boom = RuntimeException("boom")
        val d: Res<Int, String> = defect(RuntimeException("orig"))
        val r = d.catchAll { throw boom }
        assertSame(boom, r.defectOrNull())
    }

    // --- zip: see ZipTest --------------------------------------------------
}
