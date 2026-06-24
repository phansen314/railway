# railway

A railway-oriented **result type** for Kotlin/JVM. `Res<S, F>` is a zero-allocation
`@JvmInline value class` with three rails:

- **Ok** — the happy path (value of type `S`, stored unboxed).
- **Failure** — typed, expected domain errors of type `F` (`fail` / `raise`).
- **Defect** — unexpected `Throwable`s, a hidden third channel not shown in the type.

It offers combinators (`map`/`flatMap`/`fold`/`recover`/…), a defect-narrowing set
(`catchAll`/`catch<T>`/`recoverDefect`), `zip`, an imperative `rail { }` builder with
`bind()` / `raise()` / `ensure()`, and **error-context frames** (`.context { }` /
`withFrame`) that attach debugging breadcrumbs to the Failure rail. The library lives in
the `:result` module, package `tech.codingzen.res`.

## Install

```kotlin
dependencies {
    implementation("tech.codingzen:railway:1.0.1")
}
```

Zero transitive dependencies beyond the Kotlin standard library. Requires a JVM 21+ toolchain.

> **New to result types?** Start with the [`TUTORIAL.md`](TUTORIAL.md) — a
> read-top-to-bottom lesson that builds up the API from scratch through one worked
> example. The [`AGENTS.md`](AGENTS.md) reference is the lookup guide once you know
> your way around.

> **Writing code against this library (human or AI agent)? Start with
> [`AGENTS.md`](AGENTS.md)** — it covers the 3-rail mental model, the non-obvious
> rules (hidden defect rail, throw-to-defect, `catchAll` naming), the full API map,
> and copy-paste examples. The exhaustive public-API signature list is
> [`result/api/result.api`](result/api/result.api), and HTML docs build with
> `./gradlew :result:dokkaGenerate`.

## Building

This project uses the Gradle Wrapper (`./gradlew`):

* `./gradlew :result:build` — compile (strict `explicitApi`) and run tests.
* `./gradlew :result:test` — tests only.
* `./gradlew apiCheck` — verify the public ABI matches `result/api/result.api` (`apiDump` to regenerate).
* `./gradlew :result:dokkaGenerate` — build the HTML API docs.
* `./gradlew publishToMavenLocal` — install the artifact to `~/.m2` (signing key required).

The single library module is `:result`; shared build logic lives in a convention plugin under
`buildSrc`. Dependencies are declared via a version catalog (`gradle/libs.versions.toml`), and
both the build cache and configuration cache are enabled (`gradle.properties`).

## License

[MIT License](LICENSE).