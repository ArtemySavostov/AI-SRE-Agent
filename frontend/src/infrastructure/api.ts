import { ApiError, request } from '../api'

export interface Project { id: string; name: string; description: string }
export interface Server { id: string; projectId: string; name: string; hostname: string; ipAddress: string; os: string; status: 'ACTIVE' | 'INACTIVE' }
export interface ManagedService { id: string; projectId: string; serverId: string; name: string; description: string | null; healthcheckUrl: string | null; status: 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN' }
export interface SshSettings { host: string; port: number; username: string; credentialId: string; hostKeyFingerprint: string }
export interface ConnectionTest { connected: boolean; checkedAt: string; hostname: string }
export interface Container { id: string; name: string; image: string; state: string; health: string | null; composeProject: string | null; composeService: string | null; startedAt: string | null; ports: { containerPort: string; hostIp: string | null; hostPort: string | null }[]; networks: { name: string; ipAddress: string | null }[] }
export interface Snapshot {
  collectedAt: string
  host: { hostname: string; operatingSystem: string; kernel: string; architecture: string; cpuCount: number; memoryBytes: number; uptimeSeconds: number }
  docker: { version: string; name: string; storageDriver: string; containers: number; running: number; stopped: number }
  containers: Container[]
}
export interface DiscoveryJob { jobId: string; status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'; requestedAt: string; completedAt: string | null; errorCode: string | null; error: string | null; snapshot: Snapshot | null }
export const serverPath = (projectId: string, serverId: string) => `/api/project/${encodeURIComponent(projectId)}/server/${encodeURIComponent(serverId)}`
export const activeJob = (job: DiscoveryJob | null | undefined) => job?.status === 'QUEUED' || job?.status === 'RUNNING'
export const messageOf = (error: unknown) => error instanceof Error ? error.message : 'Не удалось выполнить запрос.'

export async function read<T>(path: string, signal?: AbortSignal): Promise<T> {
  try { return await (await request(path, { signal })).json() as T }
  catch (error) {
    if (error instanceof ApiError && error.status === 401) window.dispatchEvent(new Event('sre:session-expired'))
    throw error
  }
}
export async function optional<T>(path: string, signal?: AbortSignal): Promise<T | null> {
  try { return await read<T>(path, signal) }
  catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error }
}
export async function write<T>(path: string, data?: unknown, method: 'POST' | 'PUT' = 'POST'): Promise<T> {
  const csrf = await read<{ headerName: string; token: string }>('/api/auth/csrf')
  try {
    const response = await request(path, { method, headers: { [csrf.headerName]: csrf.token, ...(data === undefined ? {} : { 'Content-Type': 'application/json' }) }, body: data === undefined ? undefined : JSON.stringify(data) })
    return await response.json() as T
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) window.dispatchEvent(new Event('sre:session-expired'))
    throw error
  }
}
