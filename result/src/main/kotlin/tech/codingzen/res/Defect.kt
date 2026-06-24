@file:Suppress("UNCHECKED_CAST")

package tech.codingzen.res

// ---------------------------------------------------------------------------
// Defect-narrowing — operate only on the Defect rail. Ok and Failure pass
// through untouched. Each runs a user lambda → sealed (non-fatal throw routes to
// a new Defect; fatal propagates).
// ---------------------------------------------------------------------------

/**
 * Handle any Defect by producing any [Res] (the defect-rail analogue of [flatMap]).
 * The [handler] may recover to Ok, narrow to a typed Failure, or yield another
 * Defect. Ok and Failure pass through unchanged.
 *
 * Named `catchAll` rather than `catch`: an untyped `catch` would erase to the same
 * JVM signature as the reified [catch] below (generics are erased), a platform
 * declaration clash. The typed selective form keeps the shorter `catch` name.
 *
 * @sample tech.codingzen.res.catchAllSample
 */
public inline fun <S, F> Res<S, F>.catchAll(handler: (Throwable) -> Res<S, F>): Res<S, F> {
    val r = raw
    if (r !is Defect) return this
    return sealToDefect { handler(r.throwable) }
}

/**
 * Selectively handle a Defect whose throwable is a [T]. Defects of any other type —
 * and the Ok / Failure rails — pass through unchanged. Invoke with an explicit type
 * argument: `catch<IOException> { … }`. (The catch-all form is [catchAll].)
 *
 * @sample tech.codingzen.res.catchTypedSample
 */
public inline fun <reified T : Throwable, S, F> Res<S, F>.catch(handler: (T) -> Res<S, F>): Res<S, F> {
    val r = raw
    if (r !is Defect || r.throwable !is T) return this
    return sealToDefect { handler(r.throwable) }
}

/** Recover a Defect to an Ok value. Ok and Failure pass through unchanged. */
public inline fun <S, F> Res<S, F>.recoverDefect(transform: (Throwable) -> S): Res<S, F> {
    val r = raw
    if (r !is Defect) return this
    return sealToDefect { ok(transform(r.throwable)) }
}

/**
 * Transform the Defect's throwable, staying on the Defect rail. Ok and Failure pass through.
 *
 * @sample tech.codingzen.res.mapDefectSample
 */
public inline fun <S, F> Res<S, F>.mapDefect(transform: (Throwable) -> Throwable): Res<S, F> {
    val r = raw
    if (r !is Defect) return this
    return sealToDefect { defect(transform(r.throwable)) }
}
