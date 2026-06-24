package tech.codingzen.res

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CatchingTest {

    // --- one-arg: throw → Defect -------------------------------------------

    @Test
    fun `catching returns Ok when the block succeeds`() {
        val r = catching { "42".toInt() }
        assertTrue(r.isOk)
        assertEquals(42, r.getOrNull())
    }

    @Test
    fun `catching routes a non-fatal throw to the Defect rail`() {
        val boom = IllegalStateException("boom")
        val r = catching { throw boom }
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }

    // --- two-arg: throw → typed Failure ------------------------------------

    @Test
    fun `catching with a transform returns Ok when the block succeeds`() {
        val r: Res<Int, String> = catching(transform = { "x" }) { "7".toInt() }
        assertEquals(7, r.getOrNull())
    }

    @Test
    fun `catching with a transform maps a non-fatal throw to a Failure`() {
        val r: Res<Int, String> = catching(transform = { "not a number: ${it.message}" }) { "x".toInt() }
        assertTrue(r.isFailure)
        assertEquals(true, r.failureOrNull()?.startsWith("not a number"))
    }

    @Test
    fun `a throwing transform lands on the Defect rail (mapper bug, not a domain error)`() {
        val mapperBug = RuntimeException("mapper")
        val r: Res<Int, String> = catching(transform = { throw mapperBug }) { throw RuntimeException("orig") }
        assertTrue(r.isDefect)
        assertSame(mapperBug, r.defectOrNull())
    }

    // --- fatal / cancellation always propagate -----------------------------

    @Test
    fun `fatal throws propagate from both forms`() {
        assertFailsWith<OutOfMemoryError> { catching { throw OutOfMemoryError() } }
        assertFailsWith<OutOfMemoryError> { catching(transform = { "x" }) { throw OutOfMemoryError() } }
    }

    @Test
    fun `cancellation propagates from both forms`() {
        assertFailsWith<CancellationException> { catching { throw CancellationException("c") } }
        assertFailsWith<CancellationException> { catching(transform = { "x" }) { throw CancellationException("c") } }
    }

    // --- enclosing rail short-circuit must pass through --------------------

    @Test
    fun `catching rethrows an enclosing rail short-circuit rather than mapping it`() {
        val outer = rail<Int, String> {
            catching<Int, String>(transform = { "mapped" }) { this@rail.raise("from-rail") }
            1 // unreachable: the rethrown RailHalt collapses the outer rail before this
        }
        assertTrue(outer.isFailure)
        assertEquals("from-rail", outer.failureOrNull()) // not "mapped" — the halt passed through
    }
}
