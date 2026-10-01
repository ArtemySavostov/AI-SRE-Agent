import { useCallback, useEffect, useState } from 'react'
import { messageOf } from './api'

export function useResource<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [state, setState] = useState<{ data?: T; loading: boolean; error: string }>({ loading: true, error: '' })
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ data, loading: false, error: '' })
    }).catch(error => {
      if (!controller.signal.aborted) setState(previous => ({ ...previous, loading: false, error: messageOf(error) }))
    })
    return () => controller.abort()
  }, [load, revision])
  const reload = useCallback(() => {
    setState(previous => ({ ...previous, loading: true, error: '' }))
    setRevision(previous => previous + 1)
  }, [])
  return { ...state, reload }
}
