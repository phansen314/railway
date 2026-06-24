# railway — agent guide

A railway-oriented result type for Kotlin/JVM. Read this before generating or
editing code that uses the `:result` library. Package: `tech.codingzen.res`.
The exhaustive, always-current signature list is `result/api/result.api`.

## Mental model — THREE rails

`Res<S, F>` is a `@JvmInline value class` (a single `Any?` slot — zero allocation
on the happy path). Type params are **ok-first, failure-second**: `S` = ok type,
`F` = failure type.

| Rail | What | How it arises | In the type? |
|------|------|---------------|--------------|
| **Ok** | happy path, value of type `S`, stored unboxed | `ok(v)` | yes (`S`) |
| **Failure** | a typed, *expected* domain error of type `F` | `fail(e)` / `raise(e)` | yes (`F`) |
| **Defect** | an *unexpected* `Throwable` | a thrown exception (auto-routed) | **NO — hidden** |

### ⚠️ The things you cannot infer from the signature

1. **The Defect rail is invisible.** `Res<Int, String>` can still be a Defect.
   Never assume "not Ok ⇒ Failure". Handle defects at a terminal op:
   `fold` (3 arms force it), `getOrThrow` (rethrows it), or `defectOrNull`.
   This includes `Res<S, Nothing>` (e.g. from `recover`): `Nothing` means the
   *Failure* rail is empty, **not** that the result is infallible — a defect can
   still pass through or be thrown by the recover lambda. `Nothing` failure ≠
   cannot-fail.
2. **Throws auto-route to Defect.** Any non-fatal `Throwable` thrown inside a
   lambda passed to `map`/`flatMap`/`rail`/taps/etc. is caught and becomes a
   Defect — it does not propagate. You don't wrap lambdas in try/catch.
3. **Fatal throwables always propagate** (never become a Defect):
   `VirtualMachineError`, `LinkageError`, `InterruptedException`,
   `kotlin.coroutines.cancellation.CancellationException`. Do not try to "recover"
   these inside a combinator — they fly straight out.
4. **The general defect handler is `catchAll`, not `catch`.** Plain `catch` does
   not exist; `catch<T : Throwable> { }` (explicit type arg) is the *typed
   selective* form. Use `catchAll { }` to handle any defect.
5. **`ensureNotNull` returns the value** (no smart-cast — the library avoids
   experimental `kotlin.contracts`): `val v = ensureNotNull(x) { err }`.
6. **`getOrNull()` is rail-ambiguous.** It returns `null` on the Failure/Defect rails
   **and** for `ok(null)` — a `null` tells you nothing about the rail. When `null` is a
   valid ok value, use `fold` / `isOk` / `getOrThrow` instead.

> Note: `result/api/result.api` lists `Err`/`Failed`/`Defect`/`OkBox`/`RailHalt`/
> `isFatal`/`unwrapOk`/`sealToDefect`/`defect` as public — that is internal plumbing
> (`@PublishedApi`, needed to back the inline functions). **Do not call them.** Use the
> functions below. (`catch<T>` is inline-reified, so it has no entry in that file.)
> There is **no public way to construct a Defect**: throw inside a `rail { }` block to
> land a throwable on the Defect rail.

## API map

Construction
| Call | Result | Meaning |
|------|--------|---------|
| `ok(value: S)` | `Res<S, Nothing>` | Ok rail (value stored unboxed) |
| `fail(error: F)` | `Res<Nothing, F>` | Failure rail |

There is no public Defect constructor: a Defect only *arises* from a thrown
exception (e.g. inside `rail { }` / `map { }`), it is never built deliberately.

Capture — adapt a throwing call at the boundary
| `catching { s }` → `Res<S, Nothing>` (throw → Defect) · `catching({ t -> f }) { s }` → `Res<S, F>` (throw → typed Failure) |

Both rails
| `mapBoth(onOk, onFailure)` → `Res<S2, F2>` (Defect passes through; frames preserved) · `swap()` → `Res<F, S>` (Ok↔Failure; Defect stays; frames dropped) |

State (predicates)
| `isOk` · `isFailure` · `isDefect` → `Boolean` |

Ok rail
| `map { s -> s2 }` → `Res<S2, F>` · `flatMap { s -> Res<S2,F> }` → `Res<S2, F>` · `flatten()` (on `Res<Res<S,F>,F>`) · `filterOrElse({ s -> Boolean }) { s -> f }` → `Res<S, F>` (demote a failing Ok) |

Failure rail
| `mapFailure { f -> f2 }` → `Res<S, F2>` · `recover { f -> s }` → `Res<S, Nothing>` (not infallible — see caveat 1) · `orElse { f -> Res<S,F2> }` → `Res<S, F2>` |

