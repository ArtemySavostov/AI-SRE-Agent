import { useCallback, useEffect, useState } from 'react'
import { read } from '../api'
import { metrics, monitoringError } from './model'
import type { MetricResult } from './model'
import { runMetricQueue } from './requestQueue'

const autoRefreshKey = 'sre:monitoring:auto-refresh'

export function useMetrics(path: string, hours: number, containers: boolean) {
  const [state, setState] = useState<{
    summary: MetricResult[]
    history: Record<string, MetricResult>
    busy: boolean
    error: string
  }>({ summary: [], history: {}, busy: true, error: '' })
  const [revision, setRevision] = useState(0)
  const [auto, setAutoState] = useState(() => {
    try {
      return window.localStorage.getItem(autoRefreshKey) === 'true'
    } catch {
      return false
    }
  })
  const setAuto = useCallback((enabled: boolean) => {
    setAutoState(enabled)
    try {
      window.localStorage.setItem(autoRefreshKey, String(enabled))
    } catch {
      /* Keep the control usable when browser storage is unavailable. */
    }
  }, [])
  const [now, setNow] = useState(Date.now)
  const refresh = useCallback(() => {
    setState((previous) => ({ ...previous, busy: true, error: '' }))
    setRevision((value) => value + 1)
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    const end = Date.now()
    const start = end - hours * 3600_000
    const selected = metrics.filter(([id]) => id.startsWith('container_') === containers)
    const tasks: (() => Promise<void>)[] = []
    if (!containers)
      tasks.push(async () => {
        const summary = await read<MetricResult[]>(path + '/metrics/summary', controller.signal)
        if (!controller.signal.aborted) setState((previous) => ({ ...previous, summary }))
      })
    for (const [id] of selected)
      tasks.push(async () => {
        const query = new URLSearchParams({
          metric: id,
          start: new Date(start).toISOString(),
          end: new Date(end).toISOString(),
          points: '600',
        })
        const result = await read<MetricResult>(
          `${path}/metrics/history?${query}`,
          controller.signal,
        )
        if (!controller.signal.aborted)
          setState((previous) => ({ ...previous, history: { ...previous.history, [id]: result } }))
      })
    void runMetricQueue(tasks, controller.signal, (cause) => {
      setState((previous) => ({ ...previous, error: monitoringError(cause) }))
    }).then(() => {
      if (!controller.signal.aborted) setState((previous) => ({ ...previous, busy: false }))
    })
    return () => controller.abort()
  }, [path, hours, containers, revision])
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])
  useEffect(() => {
    if (!auto || state.busy || state.error) return
    const timer = window.setInterval(() => {
      if (!document.hidden) refresh()
    }, 30_000)
    return () => window.clearInterval(timer)
  }, [auto, state.busy, state.error, refresh])
  return { ...state, refresh, auto, setAuto, now }
}
