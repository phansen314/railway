# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A railway-oriented **result type** for Kotlin/JVM. The real library is the
`:result` module (package `tech.codingzen.res`); `:app` and `:utils` are
leftover Gradle multi-module scaffold and are not part of the library.

**Before writing code against the library, read [`AGENTS.md`](AGENTS.md)** — it is
the authoritative usage guide (3-rail mental model, the non-obvious rules, the full
API map, examples). This file is about *developing* the library; AGENTS.md is about
*using* it.

## Commands

```bash
./gradlew :result:build                 # compile (strict explicitApi) + run tests
./gradlew :result:test                  # tests only
./gradlew :result:test --tests "tech.codingzen.res.SugarTest"            # one class
./gradlew :result:test --tests "tech.codingzen.res.RailTest.*bind*"      # one method (glob)
./gradlew apiCheck                       # public API matches result/api/result.api
./gradlew apiDump                        # regenerate result/api/result.api (commit the diff)
./gradlew :result:dokkaGenerate          # HTML docs (build/dokka/html) with @sample snippets
```

Test framework is `kotlin.test` on JUnit Platform (configured in the `buildSrc`
convention plugin). JVM toolchain is 21; Kotlin 2.3.0.

## Architecture — the encoding is the whole design

`Res<S, F>` is a `@JvmInline value class` wrapping one `Any?` slot (`raw`). Three
rails are distinguished by the runtime shape of `raw` — understanding this unlocks
every source file:

- **Ok** → `raw` *is the value itself*, stored unboxed (0-alloc happy path).
- **Failure** → `raw` is an internal `Failed<F>` marker.
- **Defect** → `raw` is an internal `Defect` marker (a hidden `Throwable` rail, not
  in the type signature).

`Failed` and `Defect` implement a sealed marker interface **`Err`**, so the hot-path
test is one instanceof: `isOk ⇔ raw !is Err`. An Ok value that is *itself* an `Err`
(only via nested `Res`) is wrapped in `OkBox` to avoid collision — mirrors
`kotlin.Result`'s strategy. All of this lives in `Res.kt`; the unwrap helper
`unwrapOk` (in `Combinators.kt`) reverses the boxing.

These markers are `@PublishedApi internal` (not truly public) so the public `inline`
functions can reference them. That is also why they appear as `public` in
`result/api/result.api` — that file is the binary ABI, not the intended API.

### Two invariants every combinator upholds

1. **Throw → Defect.** Any user lambda (`map`, `flatMap`, taps, `zip`, defect
   handlers, …) runs inside the `sealToDefect` helper (`Combinators.kt`), which routes
   a thrown `Throwable` to the Defect rail — *unless* it is a `RailHalt` (the
   `rail { }` short-circuit, always **rethrown** so the owning rail handles it) or
   `isFatal(e)` (`Fatal.kt`: `VirtualMachineError`, `LinkageError`,
   `InterruptedException`, `CancellationException`), which always rethrows. When adding
   any combinator that runs a lambda, keep the fast `if (raw is Err) return …`
   pass-through, then wrap the lambda-running path in `sealToDefect { … }` — do not
   hand-roll the `try/catch` (the original copy-paste omitted the `RailHalt` arm and
   silently turned Failures into Defects). `rail { }` itself inlines the same policy.
2. **Pass-through.** A combinator touches only its own rail and returns the other
   rails unchanged (e.g. `mapFailure` ignores Ok/Defect; `catchAll` ignores Ok/Failure).

### The `rail { }` DSL

`rail { }` (in `Rail.kt`) is an `inline` builder with a `Rail<F>` receiver. Inside,
`res.bind()` unwraps Ok or short-circuits; `raise(e)` aborts with a Failure. The
short-circuit jump is **`RailHalt`**, a no-stacktrace `RuntimeException`
(`RuntimeException(null,null,false,false)` + `fillInStackTrace()=this`) deliberately
*not* a `CancellationException`. Each `rail { }` allocates a fresh `Rail` whose
identity is the **owner token**: the builder only catches a `RailHalt` whose
`owner === scope`, otherwise rethrows — this isolates nested `rail { }` blocks.
`Rail` is `@DslMarker`-annotated (`RailDsl`), so nested blocks must use explicit
`this@rail` to reach an outer scope.

### File layout of `:result`

`Res.kt` (type + markers + `ok`/`fail`/`defect`), `Fatal.kt` (`isFatal`),
`Combinators.kt` (map/flatMap/fold/recover/terminal ops + `unwrapOk`), `Rail.kt`
(DSL + `withFrame`), `Guards.kt` (`ensure`/`ensureNotNull`), `Taps.kt` (`onOk`/`onFailure`/`onDefect`),
`Defect.kt` (`catchAll`/`catch<T>`/`recoverDefect`/`mapDefect`), `Zip.kt`,
`Frame.kt` (`Frame`/`SourceLocation`), `FrameChain.kt` (internal persistent frame stack),
`Context.kt` (`context`/`contextFrame`), `ContextRendering.kt` (`contextChain`/`renderContext`/
`contextSummary`/`contextMap`/`findAttachment`).
Files that cast through the erased `Any?` slot start with
`@file:Suppress("UNCHECKED_CAST")`.

### Context frames (Failure rail only)

`Failed<F>` holds an optional `FrameChain?` (head = outermost; O(1) structural-sharing
append, materialized to `List<Frame>` only at read time). `.context { }` pushes a breadcrumb
and **no-ops on Ok and Defect** (lambda unevaluated) — a Defect carries a real `Throwable`, so
it needs no frames; this Failure-only scope is what keeps the feature small (no exception
bridging, unlike the predecessor project). `mapFailure` preserves frames across a payload
change; `orElse` merges the original's frames onto a failed alternative. `Rail.withFrame`
rewraps a *same-owner* `RailHalt`'s `Failed` marker with one extra frame, passing Defects and
nested-rail halts through untouched. Frames are metadata: `Failed.equals`/`hashCode` ignore them.

## Conventions specific to this repo

- **Rail terminology is Ok / Failure / Defect** (not Success). `Res<S, F>` is
  **ok-first, failure-second** (reversed from ZIO/Either, intentional).
- **No experimental Kotlin APIs.** Notably `kotlin.contracts` is avoided, so
  `ensureNotNull` *returns* the narrowed value instead of smart-casting.
- **`explicitApi()` is strict on `:result` only** (in `result/build.gradle.kts`, not
  the shared convention plugin) — every public declaration needs explicit visibility
  and return type.
- **Public API changes must be intentional.** After changing the public surface,
  run `apiDump` and commit the updated `result/api/result.api`, or `apiCheck` fails.
- **Tests live in-package** (`package tech.codingzen.res`) so they can assert on the
  internal markers (e.g. `OkBox`, `RailHalt`); new tests should too when they need that.
- **KDoc `@sample` references compiled snippets** in `result/src/samples/kotlin`,
  attached to the test compilation. Add a sample there before referencing it.
- Dokka uses the **V2** plugin, enabled via the `pluginMode` flag in
  `gradle.properties` (build-tooling only; does not affect the artifact).
