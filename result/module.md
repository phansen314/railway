# Module result

**railway** — a railway-oriented result type for Kotlin/JVM. `Res<S, F>` is a zero-allocation
`@JvmInline value class` modelling three rails: **Ok** (the value, stored unboxed), **Failure**
(a typed, expected domain error of type `F`), and **Defect** (an unexpected `Throwable`, a hidden
channel not shown in the type signature).

New here? Read [`TUTORIAL.md`](https://github.com/phansen314/railway/blob/main/TUTORIAL.md) for a
guided introduction, or [`AGENTS.md`](https://github.com/phansen314/railway/blob/main/AGENTS.md)
for the reference: the 3-rail mental model, the non-obvious rules, the full API map, and examples.

# Package tech.codingzen.res

The entire public API. Construct with [ok][tech.codingzen.res.ok] / [fail][tech.codingzen.res.fail];
transform on the Ok rail ([map][tech.codingzen.res.map] / [flatMap][tech.codingzen.res.flatMap] /
[filterOrElse][tech.codingzen.res.filterOrElse]); recover on the Failure rail
([recover][tech.codingzen.res.recover] / [orElse][tech.codingzen.res.orElse] /
[mapFailure][tech.codingzen.res.mapFailure]); narrow the hidden Defect rail
([catchAll][tech.codingzen.res.catchAll] / `catch<T>` / [recoverDefect][tech.codingzen.res.recoverDefect]);
combine ([zip][tech.codingzen.res.zip] / [sequence][tech.codingzen.res.sequence] /
[traverse][tech.codingzen.res.traverse]); capture throwing code at the boundary
([catching][tech.codingzen.res.catching]); or write imperative flows with the
[rail][tech.codingzen.res.rail] `{ }` builder (`bind` / `raise` / `ensure`). Eliminate with
[fold][tech.codingzen.res.fold] / [getOrNull][tech.codingzen.res.getOrNull] /
[getOrThrow][tech.codingzen.res.getOrThrow].

**Two invariants hold across every combinator:** a thrown non-fatal exception is routed to the
Defect rail (fatal throws and `CancellationException` always propagate), and each combinator
touches only its own rail, passing the others through unchanged. Everything is `inline`, so it all
composes unchanged inside `suspend` functions.
