@file:Suppress("UNCHECKED_CAST")

package tech.codingzen.res

// ---------------------------------------------------------------------------
// Error-context frames — Failure rail only
// ---------------------------------------------------------------------------
//
// `.context { }` attaches a breadcrumb to a Failure so a deep typed error is debuggable
// without a stacktrace. Ok and Defect pass through untouched (a Defect already carries a
// real Throwable + JVM stacktrace). The lambda is evaluated only on the Failure path, so
// the Ok/Defect cost is a single `is Failed` check — nothing is allocated.
//
// Frames ride inside the [Failed] marker, so they survive `map`/`flatMap` and a `bind()`
// short-circuit for free: `loadUser(id).context { "loading user $id" }.bind()` carries the
// frame through the rail with no extra machinery. See `withFrame` (Rail.kt) for annotating
// a whole region of a rail block.

/**
 * Push a breadcrumb [message] onto a Failure. No-op on Ok / Defect ([message] not run).
 *
 * @sample tech.codingzen.res.contextSample
 */
public inline fun <S, F> Res<S, F>.context(message: () -> String): Res<S, F> {
    val r = raw
    if (r !is Failed<*>) return this
    return Res(Failed(r.error, FrameChain.push(r.frames, Frame(message()))))
}

/** Push a breadcrumb with a source [location]. No-op on Ok / Defect (neither lambda runs). */
public inline fun <S, F> Res<S, F>.context(
    message: () -> String,
    location: () -> SourceLocation,
): Res<S, F> {
    val r = raw
    if (r !is Failed<*>) return this
    return Res(Failed(r.error, FrameChain.push(r.frames, Frame(message(), location = location()))))
}

/** Push a fully-built [Frame] (e.g. with a structured `attachment`). No-op on Ok / Defect. */
public inline fun <S, F> Res<S, F>.contextFrame(frame: () -> Frame): Res<S, F> {
    val r = raw
    if (r !is Failed<*>) return this
    return Res(Failed(r.error, FrameChain.push(r.frames, frame())))
}
