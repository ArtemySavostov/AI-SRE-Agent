import assert from 'node:assert/strict'
import { test } from 'node:test'
import { actionsFor, orderedEvents, formatDate } from '../src/infrastructure/agentSessions/model.ts'
import type {
  AgentSession,
  AgentSessionStatus,
  AgentEvent,
} from '../src/infrastructure/agentSessions/model.ts'
import {
  createSessionController,
  initialState,
} from '../src/infrastructure/agentSessions/controller.ts'
import { sessionApi } from '../src/infrastructure/agentSessions/api.ts'
import type { SessionApi } from '../src/infrastructure/agentSessions/api.ts'
const session = (status: AgentSessionStatus = 'PENDING', id = 'one'): AgentSession => ({
  id,
  serviceId: 'service',
  incidentId: null,
  status,
  startedAt: null,
  finishedAt: null,
  createdAt: '2026-10-20T10:00:00',
  updatedAt: '2026-10-20T10:00:00',
  recoveryAttempts: 0,
  summary: null,
  errorMessage: null,
})
const event = (id: string, sequenceNumber: number): AgentEvent => ({
  id,
  sequenceNumber,
  type: 'DIAGNOSTIC_STARTED',
  message: 'same',
  createdAt: '2026-10-20T10:00:00',
})
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const settle = () => new Promise<void>((resolve) => setImmediate(resolve))
function harness(overrides: Partial<SessionApi> = {}, selectedId = 'one') {
  let state = initialState(selectedId)
  let visible = true
  const timers = new Set<() => void>()
  const selections: string[] = []
  const api: SessionApi = {
    history: async () => [session()],
    get: async (id) => session('PENDING', id),
    events: async () => [event('a', 1)],
    create: async () => session(),
    start: async () => session('RUNNING'),
    cancel: async () => session('CANCELLED'),
    ...overrides,
  }
  const loop = createSessionController({
    serviceId: 'service',
    selectedId,
    api,
    change: (next) => {
      state = next
    },
    select: (id) => selections.push(id),
    visible: () => visible,
    schedule: (callback, ms) => {
      assert.equal(ms, 2000)
      timers.add(callback)
      return () => {
        timers.delete(callback)
      }
    },
  })
  return {
    loop,
    api,
    timers,
    selections,
    get state() {
      return state
    },
    hide() {
      visible = false
      loop.visibilityChanged()
    },
    show() {
      visible = true
      loop.visibilityChanged()
    },
  }
}
test('all six statuses expose only supported commands', () => {
  for (const [status, actions] of Object.entries({
    PENDING: ['start', 'cancel'],
    RUNNING: ['cancel'],
    WAITING_APPROVAL: ['cancel'],
    COMPLETED: [],
    FAILED: [],
    CANCELLED: [],
  }))
    assert.deepEqual(actionsFor(status as AgentSessionStatus), actions)
})
test('LocalDateTime preserves wall time and validates missing/invalid calendar dates', () => {
  assert.equal(formatDate('2026-10-20T10:20:30.123456'), '20.10.2026 10:20:30')
  assert.equal(formatDate(null), '—')
  for (const value of ['bad', '2026-02-30T10:00:00', '2026-10-20T25:00:00', '2026-10-20T10:00:00Z'])
    assert.equal(formatDate(value), 'Некорректная дата')
})
test('full event snapshots sort by sequence, deduplicate only ID, retain equal timestamps/messages', () => {
  const events = [event('b', 2), event('a', 1), event('b', 2)]
  assert.deepEqual(
    orderedEvents(events).map((e) => e.id),
    ['a', 'b'],
  )
  assert.deepEqual(orderedEvents(orderedEvents(events)), orderedEvents(events))
})
test('real API sends fresh CSRF, cookies and bodyless commands and never retries POST', async () => {
  const original = globalThis.fetch
  const calls: { path: string; init?: RequestInit }[] = []
  let fail = false
  globalThis.fetch = async (path, init) => {
    calls.push({ path: String(path), init })
    if (String(path) === '/api/auth/csrf')
      return Response.json({ headerName: 'X-CSRF', token: `token-${calls.length}` })
    if (fail) throw new TypeError('network')
    return Response.json(session())
  }
  try {
    await sessionApi.create('service')
    await sessionApi.start('one')
    await sessionApi.cancel('one')
    const posts = calls.filter((call) => call.init?.method === 'POST')
    assert.deepEqual(
      posts.map((call) => call.path),
      [
        '/api/services/service/agent-sessions',
        '/api/agent-sessions/one/start',
        '/api/agent-sessions/one/cancel',
      ],
    )
    for (const call of posts) {
      assert.equal(call.init?.body, undefined)
      assert.equal(call.init?.credentials, 'same-origin')
      assert.ok((call.init?.headers as Record<string, string>)['X-CSRF'])
    }
    fail = true
    await assert.rejects(sessionApi.create('service'))
    assert.equal(calls.filter((call) => call.init?.method === 'POST').length, 4)
  } finally {
    globalThis.fetch = original
  }
})
test('read cycles do not overlap; timers start after requests finish', async () => {
  const pending = deferred<AgentSession>()
  let count = 0
  const h = harness({
    get: async () => {
      count++
      return pending.promise
    },
  })
  const first = h.loop.refresh()
  await settle()
  await h.loop.refresh()
  assert.equal(count, 1)
  assert.equal(h.timers.size, 0)
  pending.resolve(session())
  await first
  assert.equal(h.timers.size, 1)
  h.loop.dispose()
})
test('old replies cannot populate newly selected session', async () => {
  const pending = deferred<AgentSession>()
  let signal!: AbortSignal
  const h = harness({
    get: async (id, s) => {
      if (id === 'one') {
        signal = s
        return pending.promise
      }
      return session('RUNNING', id)
    },
  })
  const first = h.loop.refresh()
  await settle()
  h.loop.select('two')
  await settle()
  assert.equal(signal.aborted, true)
  pending.resolve(session())
  await first
  assert.equal(h.state.session?.id, 'two')
  h.loop.dispose()
})
test('terminal status is followed by final events and history, then stops', async () => {
  const order: string[] = []
  const h = harness({
    history: async () => {
      order.push('history')
      return [session('COMPLETED')]
    },
    get: async () => {
      order.push('get')
      return session('COMPLETED')
    },
    events: async () => {
      order.push('events')
      return [event('final', 9)]
    },
  })
  await h.loop.refresh()
  assert.deepEqual(order, ['history', 'get', 'events', 'history'])
  assert.equal(h.state.events[0].id, 'final')
  assert.equal(h.timers.size, 0)
  h.loop.dispose()
})
test('failed final events retain terminal snapshot and allow retry', async () => {
  let fail = true
  const h = harness({
    get: async () => session('COMPLETED'),
    events: async () => {
      if (fail) throw new Error('offline')
      return [event('final', 9)]
    },
  })
  await h.loop.refresh()
  assert.equal(h.state.session?.status, 'COMPLETED')
  assert.ok(h.state.error)
  assert.equal(h.timers.size, 0)
  fail = false
  await h.loop.refresh()
  assert.equal(h.state.error, '')
  assert.equal(h.state.events[0].id, 'final')
  assert.equal(h.timers.size, 0)
})
for (const status of [0, 401, 403, 404, 500])
  test(`error ${status} preserves data and pauses until explicit retry`, async () => {
    let fail = false
    const h = harness({
      events: async () => {
        if (fail) throw Object.assign(new Error('failure'), { status })
        return [event('a', 1)]
      },
    })
    await h.loop.refresh()
    fail = true
    await h.loop.refresh()
    assert.equal(h.state.events.length, 1)
    assert.equal(h.state.session?.id, 'one')
    assert.ok(h.state.error)
    assert.equal(h.timers.size, 0)
    h.hide()
    h.show()
    await settle()
    assert.equal(h.timers.size, 0)
    fail = false
    await h.loop.refresh()
    assert.equal(h.state.error, '')
    assert.equal(h.timers.size, 1)
    h.loop.dispose()
  })
