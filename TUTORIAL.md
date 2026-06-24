# railway — a tutorial

This is a hands-on, read-top-to-bottom introduction to `railway`, a *result type*
for Kotlin/JVM. It assumes you write Kotlin but have **never** used a `Result`,
`Either`, or "railway-oriented programming" before. By the end you'll be able to
model expected errors as values, chain operations that might fail, drop into an
imperative style when that reads better, deal with the unexpected, and attach
debugging breadcrumbs to your errors.

If you just want a signature lookup or a decision table, read
[`AGENTS.md`](AGENTS.md) instead — it's the reference. This file is the *lesson*.

Everything below assumes one import:

```kotlin
import tech.codingzen.res.*
```

---

## 1. The problem: errors that hide

Kotlin gives you two built-in ways to say "this can fail," and both have a sharp edge.

**Exceptions are invisible.** Look at this signature:

```kotlin
fun loadUser(id: Int): User
```

Can it fail? You cannot tell. Maybe it throws `IOException`, maybe not — the type
says nothing, the compiler reminds you of nothing, and the day you forget to handle
it is the day it throws in production. Errors that don't appear in the type are
errors you *will* forget.

**Nullables lose the "why."** You can make failure visible:

```kotlin
fun loadUser(id: Int): User?
```

Now the compiler forces you to deal with the `null`. But which failure was it?
"No such user"? "Database down"? "Bad id"? A bare `null` threw that away. You know
*that* it failed, never *why*.

A **result type** fixes both. It puts the failure right in the return type, *and*
it carries a real description of what went wrong:

```kotlin
fun loadUser(id: Int): Res<User, String>
//                         ^^^^  ^^^^^^
//                     success    failure reason
```

`Res<S, F>` is "a success of type `S`, **or** a failure of type `F`." It's an
ordinary value you return, pass around, and transform — no `throw`, no `try`. The
type tells the truth, and the failure carries its reason.

> **Ok-first, failure-second.** The success type comes first: `Res<User, String>`
> is "ok `User`, or fail `String`." (If you've seen `Either`, that's the opposite
> order — this library is deliberately ok-first.)

---

## 2. The three rails

Picture two parallel train tracks — *rails*. A `Res` is a value riding on one of them:

- **Ok** — the happy path. Carries your value of type `S`.
- **Failure** — an *expected* error of type `F`. The kind of thing that's a normal
  part of the domain: "not found," "invalid input," "quota exceeded." You put a
  value here on purpose.

Operations run on the Ok rail and skip the Failure rail, so a failure flows
straight to the end untouched. That's the whole idea: the happy path stays clean,
the error path takes care of itself.

But there's a **third rail you don't see in the type**:

- **Defect** — an *unexpected* `Throwable`. A bug. The `null` you didn't expect, the
  arithmetic that overflowed, the `IllegalStateException` from three libraries deep.

When code inside the library throws, the exception doesn't fly out — it's caught and
parked on the **Defect** rail. Crucially, **the Defect rail is not in the type**.
`Res<Int, String>` can be Ok, Failure, *or* secretly a Defect.

> ### ⚠️ The one rule to internalize
>
> **"Not Ok" does not mean "Failure."** A Defect can always be lurking, even when
> the failure type is `Nothing`. This is *why* the eliminator you'll meet in §5
> (`fold`) makes you handle three cases, not two. Keep this in the back of your mind;
> everything else follows from it.

The split is deliberate. **Failure** = errors you model and expect callers to handle
(they're in the type). **Defect** = bugs and surprises you usually want to log, alert
on, or crash — not quietly pattern-match. You never construct a Defect by hand; one
only *arises* from a thrown exception.

---

## 3. Your first results

Two constructors. `ok` puts a value on the Ok rail; `fail` puts an error on the
Failure rail:

```kotlin
val good: Res<Int, String> = ok(42)
val bad:  Res<Int, String> = fail("not found")
```

Three predicates ask which rail you're on:

```kotlin
good.isOk       // true
bad.isFailure   // true
good.isDefect   // false
```

Let's start the example we'll grow through the whole tutorial: loading and rendering
a user profile. First step — turn a raw string id into a number, or fail with a
reason:

```kotlin
data class User(val id: Int, val name: String)

fun parseId(raw: String): Res<Int, String> =
    raw.toIntOrNull()?.let { ok(it) } ?: fail("not a number: $raw")

fun loadUser(id: Int): Res<User, String> =
    if (id == 42) ok(User(42, "Ada")) else fail("no user with id $id")

fun render(user: User): String = "Profile: ${user.name} (#${user.id})"
```

`parseId("42")` is `ok(42)`; `parseId("oops")` is `fail("not a number: oops")`.
Notice the failures are *useful strings*, not a lost `null`.

---

## 4. Chaining on the Ok rail

You rarely stop at one step. The point of a result type is to *chain* steps so that
the first failure short-circuits the rest — automatically.

Two combinators do almost all the work, and the difference between them is the whole
trick:

- **`map`** — when your transform returns a **plain value**.
- **`flatMap`** — when your transform itself returns **another `Res`**.

`render` returns a plain `String`, so it's `map`. `loadUser` returns a
`Res<User, String>`, so it's `flatMap` (otherwise you'd get a `Res` nested inside a
`Res`). Wire the example together:

