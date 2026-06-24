@file:Suppress("UNCHECKED_CAST")

package tech.codingzen.res

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/** Extract the ok value from a raw slot known to be on the Ok rail (unbox if needed). */
@PublishedApi
internal fun unwrapOk(raw: Any?): Any? = if (raw is OkBox) raw.value else raw

/**
 * Run [block] under the rail "seal" policy and return its [Res]. This is the single
 * chokepoint every lambda-running combinator routes through:
 *
 *  - a [RailHalt] (the short-circuit of an enclosing [rail] block) is **rethrown** so
 *    the owning rail handles it — never swallowed into a Defect;
 *  - a fatal throw ([isFatal]) propagates;
 *  - any other throw is sealed onto the Defect rail.
 *
 * The [RailHalt] arm must come **before** the generic arm: it is a non-fatal
 * `RuntimeException`, so a generic-first order would catch and mis-route it (mirrors
 * [rail]'s own catch order). Wrap the lambda-running path only — keep each
 * combinator's fast `if (raw is Err) return …` pass-through outside this helper.
 */
@PublishedApi
internal inline fun <S, F> sealToDefect(block: () -> Res<S, F>): Res<S, F> =
    try {
        block()
    } catch (h: RailHalt) {
        throw h
    } catch (e: Throwable) {
        if (isFatal(e)) throw e else defect(e)
    }

// ---------------------------------------------------------------------------
// Ok rail
// ---------------------------------------------------------------------------

/**
 * Transform the ok value. Failure and defect pass through untouched. A non-fatal
 * throw inside [transform] is routed to the Defect rail; a fatal throw propagates.
 *
 * @sample tech.codingzen.res.mapSample
 */
public inline fun <S, F, S2> Res<S, F>.map(transform: (S) -> S2): Res<S2, F> {
    val r = raw
    if (r is Err) return Res(r)
    return sealToDefect { ok(transform(unwrapOk(r) as S)) }
}

/**
 * Sequence another result off the ok value. Failure and defect pass through
 * untouched. A non-fatal throw inside [transform] is routed to the Defect rail.
 *
 * @sample tech.codingzen.res.flatMapSample
 */
public inline fun <S, F, S2> Res<S, F>.flatMap(transform: (S) -> Res<S2, F>): Res<S2, F> {
    val r = raw
    if (r is Err) return Res(r)
    return sealToDefect { transform(unwrapOk(r) as S) }
}

/** Collapse a nested ok rail. */
public fun <S, F> Res<Res<S, F>, F>.flatten(): Res<S, F> = flatMap { it }

/**
 * Demote an ok value that fails [predicate] to a Failure carrying `error(value)`. Ok values
 * that pass, and the Failure/Defect rails, pass through untouched. A non-fatal throw inside
 * either lambda is routed to the Defect rail. The named complement of
 * `flatMap { if (p(it)) ok(it) else fail(error(it)) }`.
 *
 * @sample tech.codingzen.res.filterOrElseSample
 */
public inline fun <S, F> Res<S, F>.filterOrElse(predicate: (S) -> Boolean, error: (S) -> F): Res<S, F> {
    val r = raw
    if (r is Err) return Res(r)
    return sealToDefect {
        val v = unwrapOk(r) as S
        if (predicate(v)) ok(v) else fail(error(v))
    }
}

// ---------------------------------------------------------------------------
// Failure rail
// ---------------------------------------------------------------------------

/**
 * Transform the failure payload. Ok and defect pass through untouched. A non-fatal
 * throw inside [transform] is routed to the Defect rail.
 *
 * @sample tech.codingzen.res.mapFailureSample
 */
public inline fun <S, F, F2> Res<S, F>.mapFailure(transform: (F) -> F2): Res<S, F2> {
    val r = raw
    if (r !is Failed<*>) return Res(r) // ok or defect
    // Preserve the context frames across the payload change — they are metadata about the
    // failure, not the error value itself.
    return sealToDefect { Res(Failed(transform(r.error as F), r.frames)) }
}

/**
 * Recover from a failure by producing an ok value. Ok values and defects pass
 * through untouched. A non-fatal throw inside [transform] is routed to Defect.
 *
 * The result type is `Res<S, Nothing>` because the Failure rail is provably empty
 * afterward — but this is **not** infallible: a pre-existing defect passes through,
 * and [transform] may itself throw to the Defect rail. `Nothing` failure ≠
 * cannot-fail. For an Ok-only value, also handle defects ([recoverDefect] / [fold] /
 * [getOrNull]).
 *
 * @sample tech.codingzen.res.recoverSample
 */
public inline fun <S, F> Res<S, F>.recover(transform: (F) -> S): Res<S, Nothing> {
    val r = raw
    if (r !is Failed<*>) return Res(r) // ok or defect
    return sealToDefect { ok(transform(r.error as F)) }
}

/**
 * Replace a failure with an alternative result. Ok values and defects pass through
 * untouched. A non-fatal throw inside [alternative] is routed to Defect.
 *
 * @sample tech.codingzen.res.orElseSample
 */
