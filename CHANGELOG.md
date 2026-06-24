# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] — 2026-06-23

### Added

- **Coroutines / suspend usage** — no new API: every combinator and `rail { }` is `inline`,
  so suspend lambdas work from any `suspend` caller (e.g. `rail { fetchA().bind() }`).
  `CancellationException` is fatal, so cancellation propagates rather than being sealed into a
  Defect. Documented in `AGENTS.md` and `TUTORIAL.md`; `kotlinx-coroutines` is a test-only
  dependency (the artifact stays zero-dependency).
- `catching { }` — capture a throwing call onto the Defect rail; `catching(transform) { }` —
  map a throw onto a typed Failure (the throw-to-Failure bridge `rail { }` cannot express).
- `Iterable<Res<S, F>>.sequence()` and `Iterable<T>.traverse { }` — collapse many results into
  one result over a list, fail-fast and left-biased (like `zip`).
- `mapBoth(onOk, onFailure)` — transform whichever of the Ok/Failure rails is present in one
  pass; Defect passes through, context frames preserved.
- `swap()` — exchange the Ok and Failure rails; Defect stays a Defect.
- `getOrDefault(value)` — eager constant fallback (the non-lambda `getOrElse`).
- `filterOrElse(predicate, error)` — demote an Ok that fails the predicate to a Failure.

## [1.0.1] — 2026-06-22

### Fixed

- Closed 1.0.x review gaps: location-overload tests, fatal-safe attachment rendering, POM email.

## [1.0.0] — 2026-06-22

### Added

- Initial release: the three-rail `Res<S, F>` value class (Ok / Failure / Defect), core
  combinators (`map`/`flatMap`/`fold`/`recover`/`orElse`/`mapFailure`), the defect-narrowing
  set (`catchAll`/`catch<T>`/`recoverDefect`/`mapDefect`), taps, guards, `zip`, the imperative
  `rail { }` builder (`bind`/`raise`/`ensure`/`ensureNotNull`/`withFrame`), and error-context
  frames on the Failure rail.

[1.1.0]: https://github.com/phansen314/railway/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/phansen314/railway/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/phansen314/railway/releases/tag/v1.0.0
