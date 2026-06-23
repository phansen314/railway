package tech.codingzen.res

/**
 * Whether [e] is *fatal* and must always propagate rather than be routed to the
 * Defect rail. Modelled on Scala's `NonFatal` / ZIO's fatal set:
 *
 *  - [VirtualMachineError] — `OutOfMemoryError`, `StackOverflowError`, etc.
 *  - [LinkageError] — classloading/linkage corruption.
 *  - [InterruptedException] — thread-interruption signal.
 *  - [kotlin.coroutines.cancellation.CancellationException] — structured-concurrency
 *    cancellation; swallowing it as a defect would break coroutine cancellation.
 *
 * Combinators that run user lambdas ([map], [flatMap], …) catch non-fatal throws
 * and convert them to defects; a fatal throw is re-thrown by this guard.
 */
@PublishedApi
internal fun isFatal(e: Throwable): Boolean = when (e) {
    is VirtualMachineError -> true
    is LinkageError -> true
    is InterruptedException -> true
    is kotlin.coroutines.cancellation.CancellationException -> true
    else -> false
}