```kotlin
val page: Res<String, String> =
    parseId("42")            // Res<Int, String>
        .flatMap { loadUser(it) }   // Int -> Res<User, String>   → flatMap
        .map { render(it) }         // User -> String             → map
// page == ok("Profile: Ada (#42)")
```

Now watch a failure short-circuit. Feed it a bad id:

```kotlin
parseId("oops")
    .flatMap { loadUser(it) }   // never runs
    .map { render(it) }         // never runs
// == fail("not a number: oops")
```

`parseId` lands on the Failure rail, and `flatMap`/`map` only act on the Ok rail — so
they're both skipped and the original failure flows out unchanged. You didn't write a
single `if` or `try`; the rails did the branching.

> **The rule of thumb:** does your lambda return a `Res`? Use `flatMap`. Does it
> return a plain value? Use `map`. (If you ever end up with a `Res<Res<S, F>, F>` —
> a result inside a result — `flatten()` collapses it; that usually means you wanted
> `flatMap` in the first place.)

---

## 5. Getting a value back out

Eventually you need a plain value, not a `Res`. The honest way is **`fold`** — it
forces you to handle every rail, including the hidden Defect one:

```kotlin
val message: String = page.fold(
    onOk      = { it },                       // it: String   (the rendered page)
    onFailure = { "error: $it" },             // it: String   (your failure reason)
    onDefect  = { "bug: ${it.message}" },     // it: Throwable
)
```

Three arms, no way to forget the Defect. **Make `fold` your default** for turning a
`Res` into a final answer — it's the one eliminator the compiler uses to enforce §2's
rule.

When you don't need all three branches, there are shortcuts:

```kotlin
page.getOrElse { "anonymous" }   // the ok value, or a fallback you compute
page.getOrThrow()                // the ok value, or rethrow — see below
page.getOrNull()                 // the ok value, or null  — read the caveat!
```

- `getOrElse { … }` — Ok value, or run the lambda for a default.
- `getOrThrow()` — Ok value; on a **Defect** it rethrows the original throwable, on a
  **Failure** it throws a `FailureException` carrying your error payload. Good at an
  outer boundary where you genuinely want to blow up.

> ### ⚠️ `getOrNull()` can't tell you the rail
>
> `getOrNull()` returns `null` for a Failure, `null` for a Defect — **and** `null`
> for a perfectly good `ok(null)`. If `null` is a valid success value, a `null` here
> tells you *nothing* about which rail you're on. When `null` could be legitimate,
> use `fold`, `isOk`, or `getOrThrow` instead. (When your Ok type can't be `null`,
> `getOrNull()` is fine and convenient.)

---

## 6. The `rail { }` builder — imperative when that reads better

Long `flatMap` chains can get awkward, especially when a later step needs values from
two earlier ones. For those, `railway` gives you an imperative style that reads like
ordinary sequential code but keeps the short-circuiting:

```kotlin
fun profilePage(raw: String): Res<String, String> = rail {
    val id   = parseId(raw).bind()    // unwrap the Ok value, or short-circuit
    val user = loadUser(id).bind()    // both id and user are in scope here
    render(user)                      // the last expression is the Ok value
}
```

Inside a `rail { }` block:

- **`.bind()`** unwraps an Ok value *or* aborts the entire block with that result's
  failure. `parseId(raw).bind()` is either an `Int`, or the block stops right there
  and returns the failure.
- **`raise(error)`** aborts immediately with a Failure you supply. Its return type is
  `Nothing`, so nothing after it runs:

  ```kotlin
  rail<Int, String> {
      raise("aborted")   // block stops here
      // unreachable
  }   // == fail("aborted")
  ```

- **`ensure(condition) { error }`** is a guard — if the condition is false, it
  raises your error and aborts. **`ensureNotNull(x) { error }`** does the same for
  nulls but, when `x` is non-null, **returns the unwrapped value**:

  ```kotlin
  fun profilePage(raw: String): Res<String, String> = rail {
      val id = parseId(raw).bind()
      ensure(id > 0) { "id must be positive" }
      val name = ensureNotNull(lookupName(id)) { "no name for $id" }  // name: String
      "Profile: $name (#$id)"
  }
  ```

  > Note: `ensureNotNull` **returns** the narrowed value (`val name = ensureNotNull(...)`)
  > rather than smart-casting `x` in place. That's intentional — the library avoids
  > experimental Kotlin features. Use the returned value, not the original variable.

