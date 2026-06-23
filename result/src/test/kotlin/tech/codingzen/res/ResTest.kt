package tech.codingzen.res

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResTest {

    // --- Construction & state discrimination -------------------------------

    @Test
    fun `ok stores the value unboxed`() {
        val value = "hello"
        val r = ok(value)
        assertTrue(r.isOk)
        assertFalse(r.isFailure)
        assertFalse(r.isDefect)
        // Raw slot IS the value itself — no wrapper allocated.
        assertSame(value, r.raw)
        assertSame(value, r.getOrNull())
    }

    @Test
    fun `fail is on the failure rail`() {
        val r = fail("boom")
        assertTrue(r.isFailure)
        assertFalse(r.isOk)
        assertFalse(r.isDefect)
        assertEquals("boom", r.failureOrNull())
        assertNull(r.getOrNull())
    }

    @Test
    fun `defect is on the defect rail and hidden from the type`() {
        val cause = IllegalStateException("kaboom")
        val r: Res<Int, String> = defect(cause)
        assertTrue(r.isDefect)
        assertFalse(r.isOk)
        assertFalse(r.isFailure)
        assertSame(cause, r.defectOrNull())
        assertNull(r.getOrNull())
        assertNull(r.failureOrNull())
    }

    // --- Boxing edge case --------------------------------------------------

    @Test
    fun `ok value that is itself an Err is boxed and not misread`() {
        // Directly feed an internal Err marker as a success value (only reachable
        // here because the test is in-package). It must round-trip as Ok, not Failure.
        val sneaky: Err = Failed("not-really-a-failure")
        val r = ok(sneaky)
        assertTrue(r.isOk)
        assertFalse(r.isFailure)
        assertTrue(r.raw is OkBox)
        assertSame(sneaky, r.getOrNull())
    }

    @Test
    fun `normal nested Res does not collide`() {
        val inner: Res<Int, String> = fail("inner")
        val outer = ok(inner)
        assertTrue(outer.isOk)
        val back = outer.getOrNull()!!
        assertTrue(back.isFailure)
        assertEquals("inner", back.failureOrNull())
    }

    // --- Ok-rail combinators ------------------------------------------------

    @Test
    fun `map transforms ok, passes failure and defect through`() {
        assertEquals(6, ok(5).map { it + 1 }.getOrNull())

        val f: Res<Int, String> = fail("e")
        assertEquals("e", f.map { it + 1 }.failureOrNull())

        val cause = RuntimeException("d")
        val d: Res<Int, String> = defect(cause)
        assertSame(cause, d.map { it + 1 }.defectOrNull())
    }

    @Test
    fun `flatMap sequences ok and flattens`() {
        val r = ok(5).flatMap { ok(it * 2) }
        assertEquals(10, r.getOrNull())

        val short = ok(5).flatMap { fail<String>("nope") }
        assertEquals("nope", short.failureOrNull())
    }

    @Test
    fun `flatten collapses nested ok`() {
        val nested: Res<Res<Int, String>, String> = ok(ok(7))
        assertEquals(7, nested.flatten().getOrNull())
    }

    @Test
    fun `flatten collapses an inner failure and passes an outer non-ok through`() {
        // inner Failure surfaces
        val innerFail: Res<Res<Int, String>, String> = ok(fail("inner"))
        assertEquals("inner", innerFail.flatten().failureOrNull())

        // outer Failure passes through untouched (transform never runs)
        val outerFail: Res<Res<Int, String>, String> = fail("outer")
        assertEquals("outer", outerFail.flatten().failureOrNull())

        // outer Defect passes through
        val cause = RuntimeException("boom")
        val outerDefect: Res<Res<Int, String>, String> = defect(cause)
        assertSame(cause, outerDefect.flatten().defectOrNull())
    }

    // --- Failure-rail combinators ------------------------------------------

    @Test
    fun `mapFailure transforms only the failure rail`() {
        assertEquals(3, fail("abc").mapFailure { it.length }.failureOrNull())
        assertEquals(9, ok(9).mapFailure { "x" }.getOrNull())
    }

    @Test
    fun `recover converts failure to ok, leaves defect`() {
        val recovered = fail("e").recover { 42 }
        assertEquals(42, recovered.getOrNull())

        val cause = RuntimeException("still-broken")
        val d: Res<Int, String> = defect(cause)
        assertSame(cause, d.recover { 0 }.defectOrNull())
    }

    @Test
    fun `orElse replaces failure with alternative result`() {
        val r: Res<Int, String> = fail("e")
        assertEquals(1, r.orElse { ok(1) }.getOrNull())
        assertEquals("again", r.orElse { fail("again") }.failureOrNull())
    }

    // --- Equality -----------------------------------------------------------
    // Res delegates equals/hashCode to its raw slot, so every rail must compare
    // "equal iff underlying equal", not by identity.

    @Test
    fun `failures with equal payloads are equal`() {
        assertEquals(fail("x"), fail("x"))
        assertEquals(fail("x").hashCode(), fail("x").hashCode())
        assertNotEquals(fail("x"), fail("y"))
    }

    @Test
    fun `ok and failure with the same underlying are not equal`() {
        val o: Res<String, String> = ok("x")
        val f: Res<String, String> = fail("x")
        assertNotEquals(o, f)
    }

    @Test
    fun `defects compare by throwable identity`() {
        val t = RuntimeException("x")
        assertEquals(defect(t), defect(t)) // same instance
        assertEquals(defect(t).hashCode(), defect(t).hashCode())
        assertNotEquals(defect(RuntimeException("x")), defect(RuntimeException("x"))) // distinct
    }

    @Test
    fun `failure and defect are not equal`() {
        val f: Res<Any, Any> = fail("x")
        val d: Res<Any, Any> = defect(RuntimeException("x"))
        assertNotEquals(f, d)
    }

    @Test
    fun `boxed Err ok values compare by value`() {
        val sneaky: Err = Failed("x")
        assertEquals(ok(sneaky), ok(sneaky))
    }

    @Test
    fun `res works as a hash key`() {
        val set = hashSetOf(fail("x"), ok(1))
        assertTrue(set.contains(fail("x")))
        assertTrue(set.contains(ok(1)))
        assertFalse(set.contains(fail("y")))

        val map = hashMapOf(fail<String>("x") to "hit")
        assertEquals("hit", map[fail("x")])
    }

    // --- Throw capture ------------------------------------------------------

    @Test
    fun `non-fatal throw in map routes to defect`() {
        val boom = RuntimeException("boom")
        val r = ok(1).map<Int, Nothing, Int> { throw boom }
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }

    @Test
    fun `fatal throw in map propagates`() {
        assertFailsWith<OutOfMemoryError> {
            ok(1).map<Int, Nothing, Int> { throw OutOfMemoryError() }
        }
    }

    @Test
    fun `cancellation in map propagates`() {
        assertFailsWith<CancellationException> {
            ok(1).map<Int, Nothing, Int> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `every fatal kind in map propagates rather than sealing to defect`() {
        // VirtualMachineError subtypes
        assertFailsWith<OutOfMemoryError> { ok(1).map<Int, Nothing, Int> { throw OutOfMemoryError() } }
        assertFailsWith<StackOverflowError> { ok(1).map<Int, Nothing, Int> { throw StackOverflowError() } }
        // LinkageError + InterruptedException
        assertFailsWith<LinkageError> { ok(1).map<Int, Nothing, Int> { throw LinkageError("link") } }
        assertFailsWith<InterruptedException> { ok(1).map<Int, Nothing, Int> { throw InterruptedException("intr") } }
    }

    // --- Terminal ops -------------------------------------------------------

    @Test
    fun `fold dispatches to the correct arm`() {
        fun <S, F> label(r: Res<S, F>) =
            r.fold(onOk = { "ok:$it" }, onFailure = { "fail:$it" }, onDefect = { "defect:${it.message}" })

        assertEquals("ok:1", label(ok(1)))
        assertEquals("fail:x", label(fail("x")))
        assertEquals("defect:y", label(defect(RuntimeException("y"))))
    }

    @Test
    fun `getOrElse falls back on non-ok rails`() {
        assertEquals(5, ok(5).getOrElse { -1 })
        val f: Res<Int, String> = fail("e")
        assertEquals(-1, f.getOrElse { -1 })
    }

    @Test
    fun `getOrNull cannot distinguish ok-null from a non-ok rail`() {
        // ok(null) and Failure/Defect all surface as null — pins the documented ambiguity.
        val okNull: Res<String?, String> = ok(null)
        assertTrue(okNull.isOk)
        assertNull(okNull.getOrNull())

        val f: Res<String?, String> = fail("x")
        assertNull(f.getOrNull())

        val d: Res<String?, String> = defect(RuntimeException("y"))
        assertNull(d.getOrNull())
    }

    @Test
    fun `getOrThrow returns ok, rethrows defect, raises on failure`() {
        assertEquals(5, ok(5).getOrThrow())

        val cause = IllegalArgumentException("bad")
        val d: Res<Int, String> = defect(cause)
        assertSame(cause, assertFailsWith<IllegalArgumentException> { d.getOrThrow() })

        val f: Res<Int, String> = fail("e")
        val ex = assertFailsWith<FailureException> { f.getOrThrow() }
        assertEquals("e", ex.error)
    }
}
