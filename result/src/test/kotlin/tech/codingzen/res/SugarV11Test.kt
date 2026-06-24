package tech.codingzen.res

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** v1.1 sugar: mapBoth / swap / getOrDefault / filterOrElse. */
class SugarV11Test {

    // --- mapBoth ------------------------------------------------------------

    @Test
    fun `mapBoth applies onOk on the Ok rail`() {
        val r: Res<String, Int> = ok(5).mapBoth(onOk = { "v=$it" }, onFailure = { it })
        assertEquals("v=5", r.getOrNull())
    }

    @Test
    fun `mapBoth applies onFailure on the Failure rail`() {
        val r: Res<String, Int> = fail<String>("nope").mapBoth(onOk = { "v=$it" }, onFailure = { it.length })
        assertTrue(r.isFailure)
        assertEquals(4, r.failureOrNull())
    }

    @Test
    fun `mapBoth passes a Defect through untouched`() {
        val boom = RuntimeException("boom")
        val r: Res<String, Int> = defect(boom).mapBoth(onOk = { "x" }, onFailure = { 0 })
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }

    @Test
    fun `mapBoth preserves context frames across the failure payload change`() {
        val r = fail<String>("raw")
            .context { "while loading" }
            .mapBoth(onOk = { it }, onFailure = { "wrapped:$it" })
        assertEquals("wrapped:raw", r.failureOrNull())
        assertTrue(r.contextSummary().contains("while loading"))
    }

    @Test
    fun `a throw in a mapBoth lambda routes to the Defect rail`() {
        val boom = RuntimeException("boom")
        val r: Res<String, Int> = ok(5).mapBoth(onOk = { throw boom }, onFailure = { 0 })
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }

    // --- swap ---------------------------------------------------------------

    @Test
    fun `swap turns an Ok into a Failure`() {
        val r: Res<Nothing, Int> = ok(7).swap()
        assertTrue(r.isFailure)
        assertEquals(7, r.failureOrNull())
    }

    @Test
    fun `swap turns a Failure into an Ok`() {
        val r: Res<String, Nothing> = fail<String>("boom").swap()
        assertTrue(r.isOk)
        assertEquals("boom", r.getOrNull())
    }

    @Test
    fun `swap leaves a Defect a Defect`() {
        val boom = RuntimeException("boom")
        val r = defect(boom).swap()
        assertTrue(r.isDefect)
        assertSame(boom, r.defectOrNull())
    }

    // --- getOrDefault -------------------------------------------------------

    @Test
    fun `getOrDefault returns the ok value or the constant fallback`() {
        assertEquals(7, ok(7).getOrDefault(0))
        assertEquals(0, fail<String>("x").getOrDefault(0))
        assertEquals(0, defect(RuntimeException()).getOrDefault(0))
    }

    // --- filterOrElse -------------------------------------------------------

    @Test
    fun `filterOrElse keeps an Ok that passes the predicate`() {
        val r = ok(20).filterOrElse(predicate = { it >= 18 }, error = { "under 18: $it" })
        assertTrue(r.isOk)
        assertEquals(20, r.getOrNull())
    }

    @Test
    fun `filterOrElse demotes an Ok that fails the predicate, error lambda sees the value`() {
        val r = ok(15).filterOrElse(predicate = { it >= 18 }, error = { "under 18: $it" })
        assertTrue(r.isFailure)
        assertEquals("under 18: 15", r.failureOrNull())
    }

    @Test
    fun `filterOrElse passes Failure and Defect through untouched`() {
        val f: Res<Int, String> = fail<String>("kept").filterOrElse(predicate = { true }, error = { "x" })
        assertEquals("kept", f.failureOrNull())

        val boom = RuntimeException("boom")
        val d: Res<Int, String> = defect(boom).filterOrElse(predicate = { true }, error = { "x" })
        assertTrue(d.isDefect)
        assertSame(boom, d.defectOrNull())
    }
}