test('hide cancels requests/timers, show refreshes, disposal ignores late results', async () => {
  const pending = deferred<AgentSession>()
  let signal!: AbortSignal
  const h = harness({
    get: async (_id, s) => {
      signal = s
      return pending.promise
    },
  })
  const reading = h.loop.refresh()
  await settle()
  h.hide()
  assert.equal(signal.aborted, true)
  pending.resolve(session())
  await reading
  assert.equal(h.state.session, undefined)
  h.api.get = async () => session()
  h.show()
  await settle()
  assert.equal(h.timers.size, 1)
  h.hide()
  assert.equal(h.timers.size, 0)
  h.show()
  await settle()
  h.loop.dispose()
  assert.equal(h.timers.size, 0)
  await h.loop.refresh()
  assert.equal(h.timers.size, 0)
})
test('409 rereads actual state without another POST', async () => {
  let count = 0,
    status: AgentSessionStatus = 'PENDING'
  const h = harness({
    get: async () => session(status),
    start: async () => {
      count++
      status = 'COMPLETED'
      throw Object.assign(new Error('conflict'), { status: 409 })
    },
  })
  await h.loop.refresh()
  await h.loop.command('start')
  await settle()
  assert.equal(count, 1)
  assert.equal(h.state.session?.status, 'COMPLETED')
  assert.match(h.state.notice, /Состояние изменилось/)
  assert.equal(h.timers.size, 0)
})
test('double clicks are locked; create is retained if history reload fails', async () => {
  const pending = deferred<AgentSession>()
  let count = 0
  const h = harness(
    {
      create: async () => {
        count++
        return pending.promise
      },
      history: async () => {
        throw new Error('offline')
      },
    },
    '',
  )
  const first = h.loop.command('create')
  await h.loop.command('create')
  assert.equal(count, 1)
  pending.resolve(session())
  await first
  await settle()
  assert.equal(h.state.session?.id, 'one')
  assert.equal(h.state.history[0].id, 'one')
  assert.deepEqual(h.selections, ['one'])
  assert.ok(h.state.error)
})
test('network mutation failure reports unknown result without retries', async () => {
  let count = 0
  const h = harness(
    {
      create: async () => {
        count++
        throw new Error('offline')
      },
    },
    '',
  )
  await h.loop.command('create')
  h.hide()
  h.show()
  await settle()
  assert.equal(count, 1)
  assert.match(h.state.notice, /Результат команды неизвестен/)
  assert.equal(h.timers.size, 0)
})
test('old GET cannot roll back successful start', async () => {
  const pending = deferred<AgentSession>()
  const h = harness()
  await h.loop.refresh()
  h.api.get = async () => pending.promise
  const old = h.loop.refresh()
  await settle()
  h.api.get = async () => session('RUNNING')
  await h.loop.command('start')
  await settle()
  pending.resolve(session())
  await old
  assert.equal(h.state.session?.status, 'RUNNING')
  h.loop.dispose()
})
test('foreign service session exposes neither details nor events; history remains available', async () => {
  let events = 0
  const h = harness({
    get: async () => ({ ...session(), serviceId: 'foreign' }),
    events: async () => {
      events++
      return []
    },
  })
  await h.loop.refresh()
  assert.equal(h.state.session, undefined)
  assert.equal(events, 0)
  assert.ok(h.state.history.length)
  assert.match(h.state.error, /недоступны/)
})
test('selection during POST ignores reply; hiding does not discard created session', async () => {
  const pending = deferred<AgentSession>()
  const h = harness({ create: async () => pending.promise })
  const command = h.loop.command('create')
  h.loop.select('two')
  pending.resolve(session())
  await command
  await settle()
  assert.equal(h.state.selectedId, 'two')
  assert.equal(h.state.session?.id, 'two')
  const next = deferred<AgentSession>()
  h.api.create = async () => next.promise
  const creating = h.loop.command('create')
  h.hide()
  next.resolve(session('PENDING', 'new'))
  await creating
  assert.equal(h.state.session?.id, 'new')
  h.loop.dispose()
})

