import { read, write } from '../api.ts'
import type { AgentEvent, AgentSession } from './model.ts'
const sessionPath = (id: string) => `/api/agent-sessions/${encodeURIComponent(id)}`
const historyPath = (id: string) => `/api/services/${encodeURIComponent(id)}/agent-sessions`
export const sessionApi = {
  history: (serviceId: string, signal: AbortSignal) =>
    read<AgentSession[]>(historyPath(serviceId), signal),
  get: (id: string, signal: AbortSignal) => read<AgentSession>(sessionPath(id), signal),
  events: (id: string, signal: AbortSignal) =>
    read<AgentEvent[]>(sessionPath(id) + '/events', signal),
  create: (serviceId: string) => write<AgentSession>(historyPath(serviceId)),
  start: (id: string) => write<AgentSession>(sessionPath(id) + '/start'),
  cancel: (id: string) => write<AgentSession>(sessionPath(id) + '/cancel'),
}
export type SessionApi = typeof sessionApi
