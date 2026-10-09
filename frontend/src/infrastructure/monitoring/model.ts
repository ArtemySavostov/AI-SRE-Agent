import { ApiError } from '../../api'
import { messageOf } from '../api'

export interface Settings {
  name: string
  baseUrl: string
  nodeJob: string
  nodeInstance: string
  containerJob: string
  containerInstance: string
}
export interface Integration {
  id: string
  serverId: string | null
  type: string
  name: string
  baseUrl: string
  status: string
  config: Partial<Record<keyof Settings, string>> | null
}
export interface MetricResult {
  metric: string
  unit: string
  status: 'AVAILABLE' | 'NO_DATA' | 'TARGET_DOWN' | 'STALE'
  target: { status: 'UP' | 'TARGET_DOWN' | 'STALE' | 'NO_DATA'; lastScrapeAt: number | null }
  fetchedAt: string
  start: number
  end: number
  stepSeconds: number
  series: {
    labels: Record<string, string>
    points: { timestamp: number; value: number | null }[]
  }[]
  warnings: string[]
}
export interface TestResult {
  status: string
  checkedAt: string
  targets: { labels: Record<string, string>; up: boolean; sampledAt: number; stale: boolean }[]
  warnings: string[]
}
export const defaults: Settings = {
  name: 'Prometheus',
  baseUrl: 'http://127.0.0.1:9090',
  nodeJob: 'node-exporter',
  nodeInstance: 'node-exporter:9100',
  containerJob: 'cadvisor',
  containerInstance: 'cadvisor:8080',
}
export function settingsOf(source: Integration): Settings {
  return { ...defaults, ...source.config, name: source.name, baseUrl: source.baseUrl }
}
export const metrics = [
  ['cpu_usage_percent', 'CPU хоста'],
  ['memory_used_bytes', 'RAM · использовано'],
  ['memory_available_bytes', 'RAM · доступно'],
  ['memory_usage_percent', 'RAM · использование'],
  ['filesystem_usage_percent', 'Файловые системы · заполненность'],
  ['network_receive_bytes_per_second', 'Сеть · приём'],
  ['network_transmit_bytes_per_second', 'Сеть · передача'],
  ['disk_read_bytes_per_second', 'Диски · чтение'],
  ['disk_write_bytes_per_second', 'Диски · запись'],
  ['uptime_seconds', 'Uptime хоста'],
  ['container_cpu_cores', 'CPU контейнеров · ядра'],
  ['container_memory_working_set_bytes', 'Память контейнеров · working set'],
] as const
export function monitoringError(error: unknown, operation: 'read' | 'write' = 'read'): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Сессия истекла. Войдите в аккаунт снова.'
    if (error.status === 403)
      return operation === 'read'
        ? 'Нет доступа к мониторингу этого проекта. Для роли ENGINEER администратор должен выдать отдельный доступ к метрикам проекта. Доступ к серверам и discovery не предоставляет его автоматически. После назначения доступа нажмите «Обновить список».'
        : 'Сервер отклонил операцию: нет доступа к мониторингу проекта или отклонён CSRF-токен. Обновите список интеграций: если он тоже недоступен, обратитесь к администратору за доступом к метрикам проекта. Если список доступен, войдите снова и повторите операцию.'
    if (error.status === 409)
      return 'Интеграция уже существует. Обновите список и измените её настройки.'
    if (error.status === 502) return `Источник метрик недоступен: ${error.message}`
    if (error.status === 503)
      return `Мониторинг временно занят или ожидает восстановления SSH. Повторите вручную через несколько секунд. ${error.message}`
  }
  return messageOf(error)
}
export const date = (value: string | number) => new Date(value).toLocaleString('ru-RU')
export const statusText = (status: string) =>
  ({
    AVAILABLE: 'Данные доступны',
    UP: 'Exporter доступен',
    TARGET_DOWN: 'Exporter недоступен',
    STALE: 'Данные устарели',
    NO_DATA: 'Нет данных',
  })[status] || status
export function seriesName(labels: Record<string, string>): string {
  // Include every label so similarly named devices / recreated containers stay distinct.
  const priority = [
    'container_label_com_docker_compose_service',
    'name',
    'mountpoint',
    'device',
    'id',
  ]
  return (
    Object.entries(labels)
      .sort(([a], [b]) => {
        const order = (key: string) =>
          priority.includes(key) ? priority.indexOf(key) : priority.length
        return order(a) - order(b) || a.localeCompare(b)
      })
      .map(([key, value]) => `${key}=${value}`)
      .join(' · ') || 'Хост'
  )
}
export function formatValue(value: number | null | undefined, unit: string): string {
  if (value == null || !Number.isFinite(value)) return '—'
  if (unit === 'bytes' || unit === 'bytes/s') {
    const power = Math.min(4, Math.max(0, Math.floor(Math.log2(Math.abs(value) || 1) / 10)))
    return `${(value / 1024 ** power).toLocaleString('ru-RU', { maximumFractionDigits: 2 })} ${['B', 'KiB', 'MiB', 'GiB', 'TiB'][power]}${unit === 'bytes/s' ? '/с' : ''}`
  }
  if (unit === 'seconds')
    return `${Math.floor(value / 86400)} д ${Math.floor((value % 86400) / 3600)} ч ${Math.floor((value % 3600) / 60)} мин`
  return `${value.toLocaleString('ru-RU', { maximumFractionDigits: 2 })}${unit === 'percent' ? ' %' : unit === 'cores' ? ' ядер' : ` ${unit}`}`
}