Defect rail (Ok/Failure pass through untouched)
| `catchAll { t -> Res<S,F> }` · `catch<T> { t: T -> Res<S,F> }` (typed, explicit arg) · `recoverDefect { t -> s }` · `mapDefect { t -> Throwable }` |

Taps — run a side effect on the matching rail, return `this`
| `onOk { }` · `onFailure { }` · `onDefect { }` |

Combine — fail-fast, left-biased (first non-ok wins)
| `zip(a, b) { x, y -> r }` (+ arity 3, 4) → `Res<R, F>` · `Iterable<Res<S,F>>.sequence()` → `Res<List<S>, F>` · `Iterable<T>.traverse { t -> Res<S,F> }` → `Res<List<S>, F>` |

Terminal / elimination
| `fold(onOk, onFailure, onDefect)` → `R` · `getOrNull()` · `failureOrNull()` · `defectOrNull()` · `getOrElse { s }` · `getOrDefault(s)` (eager fallback) · `getOrThrow()` (rethrows defect; `FailureException` carrying the payload on failure) |

DSL — `rail { }` builder, receiver `Rail<F>`
| `rail<S,F> { ... }` → `Res<S, F>` · inside: `res.bind(): S` (unwrap or short-circuit) · `raise(error: F): Nothing` · `ensure(cond) { error }` · `ensureNotNull(x) { error }: S` |

## Decision guide

- **`map` vs `flatMap`** — `map` when your transform returns a plain value;
  `flatMap` when it returns another `Res`.
