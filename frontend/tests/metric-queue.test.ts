import assert from 'node:assert/strict'
import { test } from 'node:test'
import { runMetricQueue } from '../src/infrastructure/monitoring/requestQueue.ts'

function deferred() {
  let resolve!: () => void
  let reject!: (error: Error) => void
  const promise = new Promise<void>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

test('limits concurrency to two and waits for the entire cycle', async () => {
  const gates = Array.from({ length: 5 }, deferred)
  let active = 0
  let maximum = 0
  const started: number[] = []
  const finished: number[] = []
  const running = runMetricQueue(
    gates.map((gate, index) => async () => {
      started.push(index)
      active++
      maximum = Math.max(maximum, active)
      await gate.promise
      active--
      finished.push(index)
    }),
    new AbortController().signal,
    (cause) => {
      throw cause
    },
  )
  assert.deepEqual(started, [0, 1])
  for (const gate of gates) {
    gate.resolve()
    await Promise.resolve()
    await Promise.resolve()
  }
  await running
  assert.equal(maximum, 2)
  assert.equal(finished.length, 5)
  assert.equal(active, 0)
})

for (const status of [401, 403, 502, 503]) {
  test(`HTTP ${status} stops queued work without retries and retains completed work`, async () => {
    const failure = deferred()
    const inFlight = deferred()
    const errors: unknown[] = []
    const results: string[] = []
    const error = new Error(`HTTP ${status}`)
    let queued = false
    const running = runMetricQueue(
      [
        () => failure.promise,
        async () => {
          await inFlight.promise
          results.push('successful result')
        },
        async () => {
          queued = true
        },
      ],
      new AbortController().signal,
      (cause) => errors.push(cause),
    )
    failure.reject(error)
    await Promise.resolve()
    await Promise.resolve()
    inFlight.resolve()
    await running
    assert.equal(queued, false)
    assert.deepEqual(errors, [error])
    assert.deepEqual(results, ['successful result'])
  })
}

test('abort prevents queued work and suppresses errors from the old screen', async () => {
  const controller = new AbortController()
  const pending = [deferred(), deferred()]
  const errors: unknown[] = []
  let queued = false
  const running = runMetricQueue(
    [
      () => pending[0].promise,
      () => pending[1].promise,
      async () => {
        queued = true
      },
    ],
    controller.signal,
    (error) => errors.push(error),
  )
  controller.abort()
  pending[0].reject(new Error('Aborted'))
  pending[1].resolve()
  await running
  assert.equal(queued, false)
  assert.deepEqual(errors, [])
})

test('already aborted screens issue no requests', async () => {
  const controller = new AbortController()
  controller.abort()
  let started = false
  await runMetricQueue(
    [
      async () => {
        started = true
      },
    ],
    controller.signal,
    assert.fail,
  )
  assert.equal(started, false)
})
