package tech.codingzen.res

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TraverseTest {

    // --- sequence -----------------------------------------------------------

    @Test
    fun `sequence collapses an all-ok list, preserving order`() {
        val r = listOf(ok(1), ok(2), ok(3)).sequence()
        assertTrue(r.isOk)
        assertEquals(listOf(1, 2, 3), r.getOrNull())
    }

    @Test
    fun `sequence of an empty list is ok of empty list`() {
        val r: Res<List<Int>, String> = emptyList<Res<Int, String>>().sequence()
        assertEquals(emptyList(), r.getOrNull())
    }

    @Test
    fun `sequence short-circuits on the first failure`() {
        val r = listOf(ok(1), fail("bad"), ok(3)).sequence()
        assertTrue(r.isFailure)
        assertEquals("bad", r.failureOrNull())
    }

    @Test
    fun `sequence short-circuits on a defect`() {
        val boom = RuntimeException("boom")
        val r: Res<List<Int>, String> = listOf(ok(1), defect(boom), ok(3)).sequence()
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }

    @Test
    fun `sequence is left-biased — an earlier failure masks a later defect`() {
        val r: Res<List<Int>, String> = listOf(fail("first"), defect(RuntimeException("later"))).sequence()
        assertTrue(r.isFailure)
        assertEquals("first", r.failureOrNull())
    }

    // --- traverse -----------------------------------------------------------

    @Test
    fun `traverse maps and collects every ok value`() {
        fun parse(s: String): Res<Int, String> =
            s.toIntOrNull()?.let { ok(it) } ?: fail("not a number: $s")

        val r = listOf("1", "2", "3").traverse { parse(it) }
        assertEquals(listOf(1, 2, 3), r.getOrNull())
    }

    @Test
    fun `traverse short-circuits and stops calling transform after the first non-ok`() {
        var calls = 0
        val r: Res<List<Int>, String> = listOf(1, 2, 3, 4).traverse {
            calls++
            if (it == 2) fail("stop at $it") else ok(it)
        }
        assertTrue(r.isFailure)
        assertEquals("stop at 2", r.failureOrNull())
        assertEquals(2, calls, "transform must not run past the short-circuit")
    }

    @Test
    fun `a non-fatal throw in transform routes to the Defect rail`() {
        val boom = RuntimeException("boom")
        val r: Res<List<Int>, String> = listOf(1, 2).traverse { if (it == 2) throw boom else ok(it) }
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }
}
