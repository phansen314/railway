package tech.codingzen.res

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RailTest {

    // --- Ok path ------------------------------------------------------------

    @Test
    fun `rail returning a plain value is Ok`() {
        val r = rail<Int, String> { 5 }
        assertTrue(r.isOk)
        assertEquals(5, r.getOrNull())
    }

    @Test
    fun `bind extracts ok values and composes`() {
        val r = rail<Int, String> { ok(2).bind() + ok(3).bind() }
        assertEquals(5, r.getOrNull())
    }

    // --- Short-circuit ------------------------------------------------------

    @Test
    fun `bind on a failure short-circuits to that failure`() {
        var reached = false
        val r = rail<Int, String> {
            val a = fail<String>("e").bind()
            reached = true
            a
        }
        assertTrue(r.isFailure)
        assertEquals("e", r.failureOrNull())
        assertFalse(reached, "statements after a failing bind must not run")
    }

    @Test
    fun `bind on a defect short-circuits preserving the defect`() {
        val cause = IllegalStateException("kaboom")
        val r = rail<Int, String> {
            val a: Res<Int, String> = defect(cause)
            a.bind() + 1
        }
        assertTrue(r.isDefect)
        assertSame(cause, r.defectOrNull())
    }

    @Test
    fun `raise short-circuits with a typed failure`() {
        var reached = false
        val r = rail<Int, String> {
            raise("boom")
            reached = true
            1
        }
        assertTrue(r.isFailure)
        assertEquals("boom", r.failureOrNull())
        assertFalse(reached, "statements after raise must not run")
    }

    // --- Throw capture ------------------------------------------------------

    @Test
    fun `non-fatal throw in body routes to defect`() {
        val boom = RuntimeException("boom")
        val r = rail<Int, String> { throw boom }
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }

    @Test
    fun `fatal throw in body propagates`() {
        assertFailsWith<OutOfMemoryError> {
            rail<Int, String> { throw OutOfMemoryError() }
        }
    }

    @Test
    fun `cancellation in body propagates`() {
        assertFailsWith<CancellationException> {
            rail<Int, String> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `every fatal kind in body propagates rather than sealing to defect`() {
        assertFailsWith<StackOverflowError> { rail<Int, String> { throw StackOverflowError() } }
        assertFailsWith<LinkageError> { rail<Int, String> { throw LinkageError("link") } }
        assertFailsWith<InterruptedException> { rail<Int, String> { throw InterruptedException("intr") } }
    }

    // --- Nesting isolation --------------------------------------------------

    @Test
    fun `inner rail collapses to its own failure, bound by the outer`() {
        var afterBind = false
        val outer = rail<Int, String> {
            val inner: Res<Int, String> = rail { raise("inner") }
            val v = inner.bind() // inner already collapsed to a Failure → outer short-circuits
            afterBind = true
            v + 10
        }
        assertTrue(outer.isFailure)
        assertEquals("inner", outer.failureOrNull())
        assertFalse(afterBind, "outer must short-circuit on the inner failure")
    }

    @Test
    fun `inner rail success does not leak its halt to the outer`() {
        val outer = rail<Int, String> {
            val inner: Res<Int, String> = rail {
                val x = ok(7).bind()
                x
            }
            inner.bind() + 1
        }
        assertEquals(8, outer.getOrNull())
    }

    @Test
    fun `outer raise inside an inner rail block propagates to the outer`() {
        // The lambda passed to the inner rail captures the OUTER receiver and calls
        // its raise. The resulting RailHalt is owned by the outer scope, so the inner
        // rail must rethrow it rather than swallow it.
        val outer = rail<Int, String> {
            val inner: Res<Int, String> = rail {
                this@rail.raise("from-outer")
            }
            inner.bind() + 1
        }
        assertTrue(outer.isFailure)
        assertEquals("from-outer", outer.failureOrNull())
    }

    // --- Fast-exception trick ----------------------------------------------

    @Test
    fun `RailHalt carries no stack trace`() {
        val halt = RailHalt(Any(), Failed("x"))
        assertEquals(0, halt.stackTrace.size)
    }

    // --- Combinator lambdas must not swallow the rail short-circuit ---------
    // A bind()/raise()/ensure() inside a combinator lambda throws a RailHalt owned
    // by the enclosing rail. The combinator must rethrow it (not seal it into a
    // Defect) so the rail collapses to the intended Failure.

    @Test
    fun `raise inside a map lambda short-circuits the rail to a failure`() {
        val r = rail<Int, String> {
            ok(5).map<Int, String, Int> { raise("bad") }.bind()
        }
        assertTrue(r.isFailure, "expected Failure, got $r")
        assertEquals("bad", r.failureOrNull())
    }

    @Test
    fun `bind of a failure inside a flatMap lambda short-circuits the rail`() {
        val r = rail<Int, String> {
            ok(5).flatMap<Int, String, Int> { fail<String>("inner").bind(); ok(1) }.bind()
        }
        assertTrue(r.isFailure, "expected Failure, got $r")
        assertEquals("inner", r.failureOrNull())
    }

    @Test
    fun `raise inside a zip combine short-circuits the rail`() {
        val r = rail<Int, String> {
            zip(ok(1), ok(2)) { _, _ -> raise("zap") }.bind()
        }
        assertTrue(r.isFailure, "expected Failure, got $r")
        assertEquals("zap", r.failureOrNull())
    }

    @Test
    fun `raise inside an onOk tap short-circuits the rail`() {
        val r = rail<Int, String> {
            ok(5).onOk { raise("tapped") }.bind()
        }
        assertTrue(r.isFailure, "expected Failure, got $r")
        assertEquals("tapped", r.failureOrNull())
    }

    @Test
    fun `raise inside a catchAll handler short-circuits the rail`() {
        val r = rail<Int, String> {
            val d: Res<Int, String> = defect(RuntimeException("boom"))
            d.catchAll { raise("narrowed") }.bind()
        }
        assertTrue(r.isFailure, "expected Failure, got $r")
        assertEquals("narrowed", r.failureOrNull())
    }

    @Test
    fun `ensure inside a map lambda short-circuits the rail`() {
        val r = rail<Int, String> {
            ok(5).map<Int, String, Int> { ensure(false) { "guard" }; it }.bind()
        }
        assertTrue(r.isFailure, "expected Failure, got $r")
        assertEquals("guard", r.failureOrNull())
    }

    @Test
    fun `outer raise inside a combinator lambda within an inner rail reaches the outer`() {
        // The map lambda runs inside an inner rail but calls the OUTER rail's raise.
        // The RailHalt is owned by the outer scope: it must pass through the map's
        // catch AND the inner rail's owner check, landing on the outer.
        val outer = rail<Int, String> {
            val inner: Res<Int, String> = rail {
                ok(5).map<Int, String, Int> { this@rail.raise("from-outer") }.bind()
            }
            inner.bind() + 1
        }
        assertTrue(outer.isFailure, "expected Failure, got $outer")
        assertEquals("from-outer", outer.failureOrNull())
    }

    @Test
    fun `a genuine non-fatal throw in a combinator lambda still seals to a defect`() {
        // The seal still seals: only RailHalt (and fatals) get special treatment.
        val boom = RuntimeException("real")
        val r = rail<Int, String> {
            ok(5).map<Int, String, Int> { throw boom }.bind()
        }
        assertTrue(r.isDefect, "expected Defect, got $r")
        assertSame(boom, r.defectOrNull())
    }

    // --- Hazard: a broad catch inside the block swallows the short-circuit ---
    // RailHalt is a plain RuntimeException, so a wide catch around bind()/raise()
    // eats it and the block continues. These pin the (intentional, unpreventable)
    // behavior and the safe narrow-catch pattern. See `rail` KDoc.

    @Test
    fun `broad catch inside rail swallows the short-circuit (known hazard)`() {
        val r = rail<Int, String> {
            try {
                fail<String>("lost").bind()
            } catch (e: RuntimeException) {
                // swallows the RailHalt — the Failure never escapes
            }
            99
        }
        assertTrue(r.isOk, "broad catch eats the halt; block runs to completion")
        assertEquals(99, r.getOrNull())
    }

    @Test
    fun `narrow catch inside rail does not swallow the short-circuit`() {
        var caught = false
        val r = rail<Int, String> {
            try {
                fail<String>("kept").bind()
            } catch (e: IllegalStateException) {
                caught = true // never reached: RailHalt is not an IllegalStateException
            }
            99
        }
        assertTrue(r.isFailure, "narrow catch lets the halt through")
        assertEquals("kept", r.failureOrNull())
        assertFalse(caught)
    }

    // --- Boxing edge case ---------------------------------------------------

    @Test
    fun `bind of an ok value that is itself an Err returns it`() {
        val sneaky: Err = Failed("not-really-a-failure")
        val r = rail<Err, String> { ok(sneaky).bind() }
        assertTrue(r.isOk)
        assertSame(sneaky, r.getOrNull())
    }
}
