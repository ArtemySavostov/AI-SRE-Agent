export interface Integration {
  id: string
  serverId: string | null
  type: string
  name: string
  baseUrl: string
  status: string
}
export interface TestResult {
  status: string
  checkedAt: string
  warnings: string[]
}
export interface LogEntry {
  timestampNs: string
  labels: Record<string, string>
  line: string
  metadata: Record<string, string>
}
export interface LogResult {
  fetchedAt: string
  start: string
  end: string
  direction: 'backward' | 'forward'
  entries: LogEntry[]
  limitReached: boolean
  warnings: string[]
}
export interface Query {
  service: string
  container: string
  composeProject: string
  search: string
  minutes: string
  start: string
  end: string
  limit: string
  direction: 'backward' | 'forward'
}
export const defaults: Query = {
  service: '',
  container: '',
  composeProject: '',
  search: '',
  minutes: '60',
  start: '',
  end: '',
  limit: '100',
  direction: 'backward',
}
export function timestampDate(timestampNs: string): Date {
  if (!/^\d{1,19}$/.test(timestampNs)) throw new Error('Некорректное время записи')
  return new Date(Number(BigInt(timestampNs) / 1_000_000n))
}
export function queryParams(query: Query, now = Date.now()): URLSearchParams {
  const end = query.minutes === 'custom' ? new Date(query.end).getTime() : now
  const start =
    query.minutes === 'custom'
      ? new Date(query.start).getTime()
      : end - Number(query.minutes) * 60_000
  if (
    !Number.isFinite(start) ||
    !Number.isFinite(end) ||
    start < 0 ||
    start >= end ||
    end - start > 7 * 86400_000 ||
    end > now + 60_000
  )
    throw new Error(
      'Укажите диапазон до 7 дней: начало раньше конца, не ранее 1970 года и без будущих дат.',
    )
  const params = new URLSearchParams({
    start: new Date(start).toISOString(),
    end: new Date(end).toISOString(),
    limit: query.limit,
    direction: query.direction,
  })
  for (const key of ['service', 'container', 'composeProject', 'search'] as const) {
    const value = query[key]
    if (
      value.length > (key === 'search' ? 1024 : 255) ||
      (key !== 'search' && value !== '' && !value.trim())
    )
      throw new Error('Метки: до 255 символов, не только пробелы. Поиск: до 1024 символов.')
    if (value !== '') params.set(key, value)
  }
  return params
}
const object = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)
const strings = (value: unknown): value is Record<string, string> =>
  object(value) && Object.values(value).every((v) => typeof v === 'string')
const instant = (value: unknown): value is string =>
  typeof value === 'string' && Number.isFinite(Date.parse(value))
export function parseResult(value: unknown): LogResult {
  if (
    !object(value) ||
    !instant(value.fetchedAt) ||
    !instant(value.start) ||
    !instant(value.end) ||
    !['forward', 'backward'].includes(String(value.direction)) ||
    typeof value.limitReached !== 'boolean' ||
    !Array.isArray(value.warnings) ||
    !value.warnings.every((v) => typeof v === 'string') ||
    !Array.isArray(value.entries) ||
    value.entries.length > 1000 ||
    !value.entries.every(
      (entry) =>
        object(entry) &&
        typeof entry.timestampNs === 'string' &&
        /^\d{1,19}$/.test(entry.timestampNs) &&
        typeof entry.line === 'string' &&
        strings(entry.labels) &&
        strings(entry.metadata),
    )
  )
    throw new Error('Некорректный ответ API логов. Проверьте совместимость бэкенда.')
  // Keep the original timestamps, stream labels, order, and duplicate entries intact.
  return value as unknown as LogResult
}
export function formattedJson(line: string): string | undefined {
  try {
    return JSON.stringify(JSON.parse(line), null, 2)
  } catch {
    return undefined
  }
}
