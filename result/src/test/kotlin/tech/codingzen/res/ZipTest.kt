package tech.codingzen.res

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ZipTest {

    // --- happy path ---------------------------------------------------------

    @Test
    fun `zip combines ok values at every arity`() {
        assertEquals(5, zip(ok(2), ok(3)) { a, b -> a + b }.getOrNull())
        assertEquals(6, zip(ok(1), ok(2), ok(3)) { a, b, c -> a + b + c }.getOrNull())
        assertEquals(10, zip(ok(1), ok(2), ok(3), ok(4)) { a, b, c, d -> a + b + c + d }.getOrNull())
    }

    // --- fail-fast, positional left-bias ------------------------------------

    @Test
    fun `arity two short-circuits on the first non-ok by position`() {
        assertEquals("a", zip(fail<String>("a"), fail<String>("b")) { _: Nothing, _: Nothing -> 0 }.failureOrNull())
        // earlier Failure masks a later Defect (single rail, positional order)
        val late = RuntimeException("late")
        assertEquals(
            "a",
            zip(fail<String>("a"), defect(late)) { _: Nothing, _: Nothing -> 0 }.failureOrNull(),
        )
    }

    @Test
    fun `arity three short-circuits on the first non-ok in each position`() {
        val firstFail = zip(fail<String>("a"), ok(2), ok(3)) { _: Nothing, _, _ -> 0 }
        assertEquals("a", firstFail.failureOrNull())

        val midFail = zip(ok(1), fail<String>("b"), ok(3)) { _, _: Nothing, _ -> 0 }
        assertEquals("b", midFail.failureOrNull())

        val lastFail = zip(ok(1), ok(2), fail<String>("c")) { _, _, _: Nothing -> 0 }
        assertEquals("c", lastFail.failureOrNull())

        // a Defect anywhere short-circuits too; an earlier Failure still wins over a later Defect
        val cause = RuntimeException("d")
        assertSame(cause, zip(ok(1), defect(cause), ok(3)) { _, _: Nothing, _ -> 0 }.defectOrNull())
        assertEquals("b", zip(ok(1), fail<String>("b"), defect(cause)) { _, _: Nothing, _: Nothing -> 0 }.failureOrNull())
    }

    @Test
    fun `arity four short-circuits on the first non-ok in each position`() {
        assertEquals("a", zip(fail<String>("a"), ok(2), ok(3), ok(4)) { _: Nothing, _, _, _ -> 0 }.failureOrNull())
        assertEquals("b", zip(ok(1), fail<String>("b"), ok(3), ok(4)) { _, _: Nothing, _, _ -> 0 }.failureOrNull())
        assertEquals("c", zip(ok(1), ok(2), fail<String>("c"), ok(4)) { _, _, _: Nothing, _ -> 0 }.failureOrNull())
        assertEquals("d", zip(ok(1), ok(2), ok(3), fail<String>("d")) { _, _, _, _: Nothing -> 0 }.failureOrNull())

        val cause = RuntimeException("defect")
        assertSame(cause, zip(ok(1), ok(2), ok(3), defect(cause)) { _, _, _, _: Nothing -> 0 }.defectOrNull())
        // earlier Failure masks a later Defect
        assertEquals("a", zip(fail<String>("a"), defect(cause), ok(3), ok(4)) { _: Nothing, _: Nothing, _, _ -> 0 }.failureOrNull())
    }

    // --- combine sealing ----------------------------------------------------

    @Test
    fun `a throwing combine routes to defect at every arity`() {
        val boom = RuntimeException("boom")
        assertSame(boom, zip(ok(1), ok(2)) { _, _ -> throw boom }.defectOrNull())
        assertSame(boom, zip(ok(1), ok(2), ok(3)) { _, _, _ -> throw boom }.defectOrNull())
        assertSame(boom, zip(ok(1), ok(2), ok(3), ok(4)) { _, _, _, _ -> throw boom }.defectOrNull())
    }

    @Test
    fun `a fatal throw in combine propagates`() {
        kotlin.test.assertFailsWith<LinkageError> {
            zip(ok(1), ok(2)) { _, _ -> throw LinkageError("boom") }
        }
    }
}
