package tech.codingzen.res

/**
 * A three-rail result of a computation:
 *
 *  - **Ok** — the happy path. Stored unboxed: holding, chaining, and returning an
 *    ok value allocates nothing.
 *  - **Failure** — a typed, domain error developers produce via [fail]. Carries a
 *    polymorphic payload [F].
 *  - **Defect** — an uncaught [Throwable] (JVM reality). A *hidden* third channel:
 *    it does not appear in the type signature, only surfacing at terminal ops such
 *    as [fold] / [defectOrNull] / [getOrThrow].
 *
 * Type parameters are **ok-first, failure-second**: `Res<S, F>` where `S` is the ok
 * type and `F` the failure type. The value class wraps a single `Any?` slot:
 * an ok value is stored raw, while failure and defect are tagged with the internal
 * [Err] markers (mandatory — generics are erased, so the non-ok rails must be
 * distinguishable at runtime).
 */
@JvmInline
public value class Res<out S, out F> @PublishedApi internal constructor(
    @PublishedApi internal val raw: Any?,
) {
    /** True when this is the Ok rail. Single `instanceof` on the hot path. */
    public val isOk: Boolean get() = raw !is Err

    /** True when this is the Failure rail (a developer-produced typed error). */
    public val isFailure: Boolean get() = raw is Failed<*>

    /** True when this is the Defect rail (an uncaught [Throwable]). */
    public val isDefect: Boolean get() = raw is Defect

    override fun toString(): String = when (val r = raw) {
        is Failed<*> -> "Failure(${r.error})"
        is Defect -> "Defect(${r.throwable})"
        is OkBox -> "Ok(${r.value})"
        else -> "Ok($r)"
    }
}

/**
 * Sealed marker for the two non-ok rails. Implementing a single interface lets
 * [Res.isOk] be one `instanceof` check rather than two.
 */
@PublishedApi
internal sealed interface Err

/**
 * Tags the Failure rail, wrapping the typed error payload and an optional chain of
 * context [frames][Frame] (null = none). Frames are observability metadata: [equals] /
 * [hashCode] intentionally ignore them, so two failures with the same payload stay equal
 * regardless of the breadcrumbs attached. See [context] / `withFrame` for how frames land.
 */
@PublishedApi
internal class Failed<out F>(
    @JvmField val error: F,
    @JvmField val frames: FrameChain? = null,
) : Err {
    override fun equals(other: Any?): Boolean = other is Failed<*> && error == other.error
    override fun hashCode(): Int = error?.hashCode() ?: 0
    override fun toString(): String =
        if (frames == null) "Failed($error)" else "Failed($error, frames=${frames.toFrameList()})"
}

/** Tags the Defect rail, wrapping the uncaught throwable. */
@PublishedApi
internal class Defect(@JvmField val throwable: Throwable) : Err {
    override fun equals(other: Any?): Boolean = other is Defect && throwable == other.throwable
    override fun hashCode(): Int = throwable.hashCode()
    override fun toString(): String = "Defect($throwable)"
}

/**
 * Boxes an ok value whose runtime type is itself an [Err] or an [OkBox], so it is
 * not misread as a failure/defect. Only reachable via nested results / direct use
 * of the internal markers — normal ok values are never boxed. Mirrors
 * `kotlin.Result`'s `Failure`-collision handling.
 */
@PublishedApi
internal class OkBox(@JvmField val value: Any?) {
    override fun equals(other: Any?): Boolean = other is OkBox && value == other.value
    override fun hashCode(): Int = value?.hashCode() ?: 0
    override fun toString(): String = "OkBox($value)"
}

/**
 * Construct an Ok. The value is stored unboxed unless it collides with a marker.
 *
 * @sample tech.codingzen.res.okSample
 */
public fun <S> ok(value: S): Res<S, Nothing> =
    Res(if (value is Err || value is OkBox) OkBox(value) else value)

/**
 * Construct a Failure carrying the typed error [error].
 *
 * @sample tech.codingzen.res.failSample
 */
public fun <F> fail(error: F): Res<Nothing, F> = Res(Failed(error))

/**
 * Construct a Defect carrying the uncaught [throwable]. **Internal plumbing**, not
 * part of the public surface: the Defect rail is the *hidden* channel — a throwable
 * only ever *arrives* there by being thrown, never deliberately constructed. This
 * backs the seal in [rail] / [sealToDefect] and the re-tag in [mapDefect]; it is
 * `@PublishedApi internal` only so those public `inline` functions can reference it.
 * To land a throwable on the Defect rail from outside, throw it inside a [rail] block.
 */
@PublishedApi
internal fun defect(throwable: Throwable): Res<Nothing, Nothing> = Res(Defect(throwable))
