package tech.codingzen.res

/**
 * Persistent immutable stack of [Frame]s carried by the [Failed] marker (`head` =
 * outermost / most-recently-pushed). A curated, cheap stand-in for the JVM stack trace
 * the Failure rail does not have.
 *
 * Append is **O(1)** with structural sharing — [push] allocates one node pointing at the
 * existing chain; nothing is copied. (A `List<Frame>` would copy on every append → O(n²)
 * for an n-deep chain.) Materialization to a `List` is O(n) but happens only at read time
 * ([toFrameList], the rendering helpers) — never while building.
 *
 * `@PublishedApi internal` so the public `inline` [context]/`withFrame` can reference it.
 */
@PublishedApi
internal class FrameChain private constructor(
    @JvmField val frame: Frame,
    @JvmField val next: FrameChain?,
    @JvmField val size: Int,
) {
    @PublishedApi
    internal companion object {
        /** Push [frame] as the new outermost frame. O(1) — shares [chain] as the tail. */
        @PublishedApi
        internal fun push(chain: FrameChain?, frame: Frame): FrameChain =
            FrameChain(frame, chain, (chain?.size ?: 0) + 1)
    }
}

/** Materialize innermost-first (index 0 = closest to the error). O(n); read-time only. */
@PublishedApi
internal fun FrameChain?.toFrameList(): List<Frame> {
    val n = this?.size ?: 0
    if (n == 0) return emptyList()
    val arr = arrayOfNulls<Frame>(n)
    var node: FrameChain? = this
    var i = n - 1
    while (node != null) {
        arr[i--] = node.frame
        node = node.next
    }
    @Suppress("UNCHECKED_CAST")
    return (arr as Array<Frame>).asList()
}

/**
 * Prepend every frame of [outer] onto [inner] (outer's innermost ends up just above inner's
 * head). Used by [orElse] so an outer breadcrumb trail survives a failed recovery attempt.
 * O(|outer|) and only on the double-failure path.
 */
@PublishedApi
internal fun concatFrames(inner: FrameChain?, outer: FrameChain?): FrameChain? {
    if (outer == null) return inner
    var chain = inner
    for (f in outer.toFrameList()) chain = FrameChain.push(chain, f)
    return chain
}
