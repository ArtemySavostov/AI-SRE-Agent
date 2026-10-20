import type { SessionApi } from './api.ts'
import { actionsFor, errorText, orderedEvents, terminal } from './model.ts'
import type { AgentEvent, AgentSession } from './model.ts'

export interface SessionState {
  history: AgentSession[]
  session?: AgentSession
  selectedId: string
  events: AgentEvent[]
  loading: boolean
  mutating: boolean
  error: string
  notice: string
}
export const initialState = (selectedId = ''): SessionState => ({
  history: [],
  selectedId,
  events: [],
  loading: false,
  mutating: false,
  error: '',
  notice: '',
})

// A single read cycle owns state, events and history. Commands invalidate all older reads.
// Any read failure pauses polling until explicit retry; POST is never retried.
export function createSessionController(options: {
  serviceId: string
  selectedId: string
  api: SessionApi
  change: (state: SessionState) => void
  select: (id: string) => void
  visible: () => boolean
  schedule: (callback: () => void, ms: number) => () => void
}) {
  let state = initialState(options.selectedId)
  let disposed = false
  let epoch = 0
  let request: AbortController | undefined
  let cancelTimer: (() => void) | undefined
  let paused = false
  let done = false
  function patch(next: Partial<SessionState>) {
    state = { ...state, ...next }
    if (!disposed) options.change(state)
  }
  function invalidate() {
    epoch++
    request?.abort()
    request = undefined
    cancelTimer?.()
    cancelTimer = undefined
  }
  function accept(session: AgentSession) {
    if (session.serviceId !== options.serviceId || session.id !== state.selectedId)
      throw Object.assign(new Error('Сессия не принадлежит выбранному сервису'), { status: 404 })
    patch({
      session,
      history: [session, ...state.history.filter((item) => item.id !== session.id)].sort((a, b) =>
        b.createdAt.localeCompare(a.createdAt),
      ),
    })
  }
  async function refresh() {
    if (disposed || request || state.mutating || !options.visible()) return
    cancelTimer?.()
    cancelTimer = undefined
    const controller = new AbortController()
    request = controller
    const version = epoch
    const current = () => !disposed && version === epoch && !controller.signal.aborted
    paused = false
    done = false
    patch({ loading: true, error: '' })
    const previousStatus = state.session?.status
    try {
      // History remains reachable even when the deep-linked session is unavailable.
      const initialHistory = await options.api.history(options.serviceId, controller.signal)
      if (!current()) return
      patch({ history: initialHistory.filter((s) => s.serviceId === options.serviceId) })
      if (state.selectedId) {
        const session = await options.api.get(state.selectedId, controller.signal)
        if (!current()) return
        accept(session)
        // Fetch AFTER the status snapshot, including the final terminal event.
        const events = await options.api.events(session.id, controller.signal)
        if (!current()) return
        patch({ events: orderedEvents(events) })
      }
      if (
        state.session &&
        (previousStatus !== state.session.status || terminal(state.session.status))
      ) {
        const history = await options.api.history(options.serviceId, controller.signal)
        if (!current()) return
        patch({ history: history.filter((s) => s.serviceId === options.serviceId) })
      }
      done = !state.selectedId || (!!state.session && terminal(state.session.status))
    } catch (error) {
      if (!current()) return
      paused = true
      patch({ error: errorText(error) })
    } finally {
      if (current()) {
        request = undefined
        patch({ loading: false })
        if (!paused && !done && options.visible())
          cancelTimer = options.schedule(() => {
            void refresh()
          }, 2000)
      }
    }
  }
  function select(id: string) {
    invalidate()
    done = false
    paused = false
    patch({ selectedId: id, session: undefined, events: [], error: '', loading: false, notice: '' })
    options.select(id)
    void refresh()
  }
  async function command(action: 'create' | 'start' | 'cancel') {
    if (disposed || state.mutating) return
    if (
      action !== 'create' &&
      (!state.session || !actionsFor(state.session.status).includes(action))
    )
      return
    invalidate()
    paused = false
    const version = epoch
    const id = action === 'create' ? options.serviceId : state.selectedId
    patch({ mutating: true, loading: false, error: '', notice: '' })
    try {
      const result = await options.api[action](id)
      if (disposed || version !== epoch) return
      if (result.serviceId !== options.serviceId) throw new Error('Неверный сервис в ответе')
      if (action === 'create') {
        patch({ selectedId: result.id, events: [] })
        options.select(result.id)
      }
      accept(result)
      patch({
        history: [result, ...state.history.filter((s) => s.id !== result.id)].sort((a, b) =>
          b.createdAt.localeCompare(a.createdAt),
        ),
      })
    } catch (error) {
      if (disposed || version !== epoch) return
      const status =
        typeof error === 'object' && error !== null && 'status' in error ? error.status : undefined
      patch({
        notice:
          status === 409
            ? errorText(error)
            : status === 401 || status === 403 || status === 404
              ? errorText(error)
              : 'Результат команды неизвестен. Обновите историю и состояние перед следующим действием; команда автоматически не повторяется.',
      })
      // Only a conflict triggers automatic reconciliation after failure.
      if (status !== 409) {
        paused = true
        return
      }
    } finally {
      if (!disposed) {
        patch({ mutating: false })
        if (version !== epoch || !paused) void refresh()
      }
    }
  }
  return {
    refresh,
    select,
    command,
    visibilityChanged() {
      if (!options.visible()) {
        if (!state.mutating) invalidate()
        patch({ loading: false })
      } else if (!paused) void refresh()
    },
    dispose() {
      disposed = true
      invalidate()
    },
  }
}
