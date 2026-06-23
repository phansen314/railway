package tech.codingzen.res

import java.io.IOException

// Compiled, type-checked usage samples referenced from KDoc via `@sample`.
// Each function is a self-contained snippet; keep them small and realistic.

internal fun okSample() {
    val r: Res<Int, String> = ok(42)
    check(r.isOk)
}

internal fun failSample() {
    val r: Res<Int, String> = fail("not found")
    check(r.isFailure)
}

internal fun mapSample() {
    val length: Res<Int, String> = ok("hello").map { it.length }
    check(length.getOrNull() == 5)
}

internal fun flatMapSample() {
    fun parse(s: String): Res<Int, String> =
        s.toIntOrNull()?.let { ok(it) } ?: fail("not a number: $s")

    val total: Res<Int, String> = ok("21").flatMap { parse(it) }.map { it * 2 }
    check(total.getOrNull() == 42)
}

internal fun foldSample() {
    val r: Res<Int, String> = fail("nope")
    val message: String = r.fold(
        onOk = { "ok: $it" },
        onFailure = { "failure: $it" },
        onDefect = { "defect: ${it.message}" },
    )
    check(message == "failure: nope")
}

internal fun railSample() {
    fun parse(s: String): Res<Int, String> =
        s.toIntOrNull()?.let { ok(it) } ?: fail("not a number: $s")

    val sum: Res<Int, String> = rail {
        val a = parse("20").bind()   // unwrap, or short-circuit the whole block
        val b = parse("22").bind()
        a + b
    }
    check(sum.getOrNull() == 42)
}

internal fun bindSample() {
    val r: Res<Int, String> = rail {
        val x = ok(40).bind()
        x + 2
    }
    check(r.getOrNull() == 42)
}

internal fun raiseSample() {
    val r: Res<Int, String> = rail {
        raise("aborted")   // : Nothing — nothing after this runs
    }
    check(r.failureOrNull() == "aborted")
}

internal fun ensureSample() {
    fun divide(a: Int, b: Int): Res<Int, String> = rail {
        ensure(b != 0) { "division by zero" }
        a / b
    }
    check(divide(10, 0).failureOrNull() == "division by zero")
}

internal fun catchAllSample() {
    val r: Res<Int, String> = defect(RuntimeException("io blew up"))
        .catchAll { ok(-1) }   // recover any defect to a fallback value
    check(r.getOrNull() == -1)
}

internal fun catchTypedSample() {
    val r: Res<Int, String> = defect(IOException("disk"))
        .catch<IOException, Int, String> { fail("io error") } // narrow only IOException
    check(r.failureOrNull() == "io error")
}

internal fun zipSample() {
    val combined: Res<Int, String> = zip(ok(20), ok(22)) { a, b -> a + b }
    check(combined.getOrNull() == 42)
}

internal fun contextSample() {
    val r: Res<Int, String> = fail<String>("not found")
        .context { "loading user 42" }   // breadcrumb attached only on the Failure rail
    check(r.contextSummary() == "loading user 42 → not found")

    // No-op on Ok — the lambda never runs.
    check(ok<Int>(1).context { error("unreachable") }.getOrNull() == 1)
}

internal fun withFrameSample() {
    fun parse(s: String): Res<Int, String> =
        s.toIntOrNull()?.let { ok(it) } ?: fail("not a number: $s")

    val r: Res<Int, String> = rail {
        withFrame("parsing inputs") {
            val a = parse("20").bind()
            val b = parse("x").bind()   // fails here → "parsing inputs" frame is attached
            a + b
        }
    }
    check(r.contextChain().single().message == "parsing inputs")
}