`rail { }` and the `flatMap` chain from §4 are equivalent — same short-circuiting,
same result. Reach for `rail` when steps depend on multiple earlier values or the
flow is naturally sequential; reach for a `map`/`flatMap` chain for a single straight
pipeline. (For a quick boundary, `bind()` also works on its own: `rail { ok(40).bind() + 2 }`
is `ok(42)`.)

> ### ⚠️ Never wrap `bind()` / `raise()` in a broad `catch`
>
> The short-circuit is implemented by throwing a private control exception. A *broad*
> catch inside the block swallows it — and then the block runs to completion as if the
> bind had succeeded, silently losing the failure:
>
> ```kotlin
> rail<Int, String> {
>     try { fail<String>("x").bind() } catch (e: Exception) { /* swallows the halt */ }
>     99
> }   // → ok(99) — the "x" failure vanished!
> ```
>
> **Rule:** never put a `bind()`/`raise()` inside `catch (e: Exception)`,
> `catch (e: RuntimeException)`, or `catch (e: Throwable)`. If you must catch
> something inside a `rail` block, catch the *specific, narrow* type you expect
> (e.g. `catch (e: IOException)`) — a narrow catch never matches the control
> exception.

---

## 7. Recovering on the Failure rail

So far failures flow to the end. Sometimes you want to *handle* one mid-stream. Three
combinators act on the Failure rail (and pass Ok/Defect through untouched):

```kotlin
// Reshape the error, keep it a failure:
loadUser(99).mapFailure { "load failed: $it" }   // Res<User, String>, new message

// Provide a fallback Ok value, ending the failure:
loadUser(99).recover { User(0, "guest") }        // Res<User, Nothing>

// Try a different result entirely:
loadUser(99).orElse { loadUser(42) }             // Res<User, String>
```

- **`mapFailure`** transforms the error payload (e.g. wrap a low-level reason in a
  domain message) and stays on the Failure rail.
- **`recover`** turns a failure into an Ok value — the failure is gone.
- **`orElse`** swaps in a whole new `Res` when the first failed (a fallback lookup,
  a default source).

> ### ⚠️ `recover` returns `Res<S, Nothing>` — but that's not "cannot fail"
>
> After `recover`, the failure type is `Nothing` — there's no Failure left. But
> remember §2: a **Defect** can still ride through (the result was already a Defect,
> or your `recover` lambda itself throws). `Nothing` in the failure slot means *the
> Failure rail is empty*, **not** that the value is infallible. To be truly done with
> a `Res`, you still terminate with `fold`/`getOrThrow`/etc.

---

## 8. The Defect rail — handling the unexpected

A Defect appears the moment code throws inside one of the library's lambdas. You
don't create it; it arises:

```kotlin
val risky: Res<Int, String> = ok("x").map { error("boom") }
// `error("boom")` throws — but it doesn't propagate.
// `risky` is now a Defect, even though its type still says Res<Int, String>.
```

That thrown `IllegalStateException` was caught and parked on the Defect rail. This is
§2's invisible third rail in action.

> **Fatal throwables are never caught.** A handful of "the JVM is in trouble"
> throwables — `VirtualMachineError` (e.g. `OutOfMemoryError`), `LinkageError`,
> `InterruptedException`, and coroutine `CancellationException` — always propagate
> straight out instead of becoming a Defect. Don't try to "recover" those inside a
> combinator; they fly past it.

To deal with a Defect, narrow it back onto a rail you can reason about:

```kotlin
// Handle ANY defect:
risky.catchAll { ok(-1) }                       // → ok(-1)

// Handle ONE type, let the rest stay defects:
result.catch<IOException, Int, String> { fail("io error") }

// Turn a defect into an Ok value, or reshape the throwable:
risky.recoverDefect { -1 }
risky.mapDefect { IllegalStateException("wrapped", it) }
```

- **`catchAll { t -> Res }`** handles *any* defect. **Note the name: it's `catchAll`,
  not `catch`.** There is no plain `catch`.
- **`catch<T> { }`** is the *typed, selective* form — it handles only defects of type
  `T` and passes the rest through. Because Kotlin can't infer the result types here,
  you pass all three explicitly: `catch<IOException, Int, String> { … }` (the throwable
  type, then your Ok and Failure types).
- **`recoverDefect`** maps a defect to an Ok value; **`mapDefect`** rewraps the
  throwable while staying on the Defect rail.

And for fire-and-forget side effects on any rail — logging, metrics — the **taps**
run an action and return the original result unchanged:

```kotlin
page
    .onOk      { log.info("served {}", it) }
    .onFailure { log.warn("failed: {}", it) }
    .onDefect  { log.error("bug!", it) }
```

