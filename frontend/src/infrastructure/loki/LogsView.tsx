import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { read, write } from '../api'
import { useResource } from '../useResource'
import { logsError } from './errors'
import type { Integration, TestResult } from './model'
import LogBrowser from './LogBrowser'
import './Logs.css'

export default function LogsView({
  projectId,
  serverId,
  sshConfigured,
}: {
  projectId: string
  serverId: string
  sshConfigured: boolean
}) {
  const base = `/api/project/${encodeURIComponent(projectId)}/integration`
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await read<Integration[]>(base, signal)
      } catch (error) {
        throw new Error(logsError(error), { cause: error })
      }
    },
    [base],
  )
  const resource = useResource(load)
  const source = resource.data?.find((item) => item.serverId === serverId && item.type === 'LOKI')
  if (resource.loading)
    return (
      <p role="status">
        <span className="spinner" /> Загружаем интеграции…
      </p>
    )
  if (resource.error)
    return (
      <div
        className="message error"
        role="alert"
      >
        {resource.error}{' '}
        <button
          className="infra-button"
          onClick={resource.reload}
        >
          Обновить список
        </button>
      </div>
    )
  return (
    <LokiSource
      key={source ? JSON.stringify(source) : serverId}
      base={base}
      initial={source}
      serverId={serverId}
      sshConfigured={sshConfigured}
      reload={resource.reload}
    />
  )
}

function LokiSource({
  base,
  initial,
  serverId,
  sshConfigured,
  reload,
}: {
  base: string
  initial?: Integration
  serverId: string
  sshConfigured: boolean
  reload: () => void
}) {
  const [source, setSource] = useState(initial)
  const [name, setName] = useState(initial?.name ?? 'Loki')
  const [baseUrl, setBaseUrl] = useState(initial?.baseUrl ?? 'http://127.0.0.1:3100')
  const [busy, setBusy] = useState<'save' | 'test' | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [test, setTest] = useState<TestResult | null>(null)
  const [revision, setRevision] = useState(0)
  const controller = useRef<AbortController | null>(null)
  useEffect(() => () => controller.current?.abort(), [])
  const dirty = !source || name.trim() !== source.name || baseUrl.trim() !== source.baseUrl
  async function mutate(kind: 'save' | 'test') {
    if (controller.current) return
    const request = new AbortController()
    controller.current = request
    setBusy(kind)
    setError('')
    setNotice('')
    setTest(null)
    try {
      const path = source ? `${base}/${encodeURIComponent(source.id)}` : base
      if (kind === 'save') {
        const next = await write<Integration>(
          path,
          {
            name: name.trim(),
            baseUrl: baseUrl.trim(),
            ...(!source ? { serverId, type: 'LOKI' } : {}),
          },
          source ? 'PUT' : 'POST',
          request.signal,
        )
        if (request.signal.aborted) return
        setSource(next)
        setName(next.name)
        setBaseUrl(next.baseUrl)
        setRevision((v) => v + 1)
        setNotice('Настройки Loki сохранены.')
      } else {
        const result = await write<TestResult>(path + '/test', undefined, 'POST', request.signal)
        if (!request.signal.aborted) setTest(result)
      }
    } catch (cause) {
      if (!request.signal.aborted) setError(logsError(cause, 'write'))
    } finally {
      if (!request.signal.aborted) {
        controller.current = null
        setBusy(null)
      }
    }
  }
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void mutate('save')
  }
  return (
    <div className="loki-view">
      <div className="infra-section-heading">
        <div>
          <h3>Loki · логи сервера</h3>
          <p className="infra-muted">
            Чтение через SSH. Доступность логов не определяет состояние сервера.
          </p>
        </div>
        <button
          className="infra-button"
          disabled={!!busy}
          onClick={reload}
        >
          Обновить интеграции
        </button>
      </div>
      {!sshConfigured && (
        <p className="infra-hint">Сначала сохраните SSH-настройки на вкладке «SSH-подключение».</p>
      )}
      {!source && (
        <p className="infra-hint">На этом сервере ещё нет интеграции Loki. Создайте её ниже.</p>
      )}
      <details
        className="loki-settings"
        open={source ? undefined : true}
      >
        <summary>Настройки Loki и проверка подключения</summary>
        <form
          className="infra-form"
          onSubmit={save}
        >
          <fieldset disabled={!!busy}>
            <div className="infra-form-grid">
              <label>
                Имя
                <input
                  required
                  maxLength={255}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </label>
              <label>
                Адрес Loki
                <input
                  required
                  type="url"
                  maxLength={255}
                  value={baseUrl}
                  onChange={(e) => setBaseUrl(e.target.value)}
                />
              </label>
            </div>
            <p className="infra-muted">
              Адрес со стороны удалённого SSH-сервера: HTTP на localhost или 127.0.0.1, без пути,
              логина и query-параметров.
            </p>
            <div className="infra-actions">
              <button
                className="infra-button accent"
                disabled={!dirty || !name.trim()}
              >
                {busy === 'save' ? 'Сохраняем…' : source ? 'Сохранить' : 'Создать Loki'}
              </button>
              <button
                className="infra-button"
                type="button"
                disabled={!source || dirty || !sshConfigured}
                onClick={() => void mutate('test')}
              >
                {busy === 'test' ? 'Проверяем…' : 'Проверить подключение'}
              </button>
            </div>
          </fieldset>
        </form>
      </details>
      {source && (
        <p className="infra-muted">
          Статус на момент загрузки/сохранения настроек: {source.status}. Это не фоновая проверка.
        </p>
      )}
      {notice && (
        <p
          className="message success"
          role="status"
        >
          {notice}
        </p>
      )}
      {error && (
        <p
          className="message error"
          role="alert"
        >
          {error}
        </p>
      )}
      {test && (
        <div
          className={`message ${test.status === 'CONNECTED' ? 'success' : 'error'}`}
          role="status"
        >
          Проверка доступности Loki: {test.status} ·{' '}
          {new Date(test.checkedAt).toLocaleString('ru-RU')}
          <p>CONNECTED подтверждает ответ /ready, но не поступление логов из Alloy.</p>
          {test.warnings.map((warning, i) => (
            <p key={i}>{warning}</p>
          ))}
        </div>
      )}
      {source && sshConfigured && busy !== 'save' && (
        <LogBrowser
          key={`${source.id}:${revision}`}
          path={`${base}/${encodeURIComponent(source.id)}/logs`}
        />
      )}
    </div>
  )
}
