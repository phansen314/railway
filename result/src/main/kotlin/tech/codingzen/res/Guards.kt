package tech.codingzen.res

/**
 * Short-circuit the enclosing [rail] block with [error] unless [condition] holds.
 *
 * No `kotlin.contracts` is used (experimental), so there is **no smart-cast**:
 * after `ensure(x != null) { … }` the compiler still sees `x` as nullable. Use
 * [ensureNotNull] when you need the narrowed value.
 *
 * @sample tech.codingzen.res.ensureSample
 */
public inline fun <F> Rail<F>.ensure(condition: Boolean, error: () -> F) {
    if (!condition) raise(error())
}

/**
 * Return [value] when non-null, otherwise short-circuit the enclosing [rail] block
 * with [error]. The narrowed, non-null value is returned (rather than smart-cast in
 * place) — `val v = ensureNotNull(x) { … }`.
 */
public inline fun <S : Any, F> Rail<F>.ensureNotNull(value: S?, error: () -> F): S =
    value ?: raise(error())
