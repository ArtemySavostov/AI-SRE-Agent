// One request at a time. A timer is scheduled only after successful completion.
// Disposal also guards callbacks when a transport ignores AbortSignal.
export function createPolling<T>(options: {
  load: (signal: AbortSignal) => Promise<T>
  success: (data: T) => void
  failure: (error: unknown) => void
  busy: (value: boolean) => void
  visible: () => boolean
  schedule: (callback: () => void, ms: number) => () => void
}) {
  let disposed = false
  let active = false
  let auto = false
  let paused = false
  let cancelTimer: (() => void) | undefined
  const controller = new AbortController()
  function schedule() {
    cancelTimer?.()
    cancelTimer = undefined
    if (!disposed && !active && auto && !paused && options.visible())
      cancelTimer = options.schedule(() => {
        if (options.visible()) void refresh()
      }, 30_000)
  }
  async function refresh() {
    if (disposed || active) return
    cancelTimer?.()
    active = true
    paused = false
    options.busy(true)
    try {
      const data = await options.load(controller.signal)
      if (!disposed) options.success(data)
    } catch (error) {
      paused = true
      if (!disposed) options.failure(error)
    } finally {
      active = false
      if (!disposed) options.busy(false)
      schedule()
    }
  }
  return {
    refresh,
    setAuto(value: boolean) {
      auto = value
      schedule()
    },
    visibilityChanged: schedule,
    dispose() {
      disposed = true
      controller.abort()
      cancelTimer?.()
    },
  }
}