public inline fun <S, F, F2> Res<S, F>.orElse(alternative: (F) -> Res<S, F2>): Res<S, F2> {
    val r = raw
    if (r !is Failed<*>) return Res(r) // ok or defect
    return sealToDefect {
        val recovered = alternative(r.error as F)
        val rec = recovered.raw
        // If recovery also fails, carry the original failure's frames onto the new one so the
        // breadcrumb trail survives the recovery attempt. Ok / defect alternatives pass through.
        if (rec is Failed<*>) Res(Failed(rec.error, concatFrames(rec.frames, r.frames))) else recovered
    }
}

// ---------------------------------------------------------------------------
// Both rails
// ---------------------------------------------------------------------------

/**
 * Transform whichever of the Ok / Failure rails is present in a single pass — the typical
 * shape at an API boundary (Ok becomes a payload, Failure becomes an error DTO). A Defect
 * passes through untouched; the Failure's context frames are preserved across the payload
 * change (like [mapFailure]). A non-fatal throw inside either lambda routes to the Defect rail.
 *
 * @sample tech.codingzen.res.mapBothSample
 */
public inline fun <S, F, S2, F2> Res<S, F>.mapBoth(onOk: (S) -> S2, onFailure: (F) -> F2): Res<S2, F2> {
    val r = raw
    return when {
        r is Failed<*> -> sealToDefect { Res(Failed(onFailure(r.error as F), r.frames)) }
        r is Defect -> Res(r)
        else -> sealToDefect { ok(onOk(unwrapOk(r) as S)) }
    }
}

/**
 * Exchange the Ok and Failure rails: an Ok value becomes the failure payload and a Failure
 * payload becomes the ok value. A Defect stays a Defect.
 *
 * **Frames are dropped:** the Ok rail cannot hold context frames, so a swapped Failure's
 * breadcrumbs do not survive. Swap before attaching frames, not after.
 *
 * @sample tech.codingzen.res.swapSample
 */
public fun <S, F> Res<S, F>.swap(): Res<F, S> = when (val r = raw) {
    is Failed<*> -> ok(r.error as F)
    is Defect -> Res(r)
    is OkBox -> fail(r.value as S)
    else -> fail(r as S)
}

// ---------------------------------------------------------------------------
// Terminal / elimination
// ---------------------------------------------------------------------------

/**
 * Dispatch on the rail. The single exhaustive eliminator — its three arms force you
 * to handle the otherwise-hidden Defect rail.
 *
 * @sample tech.codingzen.res.foldSample
 */
public inline fun <S, F, R> Res<S, F>.fold(
    onOk: (S) -> R,
    onFailure: (F) -> R,
    onDefect: (Throwable) -> R,
): R = when (val r = raw) {
    is Failed<*> -> onFailure(r.error as F)
    is Defect -> onDefect(r.throwable)
    is OkBox -> onOk(r.value as S)
    else -> onOk(r as S)
}

/**
 * The ok value, or `null` on the Failure/Defect rails.
 *
 * **Ambiguous when `null` is a valid ok value:** `ok(null)` and any Failure/Defect all
 * return `null`, so a `null` result tells you nothing about the rail. Use [fold],
 * [isOk], or [getOrThrow] when an ok `null` must be distinguished from a non-ok rail.
 *
 * @sample tech.codingzen.res.getOrNullSample
 */
public fun <S> Res<S, *>.getOrNull(): S? = when (val r = raw) {
    is Err -> null
    is OkBox -> r.value as S
    else -> r as S
}

/**
 * The failure payload, or `null` on the Ok/Defect rails.
 *
 * @sample tech.codingzen.res.failureOrNullSample
 */
public fun <F> Res<*, F>.failureOrNull(): F? {
    val r = raw
    return if (r is Failed<*>) r.error as F else null
}

/**
 * The defect throwable, or `null` on the Ok/Failure rails.
 *
 * @sample tech.codingzen.res.defectOrNullSample
 */
public fun Res<*, *>.defectOrNull(): Throwable? = (raw as? Defect)?.throwable

/**
 * The ok value, or [fallback] on any non-ok rail.
 *
 * @sample tech.codingzen.res.getOrElseSample
 */
public inline fun <S> Res<S, *>.getOrElse(fallback: () -> S): S = when (val r = raw) {
    is Err -> fallback()
    is OkBox -> r.value as S
    else -> r as S
}

/** The ok value, or the constant [value] on any non-ok rail (the eager [getOrElse]). */
public fun <S> Res<S, *>.getOrDefault(value: S): S = when (val r = raw) {
    is Err -> value
    is OkBox -> r.value as S
    else -> r as S
}

/**
 * Thrown by [getOrThrow] when the result is on the Failure rail. Carries the typed
 * failure payload as [error] (erased to `Any?` — the failure type is not available at
 * the `Res<S, *>` receiver). A Defect, by contrast, rethrows its own throwable.
 */
public class FailureException(
    public val error: Any?,
) : IllegalStateException("Res is Failure($error)")

/**
 * The ok value, or throw. A defect re-throws its original throwable; a failure throws
 * a [FailureException] carrying the payload (recoverable via [FailureException.error]).
 *
 * @sample tech.codingzen.res.getOrThrowSample
 */
public fun <S> Res<S, *>.getOrThrow(): S = when (val r = raw) {
    is Defect -> throw r.throwable
    is Failed<*> -> throw FailureException(r.error)
    is OkBox -> r.value as S
    else -> r as S
}
