@file:Suppress("UNCHECKED_CAST")

package tech.codingzen.res

// ---------------------------------------------------------------------------
// traverse / sequence — collapse a collection of results into one result over a
// list. Fail-fast and left-biased, exactly like `zip`: the first non-ok element
// (Failure *or* Defect), scanned left → right, short-circuits and the rest are
// never inspected. An all-ok input yields `ok(List)` preserving order.
// ---------------------------------------------------------------------------

/**
 * Collapse a collection of results into one result over the list of ok values. The first
 * non-ok element (Failure or Defect) short-circuits; an all-ok input yields `ok(List)` in
 * order. An empty input is `ok(emptyList())`.
 *
 * @sample tech.codingzen.res.sequenceSample
 */
public fun <S, F> Iterable<Res<S, F>>.sequence(): Res<List<S>, F> {
    val out = ArrayList<S>()
    for (res in this) {
        val r = res.raw
        if (r is Err) return Res(r)
        out.add(unwrapOk(r) as S)
    }
    return ok(out)
}

/**
 * Map each element through [transform] and [sequence] the results: `ok(List)` of every
 * transformed value, or the first non-ok (Failure or Defect) — `transform` is not called on
 * the remaining elements once one short-circuits. A non-fatal throw in [transform] routes to
 * the Defect rail (same seal policy as [map]).
 *
 * @sample tech.codingzen.res.traverseSample
 */
public inline fun <T, S, F> Iterable<T>.traverse(transform: (T) -> Res<S, F>): Res<List<S>, F> {
    val out = ArrayList<S>()
    for (item in this) {
        val r = sealToDefect { transform(item) }.raw
        if (r is Err) return Res(r)
        out.add(unwrapOk(r) as S)
    }
    return ok(out)
}
