package tech.codingzen.res

/**
 * One breadcrumb on a [Failure][Res] rail: a human-readable [message] describing the
 * operation that was in flight, plus optional structured [attachment] (a request id, the
 * offending input, …) and an optional [location] in source.
 *
 * Frames are *observability metadata*, never part of the domain error: two failures with
 * the same payload are equal regardless of the frames attached (see [Failed.equals]).
 * They serve the Failure rail only — a Defect already carries a real [Throwable] with a
 * JVM stacktrace, so [context] is a no-op there.
 *
 * @sample tech.codingzen.res.contextSample
 */
public data class Frame(
    val message: String,
    val attachment: Any? = null,
    val location: SourceLocation? = null,
)

/**
 * Where a [Frame] was attached. Supplied explicitly by the caller (no stack-walking) — a
 * literal or a cheap lambda is enough; the lambda only runs on the Failure path.
 */
public data class SourceLocation(
    val file: String,
    val line: Int,
    val function: String? = null,
) {
    override fun toString(): String = buildString {
        append(file).append(':').append(line)
        if (function != null) append(" in ").append(function)
    }
}
