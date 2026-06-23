@file:Suppress("UNCHECKED_CAST")

package tech.codingzen.res

/**
 * Fast control-flow exception used to short-circuit a [rail] block. It is
 * deliberately a plain [RuntimeException] — **not** a `CancellationException` —
 * so coroutine machinery never swallows it.
 *
 * The 4-arg `RuntimeException` ctor disables the message, cause, suppression, and
 * writable stack trace; [fillInStackTrace] is overridden to return `this`. Together
 * that makes raising one allocation-cheap with **zero stacktrace-fill cost** on the
 * short-circuit path — this jump is hot, not exceptional.
 *
 * [owner] is the [Rail] instance that opened the block, used to isolate nested
 * `rail { }` blocks: a halt is only caught by the rail that owns it. [err] is the
 * already-built [Err] marker ([Failed] from `raise`/a bound failure, or [Defect]
 * from a bound defect) that the builder rebuilds into a `Res`.
 */
@PublishedApi
internal class RailHalt(
    @JvmField val owner: Any,
    @JvmField val err: Err,
) : RuntimeException(null, null, false, false) {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Restricts implicit-receiver access inside nested [rail] blocks: when two `Rail`
 * receivers are in scope, only the innermost is implicitly accessible. Reaching an
 * outer scope's [Rail.bind]/[Rail.raise] then requires an explicit qualifier
 * (`this@rail`), turning an accidental cross-scope short-circuit into a compile error.
 */
@DslMarker
public annotation class RailDsl

/**
 * Receiver scope of a [rail] block. The error type [F] matches `Res<S, F>`. Each
 * `rail { }` call allocates a fresh `Rail`, whose identity serves as the
 * short-circuit owner token (see [RailHalt]).
 */
@RailDsl
public class Rail<F> @PublishedApi internal constructor() {

    /**
     * Unwrap the Ok value, or short-circuit this rail with the Failure/Defect.
     * On a non-ok rail this throws a [RailHalt] carrying the original [Err] marker
     * unchanged, so a bound defect stays a defect and a bound failure stays a failure.
     *
     * **Caveat:** the short-circuit is a [RailHalt] (a `RuntimeException`). A broad
     * `try { … } catch (e: Exception)` around a `bind()` will swallow it — see [rail].
     *
     * @sample tech.codingzen.res.bindSample
     */
    public fun <S> Res<S, F>.bind(): S {
        val r = raw
        if (r is Err) throw RailHalt(this@Rail, r)
        return unwrapOk(r) as S
    }

    /**
     * Abort this rail with a typed [Failure][Failed], producing `Res<…, F>` failure.
     *
     * @sample tech.codingzen.res.raiseSample
     */
    public fun raise(error: F): Nothing = throw RailHalt(this@Rail, Failed(error))
}

/**
 * Run [block] and, if a Failure of *this* rail short-circuits out of it, push a context
 * [message] frame onto that failure before it propagates — annotating a whole region rather
 * than a single result. The complement of `res.context { … }.bind()`.
 *
 * Only failures owned by this rail are touched: a short-circuit from a nested `rail { }`
 * (different owner) and a **Defect** (carries its own [Throwable] + stacktrace) pass through
 * unchanged. On the Ok path [block] returns normally and nothing is allocated.
 *
 * @sample tech.codingzen.res.withFrameSample
 */
public inline fun <S, F> Rail<F>.withFrame(message: String, block: Rail<F>.() -> S): S =
    try {
        block()
    } catch (h: RailHalt) {
        val e = h.err
        if (h.owner === this && e is Failed<*>) {
            throw RailHalt(this, Failed(e.error, FrameChain.push(e.frames, Frame(message))))
        } else {
            throw h
        }
    }

/** [withFrame] with a source [location] (evaluated only when a failure escapes [block]). */
public inline fun <S, F> Rail<F>.withFrame(
    message: String,
    location: () -> SourceLocation,
    block: Rail<F>.() -> S,
): S =
    try {
        block()
    } catch (h: RailHalt) {
        val e = h.err
        if (h.owner === this && e is Failed<*>) {
            throw RailHalt(this, Failed(e.error, FrameChain.push(e.frames, Frame(message, location = location()))))
        } else {
            throw h
        }
    }

/**
 * Run [block] as an imperative, short-circuiting computation and collapse it to a
 * [Res]. Unwrap intermediate results with [Rail.bind] and abort early with
 * [Rail.raise]; the first short-circuit ends the block.
 *
 * A non-fatal throw inside [block] is routed to the Defect rail (same policy as
 * [map]/[flatMap]); a fatal throw (see [isFatal], incl. `CancellationException`)
 * propagates. Nested `rail { }` blocks are isolated: a [RailHalt] is only handled
 * by the rail that owns it, others are rethrown to their true owner.
 *
 * **Caveat — do not broadly catch inside the block.** The short-circuit of
 * [Rail.bind]/[Rail.raise] is a [RailHalt], a plain `RuntimeException` (deliberately
 * *not* a `CancellationException`). A wide `catch (e: Exception)` /
 * `catch (e: RuntimeException)` / `catch (e: Throwable)` wrapped around a `bind()`
 * will **swallow the halt**, and the block silently continues as if the bind
 * succeeded — the intended Failure/Defect is lost. Catch *narrowly* (the specific
 * exception you expect); a narrow catch never matches `RailHalt`.
 *
 * @sample tech.codingzen.res.railSample
 */
public inline fun <S, F> rail(block: Rail<F>.() -> S): Res<S, F> {
    val scope = Rail<F>()
    return try {
        ok(scope.block())
    } catch (h: RailHalt) {
        if (h.owner === scope) Res(h.err) else throw h
    } catch (e: Throwable) {
        if (isFatal(e)) throw e else defect(e)
    }
}
