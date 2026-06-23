package tech.codingzen.res

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContextTest {

    // --- .context{} basics --------------------------------------------------

    @Test
    fun `context attaches a frame to a Failure`() {
        val r = fail<String>("boom").context { "step one" }
        assertEquals(listOf("step one"), r.contextChain().map { it.message })
        assertEquals("step one → boom", r.contextSummary())
    }

    @Test
    fun `context is a no-op on Ok and never runs the lambda`() {
        var ran = false
        val r = ok<Int>(1).context { ran = true; "nope" }
        assertEquals(1, r.getOrNull())
        assertTrue(!ran)
        assertTrue(r.contextChain().isEmpty())
    }

    @Test
    fun `context is a no-op on Defect — Defects carry their own stacktrace`() {
        var ran = false
        val r = defect(RuntimeException("kaboom")).context { ran = true; "nope" }
        assertTrue(r.isDefect)
        assertTrue(!ran)
        assertTrue(r.contextChain().isEmpty())
    }

    @Test
    fun `frames stack innermost-first across multiple context calls`() {
        val r = fail<String>("boom")
            .context { "inner" }
            .context { "outer" }
        // index 0 == closest to the error (innermost pushed first)
        assertEquals(listOf("inner", "outer"), r.contextChain().map { it.message })
        assertEquals("outer → inner → boom", r.contextSummary())
    }

    @Test
    fun `contextFrame carries a structured attachment, findAttachment retrieves it`() {
        val r = fail<String>("boom").contextFrame { Frame("with id", attachment = 42) }
        assertEquals(42, r.contextChain().findAttachment<Int>())
    }

    @Test
    fun `contextFrame works without an attachment`() {
        val r = fail<String>("boom").contextFrame { Frame("plain") }
        assertEquals(listOf("plain"), r.contextChain().map { it.message })
        assertEquals(null, r.contextChain().single().attachment)
    }

    @Test
    fun `findAttachment returns null when no frame holds the type`() {
        val r = fail<String>("boom").contextFrame { Frame("with id", attachment = 42) }
        assertEquals(null, r.contextChain().findAttachment<String>())
    }

    @Test
    fun `contextMap renders the error and frames with attachment and location`() {
        val r = fail<String>("boom")
            .contextFrame { Frame("inner", attachment = 7, location = SourceLocation("A.kt", 10, "f")) }
            .context { "outer" }
        val map = r.contextMap()
        assertEquals("boom", map["error"])

        @Suppress("UNCHECKED_CAST")
        val frames = map["frames"] as List<Map<String, Any?>>
        assertEquals(listOf("inner", "outer"), frames.map { it["message"] })
        assertEquals(7, frames[0]["attachment"])
        assertEquals("A.kt:10 in f", frames[0]["location"])
        // outer frame has neither attachment nor location keys
        assertTrue("attachment" !in frames[1])
        assertTrue("location" !in frames[1])
    }

    // --- preservation through combinators -----------------------------------

    @Test
    fun `map and flatMap preserve frames on a Failure pass-through`() {
        val base = fail<String>("boom").context { "ctx" }
        assertEquals(listOf("ctx"), base.map<Int, String, Int> { it }.contextChain().map { it.message })
        assertEquals(listOf("ctx"), base.flatMap<Int, String, Int> { ok(it) }.contextChain().map { it.message })
    }

    @Test
    fun `mapFailure preserves frames across a payload change`() {
        val r = fail<String>("boom").context { "ctx" }.mapFailure { it.length }
        assertEquals(4, r.failureOrNull())
        assertEquals(listOf("ctx"), r.contextChain().map { it.message })
    }

    @Test
    fun `orElse merges the original frames when the alternative also fails`() {
        val r = fail<String>("first").context { "outer" }
            .orElse { fail<String>("second").context { "inner" } }
        assertEquals("second", r.failureOrNull())
        // inner (alternative's own) closest to the error, then the original's outer
        assertEquals(listOf("inner", "outer"), r.contextChain().map { it.message })
    }

    // --- rail integration ---------------------------------------------------

    @Test
    fun `context composes with bind through a rail short-circuit`() {
        val r = rail<Int, String> {
            val a = ok(20).bind()
            val b: Int = fail<String>("bad").context { "loading b" }.bind()
            a + b
        }
        assertEquals("bad", r.failureOrNull())
        assertEquals(listOf("loading b"), r.contextChain().map { it.message })
    }

    @Test
    fun `withFrame annotates a failure escaping the region`() {
        val r = rail<Int, String> {
            withFrame("parsing inputs") {
                val a = ok(20).bind()
                val b: Int = fail<String>("nope").bind()
                a + b
            }
        }
        assertEquals("nope", r.failureOrNull())
        assertEquals(listOf("parsing inputs"), r.contextChain().map { it.message })
    }

    @Test
    fun `withFrame leaves an Ok region untouched`() {
        val r = rail<Int, String> {
            withFrame("region") { ok(40).bind() + 2 }
        }
        assertEquals(42, r.getOrNull())
    }

    @Test
    fun `withFrame does not annotate a Defect`() {
        val r = rail<Int, String> {
            withFrame("region") {
                error("kaboom")   // thrown → Defect rail, not a Failure
            }
        }
        assertTrue(r.isDefect)
        assertTrue(r.contextChain().isEmpty())
    }

    @Test
    fun `withFrame only annotates its own rail, not a nested rail failure`() {
        val r = rail<Int, String> {
            withFrame("outer region") {
                val inner: Res<Int, String> = rail { raise("inner fail") }
                inner.bind()   // the bind (this rail's failure) gets the frame
            }
        }
        assertEquals("inner fail", r.failureOrNull())
        assertEquals(listOf("outer region"), r.contextChain().map { it.message })
    }

    // --- rendering ----------------------------------------------------------

    @Test
    fun `renderContext lists frames innermost-first with the error`() {
        val rendered = fail<String>("boom")
            .context { "inner" }
            .context { "outer" }
            .renderContext()
        assertEquals(
            """
            boom
              0: inner
              1: outer
            """.trimIndent(),
            rendered,
        )
    }

    @Test
    fun `frames are metadata — equality ignores them`() {
        assertEquals(fail<String>("boom"), fail<String>("boom").context { "ctx" })
    }

    @Test
    fun `readers return empty on the Ok rail`() {
        val r = ok<Int>(1)
        assertTrue(r.contextChain().isEmpty())
        assertEquals("", r.renderContext())
        assertSame(emptyMap<String, Any?>().javaClass, r.contextMap().javaClass)
        assertTrue(r.contextMap().isEmpty())
    }
}
