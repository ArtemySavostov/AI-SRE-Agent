export type AgentSessionStatus =
  'PENDING' | 'RUNNING' | 'WAITING_APPROVAL' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export interface AgentSession {
  id: string
  serviceId: string
  incidentId: string | null
  status: AgentSessionStatus
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  updatedAt: string
  recoveryAttempts: number
  summary: string | null
  errorMessage: string | null
}
export type AgentEventType =
  | 'SESSION_CREATED'
  | 'SESSION_STARTED'
  | 'DIAGNOSTIC_STARTED'
  | 'METRICS_COLLECTED'
  | 'LOGS_COLLECTED'
  | 'HYPOTHESIS_GENERATED'
  | 'APPROVAL_REQUESTED'
  | 'SESSION_COMPLETED'
  | 'SESSION_FAILED'
  | 'SESSION_CANCELLED'
export interface AgentEvent {
  id: string
  type: AgentEventType
  message: string
  createdAt: string
  sequenceNumber: number
}
export const statusNames: Record<AgentSessionStatus, string> = {
  PENDING: 'Ожидает запуска',
  RUNNING: 'Выполняется',
  WAITING_APPROVAL: 'Ожидает подтверждения',
  COMPLETED: 'Завершена',
  FAILED: 'Ошибка',
  CANCELLED: 'Отменена',
}
export const eventNames: Record<AgentEventType, string> = {
  SESSION_CREATED: 'Сессия создана',
  SESSION_STARTED: 'Сессия запущена',
  DIAGNOSTIC_STARTED: 'Начало диагностики',
  METRICS_COLLECTED: 'Метрики собраны',
  LOGS_COLLECTED: 'Логи собраны',
  HYPOTHESIS_GENERATED: 'Гипотеза сформирована',
  APPROVAL_REQUESTED: 'Запрошено подтверждение',
  SESSION_COMPLETED: 'Сессия завершена',
  SESSION_FAILED: 'Ошибка сессии',
  SESSION_CANCELLED: 'Сессия отменена',
}
export const terminal = (status: AgentSessionStatus) =>
  ['COMPLETED', 'FAILED', 'CANCELLED'].includes(status)
export const actionsFor = (status: AgentSessionStatus): ('start' | 'cancel')[] =>
  status === 'PENDING'
    ? ['start', 'cancel']
    : ['RUNNING', 'WAITING_APPROVAL'].includes(status)
      ? ['cancel']
      : []
export const orderedEvents = (events: AgentEvent[]) =>
  [...new Map(events.map((event) => [event.id, event])).values()].sort(
    (a, b) => a.sequenceNumber - b.sequenceNumber,
  )

// LocalDateTime is a wall-clock value. Do not apply browser timezone conversion.
export function formatDate(value: string | null): string {
  if (!value) return '—'
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?$/.exec(value)
  if (!m) return 'Некорректная дата'
  const [, year, month, day, hour, minute, second = '00'] = m
  const y = Number(year),
    mo = Number(month),
    d = Number(day)
  const leap = y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (
    mo < 1 ||
    mo > 12 ||
    d < 1 ||
    d > days[mo - 1] ||
    Number(hour) > 23 ||
    Number(minute) > 59 ||
    Number(second) > 59
  )
    return 'Некорректная дата'
  return `${day}.${month}.${year} ${hour}:${minute}:${second}`
}
export function errorText(error: unknown) {
  const status =
    typeof error === 'object' && error !== null && 'status' in error ? error.status : undefined
  if (status === 401) return 'Сессия авторизации истекла. Войдите снова.'
  if (status === 403)
    return 'Нет доступа к ресурсу или действие запрещено. Проверьте права доступа.'
  if (status === 404) return 'Сервис или сессия недоступны.'
  if (status === 409) return 'Состояние изменилось или переход запрещён. Перечитываем состояние.'
  return 'Обновление недоступно. Проверьте соединение и нажмите «Повторить».'
}