- **`fail`/`raise` (Failure) vs throw (Defect)** — use Failure for *expected* errors
  callers should handle (it's in the type `F`). Let exceptions become Defects for
  *bugs / unexpected* conditions; you never build a Defect by hand.
- **`rail { }` vs chaining `flatMap`** — use `rail { }` for multi-step imperative
  flows: `bind()` each step, `raise`/`ensure` to abort. Use combinator chains for
  single transforms.
- **`zip(a, b) { }` vs `rail { f(a.bind(), b.bind()) }`** — identical semantics
  (both fail-fast, left-biased). `zip` is the zero-alloc fixed-arity shortcut;
  `rail`+`bind` is arity-free (any N) at the cost of one scope-token allocation.
- **`catchAll` vs `catch<T>`** — `catchAll` for any defect; `catch<IOException>`
  to handle one type and let others pass through.
- **`catching` vs `rail { }`** — `catching` wraps a *throwing* call at the boundary:
  `catching { f() }` sends a throw to Defect (like `rail`), but `catching({ e -> … }) { f() }`
  sends it to a typed **Failure** — which `rail` cannot do (inside `rail` a throw is always a
  Defect). Use `catching` for expected exceptions that belong in `F`.
- **`sequence`/`traverse` vs a hand-rolled `rail` loop** — `traverse(xs) { f(it) }` is the
  named, fail-fast `rail { xs.map { f(it).bind() } }`. Reach for it for batch validation /
  bulk loads; same left-biased short-circuit as `zip`.
- **`filterOrElse` vs `flatMap`** — `filterOrElse({ p(it) }) { err(it) }` is the named form of
  `flatMap { if (p(it)) ok(it) else fail(err(it)) }` — a post-success business rule.
- **Nested `rail { }`** — `Rail` is `@DslMarker`-annotated, so an inner block
  cannot *implicitly* call the outer scope's `bind`/`raise`. To target the outer
  scope from inside a nested block, qualify explicitly: `this@rail.raise(...)`.

### ⚠️ Don't catch the rail short-circuit

`bind()` / `raise()` short-circuit by throwing `RailHalt`, a plain `RuntimeException`
(deliberately *not* a `CancellationException`). A **broad** catch inside the block
swallows it — the block then runs to completion as if the bind succeeded, and the
intended Failure/Defect silently vanishes:

```kotlin
rail<Int, String> {
    try { fail<String>("x").bind() } catch (e: Exception) { /* swallows the halt */ }
    99
}   // → Ok(99) — the "x" Failure is gone
```

Rule: never wrap a `bind()` / `raise()` in `catch (e: Exception)` /
`catch (e: RuntimeException)` / `catch (e: Throwable)`. Catch *narrowly* — the specific
exception you expect (e.g. `catch (e: IOException)`); a narrow catch never matches
`RailHalt`.

## Canonical examples

```kotlin
import tech.codingzen.res.*

// Construct + chain
val n: Res<Int, String> = ok("21").map { it.toInt() }.map { it * 2 }   // Ok(42)

// Imperative flow with rail + bind + ensure
fun parse(s: String): Res<Int, String> =
    s.toIntOrNull()?.let { ok(it) } ?: fail("not a number: $s")

val sum: Res<Int, String> = rail {
    val a = parse("20").bind()        // unwrap, or short-circuit the whole block
    val b = parse("22").bind()
    ensure(a + b > 0) { "must be positive" }
    a + b
}                                     // Ok(42)

// Handle every rail (forces you to deal with the hidden Defect)
val msg: String = sum.fold(
    onOk      = { "ok: $it" },
    onFailure = { "failure: $it" },
    onDefect  = { "defect: ${it.message}" },
)

// Recover a defect (the defect arises from a thrown exception, auto-routed)
val safe: Res<Int, String> = ok("x").map { error("boom") }.catchAll { ok(-1) }

// Combine
val combined: Res<Int, String> = zip(ok(20), ok(22)) { x, y -> x + y }   // Ok(42)
```

## Coroutines / suspend

There is **no separate suspend API** — and you don't need one. Every combinator and
`rail { }` is `inline`, so a suspend lambda works wherever you're already in a `suspend`
function. Just call suspend functions directly:

```kotlin
suspend fun loadOrder(id: Int): Res<Order, String> = rail {
    val user  = fetchUser(id).bind()        // fetchUser is suspend
    val cart  = fetchCart(user).bind()      // suspends; bind short-circuits as usual
    Order(user, cart)
}

// combinator chains work too, from any suspend context:
suspend fun price(id: Int): Res<Money, String> =
    fetchUser(id).flatMap { fetchCart(it) }.map { total(it) }   // both lambdas suspend
```

- **No `railS`, no `mapS`** — the inline functions inherit the suspend-ness of the call site.
- **No runtime dependency** — `kotlinx-coroutines` is test-only; the artifact stays zero-dep.
- **Cancellation is safe.** `CancellationException` is fatal (see `isFatal`), so a cancel at
  a suspension point **propagates** — it is never sealed into a Defect. (Consequence of the
  same "don't catch the short-circuit" rule above: the seal only ever catches *non-fatal*
  throws.)
- **Sequential only.** No `parZip`/`parTraverse` — for concurrency, drive coroutines
  yourself (`coroutineScope`/`async`) and `bind()` the awaited `Res` values inside `rail`.

## Frames — error context on the Failure rail

A `Failure` can carry an immutable stack of **context frames** (breadcrumbs) so a deep typed
error is debuggable without a stacktrace. **Failure rail only:** `.context { }` is a no-op on
Ok *and Defect* — a Defect already carries a real `Throwable` + JVM stacktrace, so it needs no
breadcrumbs. The lambda runs only on the Failure path (zero Ok/Defect cost).

Attach
| `res.context { "loading user $id" }` → `Res<S,F>` (push a breadcrumb) · `res.context({ "msg" }, { SourceLocation("f.kt", 42) })` (with location) · `res.contextFrame { Frame("msg", attachment = id) }` (structured attachment) |

Frames survive `map`/`flatMap`/`mapFailure`/`orElse` and ride through a `bind()` short-circuit,
so the two idioms for adding context inside a `rail { }` are:

```kotlin
rail<Int, String> {
    // 1. per-result: .context{} composes with bind() for free (frames live in the marker)
    val u = loadUser(id).context { "loading user $id" }.bind()
    // 2. per-region: withFrame annotates ANY failure that escapes the block (not Defects,
    //    not a nested rail's failure)
    withFrame("rendering page") {
        val p = renderProfile(u).bind()
        val f = renderFeed(u).bind()
        p + f
    }
}
```

Read (all empty/blank on Ok/Defect; frames are innermost-first except `contextSummary`)
| `contextChain(): List<Frame>` · `renderContext(): String` (multi-line) · `contextSummary(): String` (`"outer → inner → Error"`) · `contextMap(): Map<String,Any?>` (JSON-shaped) · `List<Frame>.findAttachment<T>(): T?` |

`SourceLocation` is supplied by the caller (a literal or cheap lambda) — there is no
stack-walking. Frames are **metadata**: two failures with the same payload are equal regardless
of attached frames.

## Build / verification

- `./gradlew :result:build` — compiles (strict `explicitApi()`) + runs tests.
- `./gradlew apiCheck` — public API matches `result/api/result.api`
  (run `./gradlew apiDump` and commit the diff when you intentionally change the
  public surface — otherwise `apiCheck` fails CI).
- `./gradlew :result:dokkaGenerate` — HTML docs with compiled `@sample` snippets
  (samples live in `result/src/samples/kotlin`, type-checked with the tests).
