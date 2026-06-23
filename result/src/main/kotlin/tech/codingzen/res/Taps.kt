@file:Suppress("UNCHECKED_CAST")

package tech.codingzen.res

// ---------------------------------------------------------------------------
// Tap / peek — run a side effect on the matching rail, return `this` unchanged.
// Sealed like the combinators: a non-fatal throw in the action routes to Defect;
// a fatal throw propagates.
// ---------------------------------------------------------------------------

/** Run [action] on the Ok value (if any), then return this result unchanged. */
public inline fun <S, F> Res<S, F>.onOk(action: (S) -> Unit): Res<S, F> {
    val r = raw
    if (r !is Err) return sealToDefect { action(unwrapOk(r) as S); this }
    return this
}

/** Run [action] on the Failure payload (if any), then return this result unchanged. */
public inline fun <S, F> Res<S, F>.onFailure(action: (F) -> Unit): Res<S, F> {
    val r = raw
    if (r is Failed<*>) return sealToDefect { action(r.error as F); this }
    return this
}

/** Run [action] on the Defect throwable (if any), then return this result unchanged. */
public inline fun <S, F> Res<S, F>.onDefect(action: (Throwable) -> Unit): Res<S, F> {
    val r = raw
    if (r is Defect) return sealToDefect { action(r.throwable); this }
    return this
}
