package tech.codingzen.res

/**
 * Run [block] and capture a thrown exception onto the **Defect** rail — the boundary
 * adapter for throwing JVM/stdlib APIs (`Files.readString`, `Integer.parseInt`, JDBC, …)
 * that you want to chain on as a [Res].
 *
 * Same seal policy as every combinator (it delegates to the same helper): a `RailHalt`
 * (an enclosing [rail] short-circuit) and a fatal throw ([isFatal]) are rethrown; any other
 * throw becomes a Defect. The result type is `Res<S, Nothing>` because nothing ever lands on
 * the Failure rail here — use the two-arg overload when a throw should be a *typed* Failure.
 *
 * @sample tech.codingzen.res.catchingSample
 */
public inline fun <S> catching(block: () -> S): Res<S, Nothing> =
    sealToDefect { ok(block()) }

/**
 * Run [block] and map a thrown exception onto the **Failure** rail via [transform] — for
 * *expected* errors that belong in the type `F` (a malformed env var, bad user input).
 * This is the throw-to-Failure bridge that [rail] deliberately does not offer: inside `rail`
 * a throw always routes to Defect.
 *
 * A `RailHalt` and a fatal throw ([isFatal], incl. `CancellationException`) are rethrown. A
 * non-fatal throw is passed to [transform] and the result becomes a Failure; if [transform]
 * itself throws non-fatally, that lands on the Defect rail (a mapper bug is not a domain error).
 *
 * @sample tech.codingzen.res.catchingMappedSample
 */
public inline fun <S, F> catching(transform: (Throwable) -> F, block: () -> S): Res<S, F> =
    try {
        ok(block())
    } catch (h: RailHalt) {
        throw h
    } catch (e: Throwable) {
        if (isFatal(e)) throw e else sealToDefect { fail(transform(e)) }
    }
