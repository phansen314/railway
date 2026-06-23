@file:Suppress("UNCHECKED_CAST")

package tech.codingzen.res

// ---------------------------------------------------------------------------
// Reading the frame chain off a Failure
// ---------------------------------------------------------------------------
//
// All readers return the empty/blank value on the Ok and Defect rails — frames are a
// Failure-rail concept. Frames are exposed innermost-first (index 0 = closest to the error)
// except [contextSummary], which reads outermost-first for natural left-to-right prose.

/** The frames on this Failure, innermost-first. Empty on Ok / Defect. */
public fun Res<*, *>.contextChain(): List<Frame> {
    val r = raw
    return if (r is Failed<*>) r.frames.toFrameList() else emptyList()
}

/**
 * Multi-line dump: the error followed by each frame (index, message, optional location and
 * attachment), innermost-first. Empty string on Ok / Defect.
 */
public fun Res<*, *>.renderContext(): String {
    val r = raw
    if (r !is Failed<*>) return ""
    val frames = r.frames.toFrameList()
    return buildString {
        append(r.error.toString())
        frames.forEachIndexed { index, frame ->
            appendLine()
            append("  ").append(index).append(": ").append(frame.message)
            frame.location?.let { appendLine(); append("     at ").append(it) }
            frame.attachment?.let {
                appendLine(); append("     attachment=")
                try { append(it) } catch (_: Exception) { append("<error rendering attachment>") }
            }
        }
    }
}

/**
 * One-line breadcrumb `"outer → inner → Error"` (outermost-first). Just the error string on
 * Ok / Defect or a frameless Failure.
 */
public fun Res<*, *>.contextSummary(): String {
    val r = raw
    if (r !is Failed<*>) return ""
    val frames = r.frames.toFrameList()
    if (frames.isEmpty()) return r.error.toString()
    return buildString {
        frames.asReversed().forEach { append(it.message).append(" → ") }
        append(r.error.toString())
    }
}

/** Structured shape for JSON logging: `{ "error": …, "frames": [ { message, location?, attachment? } ] }`. */
public fun Res<*, *>.contextMap(): Map<String, Any?> {
    val r = raw
    if (r !is Failed<*>) return emptyMap()
    return mapOf(
        "error" to r.error,
        "frames" to r.frames.toFrameList().map { frame ->
            buildMap {
                put("message", frame.message)
                frame.location?.let { put("location", it.toString()) }
                frame.attachment?.let { put("attachment", it) }
            }
        },
    )
}

/** First frame attachment of type [T], or null. e.g. `res.contextChain().findAttachment<RequestId>()`. */
public inline fun <reified T> List<Frame>.findAttachment(): T? =
    firstOrNull { it.attachment is T }?.attachment as T?