test('API 401 uses existing session-expired event and 403 remains an access error', async () => {
  const originalFetch = globalThis.fetch
  const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window')
  const target = new EventTarget()
  Object.defineProperty(globalThis, 'window', { configurable: true, value: target })
  let expired = 0
  target.addEventListener('sre:session-expired', () => {
    expired++
  })
  try {
    for (const status of [401, 403, 404, 500]) {
      globalThis.fetch = async () => new Response(null, { status })
      await assert.rejects(
        sessionApi.get('one', new AbortController().signal),
        (error: unknown) =>
          typeof error === 'object' &&
          error !== null &&
          'status' in error &&
          error.status === status,
      )
    }
    assert.equal(expired, 1)
  } finally {
    globalThis.fetch = originalFetch
    if (originalWindow) Object.defineProperty(globalThis, 'window', originalWindow)
    else Reflect.deleteProperty(globalThis, 'window')
  }
})
for (const reject of [false, true])
  test(`dispose aborts and ignores late ${reject ? 'failure' : 'success'}`, async () => {
    const pending = deferred<AgentSession>()
    let signal!: AbortSignal
    const h = harness({
      get: async (_id, s) => {
        signal = s
        return pending.promise
      },
    })
    const reading = h.loop.refresh()
    await settle()
    h.loop.dispose()
    assert.equal(signal.aborted, true)
    if (reject) pending.reject(new Error('late'))
    else pending.resolve(session())
    await reading
    assert.equal(h.state.session, undefined)
    assert.equal(h.state.error, '')
    assert.equal(h.timers.size, 0)
  })
test('cancel is terminal and does not expose restart actions; PENDING polls external starts', async () => {
  let status: AgentSessionStatus = 'PENDING'
  const h = harness({
    get: async () => session(status),
    cancel: async () => {
      status = 'CANCELLED'
      return session(status)
    },
  })
  await h.loop.refresh()
  assert.equal(h.timers.size, 1)
  status = 'RUNNING'
  const tick = [...h.timers][0]
  tick()
  await settle()
  assert.equal(h.state.session?.status, 'RUNNING')
  await h.loop.command('cancel')
  await settle()
  assert.equal(h.state.session?.status, 'CANCELLED')
  assert.deepEqual(actionsFor(h.state.session!.status), [])
  assert.equal(h.timers.size, 0)
})