---

## 9. Combining independent results

When you have several *independent* results and want them all, `zip` combines them —
**fail-fast** and **left-biased** (the first non-Ok wins, and nothing to its right is
evaluated into the combine):

```kotlin
val combined: Res<Int, String> = zip(parseId("20"), parseId("22")) { a, b -> a + b }
// == ok(42)

zip(parseId("20"), parseId("nope")) { a, b -> a + b }
// == fail("not a number: nope")
```

`zip` comes in arity 2, 3, and 4. It's the fixed-arity, zero-allocation shortcut for
"combine these N results." The equivalent in `rail` is
`rail { f(a.bind(), b.bind()) }` — same fail-fast, left-biased semantics, but
arity-free (any number of binds) at the cost of one small allocation for the block.
Use `zip` for a fixed handful; reach for `rail`+`bind` when N varies or the
combination is more involved.

---

## 10. Context frames — debugging the Failure rail

Here's the production payoff. A typed failure like `"no user with id 99"` is honest,
but in a deep call stack it can be hard to know *where* it came from — and it has no
stacktrace (it's just a value). **Context frames** are breadcrumbs you attach to a
Failure as it travels up:

```kotlin
loadUser(id)
    .context { "loading user $id" }   // push a breadcrumb onto the Failure
```

`.context { }` pushes a frame **only on the Failure rail**. On an Ok or a Defect it's
a complete no-op — *the lambda never even runs*. (Why skip Defect? A Defect already
carries a real `Throwable` with a JVM stacktrace, so it doesn't need breadcrumbs.)
That makes context essentially free on the happy path.

Frames survive `map`/`flatMap`/`mapFailure`/`orElse` and ride through a `bind()`
short-circuit, so there are two natural ways to add context inside a `rail` block —
per-result with `.context { }`, or per-region with `withFrame`:

```kotlin
fun profilePage(id: Int): Res<String, String> = rail {
    // per-result: attaches "loading user N" if THIS step fails
    val user = loadUser(id).context { "loading user $id" }.bind()

    // per-region: attaches "rendering page" to ANY failure that escapes the block
    withFrame("rendering page") {
        val profile = renderProfile(user).bind()
        val feed    = renderFeed(user).bind()
        profile + feed
    }
}
```

For structured data (not just a message), `contextFrame { Frame(...) }` lets you
attach an arbitrary payload you can pull back out later:

```kotlin
loadUser(id).contextFrame { Frame("loading user", attachment = id) }
```

When the failure reaches a logging boundary, read the frames back. They're
innermost-first (except `contextSummary`, which reads outer→inner for humans):

```kotlin
val r: Res<User, String> =
    loadUser(99)
        .context { "handling request" }
        .context { "loading profile" }

r.contextSummary()   // "handling request → loading profile → no user with id 99"
r.renderContext()    // multi-line dump: each frame, its location, any attachment
r.contextChain()     // List<Frame>, innermost first — to inspect programmatically
r.contextMap()       // Map<String, Any?> — JSON-shaped, ready for structured logging
r.contextChain().findAttachment<Int>()   // pull a typed attachment back out
```

All of these are empty/blank on Ok and Defect — frames live on the Failure rail only.
And frames are pure *metadata*: two failures with the same payload are still equal
regardless of the breadcrumbs attached, so they never change your error's identity.

You can supply a source location explicitly (there's no automatic stack-walking):

```kotlin
loadUser(id).context({ "loading user $id" }, { SourceLocation("UserService.kt", 42) })
```

---

## 11. Where to go next

You now know the whole shape of the library: two constructors (`ok`/`fail`), the
Ok-rail combinators (`map`/`flatMap`), the eliminators (`fold` and friends), the
imperative `rail { }` builder, Failure recovery, the hidden Defect rail, `zip`, and
context frames. That's enough to use `railway` for real.

When you need more:

- **[`AGENTS.md`](AGENTS.md)** — the reference: the full API map as tables, a
  decision guide (`map` vs `flatMap`, `zip` vs `rail`, `catchAll` vs `catch<T>`,
  nested `rail` blocks), and every gotcha in one place. Reach for it when you know
  *what* you want and need the exact signature or the edge-case answer.
- **[`result/api/result.api`](result/api/result.api)** — the exhaustive, always-current
  list of public signatures (it also lists internal plumbing like `RailHalt` and
  `Failed` that you should *not* call — stick to the functions in this tutorial).
- **`./gradlew :result:dokkaGenerate`** — builds HTML API docs, with runnable
  `@sample` snippets attached to each function.

### Installing

```kotlin
dependencies {
    implementation("tech.codingzen:railway:1.0.1")
}
```

Zero transitive dependencies beyond the Kotlin standard library; requires a JVM 21+
toolchain.
