/** At most two operations; completed results belong to the caller, not the queue.
 * A failure stops queued work, but already running operations are allowed to settle.
 * Aborting suppresses callbacks and prevents new operations from starting.
 */
export async function runMetricQueue(
  tasks: (() => Promise<void>)[],
  signal: AbortSignal,
  onError: (cause: unknown) => void,
): Promise<void> {
  let next = 0
  let stopped = false
  async function worker() {
    while (!stopped && !signal.aborted) {
      const task = tasks[next++]
      if (!task) return
      try {
        await task()
      } catch (cause) {
        if (signal.aborted || stopped) return
        stopped = true
        onError(cause)
      }
    }
  }
  await Promise.all([worker(), worker()])
}
