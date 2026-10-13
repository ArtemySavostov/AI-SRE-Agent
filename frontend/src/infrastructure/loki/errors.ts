import { ApiError } from '../../api'
import { messageOf } from '../api'

export function logsError(error: unknown, operation: 'read' | 'write' = 'read'): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Сессия истекла. Войдите в аккаунт снова.'
    if (error.status === 403)
      return operation === 'read'
        ? 'Нет доступа к логам этого проекта. ENGINEER требуется доступ в project_metrics_access; обратитесь к администратору.'
        : 'Операция отклонена: нет доступа к проекту или отклонён CSRF-токен. Проверьте доступ, обновив список интеграций; при необходимости войдите снова.'
    if (error.status === 409)
      return 'Loki для этого сервера уже существует. Нажмите «Обновить интеграции».'
    if (error.status === 502) return `Ошибка источника Loki / SSH: ${error.message}`
    if (error.status === 503)
      return `Источник временно занят или ожидает восстановления SSH. Повторите вручную через несколько секунд. ${error.message}`
  }
  return messageOf(error)
}
