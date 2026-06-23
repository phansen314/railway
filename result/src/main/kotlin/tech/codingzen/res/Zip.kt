@file:Suppress("UNCHECKED_CAST")

package tech.codingzen.res

// ---------------------------------------------------------------------------
// zip — combine multiple results into one. Fail-fast and left-biased: the first
// non-ok argument (scanned left → right) short-circuits, whether it is a Failure
// or a Defect. The combine lambda is sealed (non-fatal throw → Defect).
//
// Because the first non-ok by position wins, an earlier Failure masks a later
// Defect (and vice-versa) — `Res` holds a single rail, it cannot carry both. This
// is exactly the ordering of `a.bind()` before `b.bind()` in the rail equivalent
// below: `b` is never observed once `a` short-circuits.
//
// `zip` is a zero-alloc, fixed-arity shortcut. The semantically-identical,
// arity-free equivalent is `rail { combine(a.bind(), b.bind()) }` — bind is
// left-biased and fail-fast too. Prefer that for higher or dynamic arity; the
// only difference is that `rail` allocates a scope token whereas these touch
// only the raw slots.
// ---------------------------------------------------------------------------

/**
 * Combine two results. The first non-ok (left → right) short-circuits.
 *
 * @sample tech.codingzen.res.zipSample
 */
public inline fun <A, B, F, R> zip(
    a: Res<A, F>,
    b: Res<B, F>,
    combine: (A, B) -> R,
): Res<R, F> {
    val ra = a.raw; if (ra is Err) return Res(ra)
    val rb = b.raw; if (rb is Err) return Res(rb)
    return sealToDefect { ok(combine(unwrapOk(ra) as A, unwrapOk(rb) as B)) }
}

/** Combine three results. The first non-ok (left → right) short-circuits. */
public inline fun <A, B, C, F, R> zip(
    a: Res<A, F>,
    b: Res<B, F>,
    c: Res<C, F>,
    combine: (A, B, C) -> R,
): Res<R, F> {
    val ra = a.raw; if (ra is Err) return Res(ra)
    val rb = b.raw; if (rb is Err) return Res(rb)
    val rc = c.raw; if (rc is Err) return Res(rc)
    return sealToDefect { ok(combine(unwrapOk(ra) as A, unwrapOk(rb) as B, unwrapOk(rc) as C)) }
}

/** Combine four results. The first non-ok (left → right) short-circuits. */
public inline fun <A, B, C, D, F, R> zip(
    a: Res<A, F>,
    b: Res<B, F>,
    c: Res<C, F>,
    d: Res<D, F>,
    combine: (A, B, C, D) -> R,
): Res<R, F> {
    val ra = a.raw; if (ra is Err) return Res(ra)
    val rb = b.raw; if (rb is Err) return Res(rb)
    val rc = c.raw; if (rc is Err) return Res(rc)
    val rd = d.raw; if (rd is Err) return Res(rd)
    return sealToDefect { ok(combine(unwrapOk(ra) as A, unwrapOk(rb) as B, unwrapOk(rc) as C, unwrapOk(rd) as D)) }
}
