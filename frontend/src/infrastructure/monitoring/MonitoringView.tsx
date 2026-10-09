import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { read, write } from '../api'
import { useResource } from '../useResource'
import { date, defaults, monitoringError, seriesName, settingsOf } from './model'
import type { Integration, Settings, TestResult } from './model'
import MetricsDashboard from './MetricsDashboard'
import './Monitoring.css'

export default function MonitoringView({
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
        throw new Error(monitoringError(error), { cause: error })
      }
    },
    [base],
  )
  const resource = useResource(load)
  const source = resource.data?.find(
    (item) => item.serverId === serverId && item.type === 'PROMETHEUS',
  )
  if (resource.loading)
    return (
      <p
        role="status"
        className="infra-wait"
      >
        <span className="spinner" /> Загружаем интеграции…
      </p>
    )
  if (resource.error)
    return (
      <div
        className="message error"
        role="alert"
      >
        {resource.error}
        <button
          className="infra-button"
          onClick={resource.reload}
        >
          Обновить список
        </button>
      </div>
    )
  return (
    <MonitoringSource
      key={source ? JSON.stringify(source) : 'new'}
      base={base}
      source={source}
      serverId={serverId}
      sshConfigured={sshConfigured}
      reload={resource.reload}
    />
  )
}

function MonitoringSource({
  base,
  source: initialSource,
  serverId,
  sshConfigured,
  reload,
}: {
  base: string
  source?: Integration
  serverId: string
  sshConfigured: boolean
  reload: () => void
}) {
  const [source, setSource] = useState(initialSource)
  const [view, setView] = useState<'host' | 'containers' | 'settings'>(source ? 'host' : 'settings')
  const [hours, setHours] = useState(1)
  return (
    <div className="monitoring">
      <div className="infra-section-heading">
        <div>
          <h3>Prometheus {source && `· ${source.name}`}</h3>
          <p className="infra-muted">
            Метрики через SSH. Ошибка мониторинга не определяет состояние сервера.
          </p>
        </div>
        <button
          className="infra-button subtle"
          onClick={reload}
        >
          Обновить интеграции
        </button>
      </div>
      {!sshConfigured && (
        <p className="message error">
          Для получения метрик сохраните SSH-подключение на вкладке «SSH-подключение».
        </p>
      )}
      {!source && (
        <p className="infra-hint">
          Для этого сервера ещё нет Prometheus-интеграции. Создайте её ниже.
        </p>
      )}
      {source && (
        <div
          className="infra-actions monitoring-nav"
          aria-label="Разделы мониторинга"
        >
          {(
            [
              ['host', 'Хост'],
              ['containers', 'Контейнеры'],
              ['settings', 'Настройки и проверка'],
            ] as const
          ).map(([id, title]) => (
            <button
              key={id}
              className={`infra-button ${view === id ? 'accent' : ''}`}
              aria-pressed={view === id}
              onClick={() => setView(id)}
            >
              {title}
            </button>
          ))}
        </div>
      )}
      {view === 'settings' ? (
        <SettingsForm
          base={base}
          source={source}
          serverId={serverId}
          reload={reload}
          onSaved={setSource}
        />
      ) : (
        source && (
          <>
            <label className="monitoring-period">
              Период{' '}
              <select
                value={hours}
                onChange={(event) => setHours(Number(event.target.value))}
              >
                {[
                  [1, '1 час'],
                  [6, '6 часов'],
                  [24, '24 часа'],
                  [168, '7 дней'],
                ].map(([value, label]) => (
                  <option
                    key={value}
                    value={value}
                  >
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <MetricsDashboard
              key={`${source.id}:${view}:${hours}`}
              path={`${base}/${encodeURIComponent(source.id)}`}
              hours={hours}
              containers={view === 'containers'}
            />
          </>
        )
      )}
    </div>
  )
}

function SettingsForm({
  base,
  source,
  serverId,
  reload,
  onSaved,
}: {
  base: string
  source?: Integration
  serverId: string
  reload: () => void
  onSaved: (source: Integration) => void
}) {
  const [draft, setDraft] = useState<Settings>(() => (source ? settingsOf(source) : defaults))
  const [saved, setSaved] = useState(source)
  const [busy, setBusy] = useState<'save' | 'test' | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [test, setTest] = useState<TestResult | null>(null)
  const pending = useRef<AbortController | null>(null)
  useEffect(() => () => pending.current?.abort(), [])
  const dirty = !saved || JSON.stringify(draft) !== JSON.stringify(settingsOf(saved))
  function edit(key: keyof Settings, value: string) {
    setDraft((previous) => ({ ...previous, [key]: value }))
    setTest(null)
    setNotice('')
  }
  async function save(event: FormEvent) {
    event.preventDefault()
    if (pending.current) return
    const controller = new AbortController()
    pending.current = controller
    setBusy('save')
    setError('')
    setNotice('')
    setTest(null)
    try {
      const result = await write<Integration>(
        saved ? `${base}/${encodeURIComponent(saved.id)}` : base,
        saved ? draft : { serverId, name: draft.name, baseUrl: draft.baseUrl },
        saved ? 'PUT' : 'POST',
        controller.signal,
      )
      if (controller.signal.aborted) return
      setSaved(result)
      onSaved(result)
      setDraft(settingsOf(result))
      setNotice('Настройки сохранены. Проверьте подключение, затем откройте метрики.')
    } catch (cause) {
      if (!controller.signal.aborted) setError(monitoringError(cause, 'write'))
    } finally {
      if (!controller.signal.aborted) {
        pending.current = null
        setBusy(null)
      }
    }
  }
  async function check() {
    if (!saved || pending.current) return
    const controller = new AbortController()
    pending.current = controller
    setBusy('test')
    setError('')
    setTest(null)
    setNotice('')
    try {
      const result = await write<TestResult>(
        `${base}/${encodeURIComponent(saved.id)}/test`,
        undefined,
        'POST',
        controller.signal,
      )
      if (!controller.signal.aborted) setTest(result)
    } catch (cause) {
      if (!controller.signal.aborted) setError(monitoringError(cause, 'write'))
    } finally {
      if (!controller.signal.aborted) {
        pending.current = null
        setBusy(null)
      }
    }
  }
  return (
    <div className="monitoring-settings">
      <form
        className="infra-form"
        onSubmit={save}
      >
        <h3>{saved ? 'Настройки интеграции' : 'Создание интеграции'}</h3>
        <p className="infra-muted">
          Адрес Prometheus на удалённом SSH-сервере. Поддерживаются http://127.0.0.1:PORT и
          http://localhost:PORT без пути.
        </p>
        <fieldset disabled={!!busy}>
          <label>
            Название
            <input
              required
              maxLength={255}
              value={draft.name}
              onChange={(e) => edit('name', e.target.value)}
            />
          </label>
          <label>
            Адрес Prometheus
            <input
              required
              type="url"
              maxLength={255}
              value={draft.baseUrl}
              onChange={(e) => edit('baseUrl', e.target.value)}
            />
          </label>
          {saved ? (
            <details>
              <summary>Метки exporters · job и instance</summary>
              <div className="infra-form-grid">
                {(['nodeJob', 'nodeInstance', 'containerJob', 'containerInstance'] as const).map(
                  (key) => (
                    <label key={key}>
                      {
                        {
                          nodeJob: 'Node Exporter · job',
                          nodeInstance: 'Node Exporter · instance',
                          containerJob: 'cAdvisor · job',
                          containerInstance: 'cAdvisor · instance',
                        }[key]
                      }
                      <input
                        required
                        maxLength={255}
                        value={draft[key]}
                        onChange={(e) => edit(key, e.target.value)}
                      />
                    </label>
                  ),
                )}
              </div>
            </details>
          ) : (
            <p className="infra-muted">
              При создании: node-exporter / node-exporter:9100 и cadvisor / cadvisor:8080. После
              сохранения эти метки можно изменить.
            </p>
          )}
          <div className="infra-actions">
            <button
              className="infra-button accent"
              disabled={!dirty}
              type="submit"
            >
              {busy === 'save' ? 'Сохраняем…' : saved ? 'Сохранить' : 'Создать интеграцию'}
            </button>
            <button
              type="button"
              className="infra-button"
              disabled={!saved || dirty}
              onClick={check}
            >
              {busy === 'test' ? 'Проверяем…' : 'Проверить Prometheus'}
            </button>
            {saved && (
              <button
                type="button"
                className="infra-button"
                disabled={dirty}
                onClick={reload}
              >
                Открыть метрики
              </button>
            )}
          </div>
        </fieldset>
        {error && (
          <div
            className="message error"
            role="alert"
          >
            {error}
            <button
              type="button"
              className="infra-link"
              onClick={reload}
            >
              Обновить список интеграций
            </button>
          </div>
        )}
        {notice && (
          <p
            className="message success"
            role="status"
          >
            {notice}
          </p>
        )}
      </form>
      {test && (
        <section aria-label="Результат проверки">
          <p className="message success">
            Prometheus: {test.status === 'CONNECTED' ? 'API доступен' : test.status}
            <br />
            <small>
              Проверено: {date(test.checkedAt)}. Состояния exporters приведены отдельно ниже.
            </small>
          </p>
          <h3>Targets</h3>
          {test.targets.length === 0 && (
            <p className="infra-hint">Targets не найдены. Проверьте конфигурацию Prometheus.</p>
          )}
          {saved &&
            (['node', 'container'] as const).map((kind) => {
              const settings = settingsOf(saved)
              return test.targets.some(
                (target) =>
                  target.labels.job === settings[`${kind}Job`] &&
                  target.labels.instance === settings[`${kind}Instance`],
              ) ? null : (
                <p
                  className="message error"
                  key={kind}
                >
                  {kind === 'node' ? 'Node Exporter' : 'cAdvisor'}: настроенный target отсутствует в
                  результате проверки.
                </p>
              )
            })}
          <div className="monitoring-targets">
            {test.targets.map((target, index) => (
              <article key={index}>
                <strong>{seriesName(target.labels)}</strong>
                <span className={`infra-badge ${target.up && !target.stale ? 'good' : 'bad'}`}>
                  {target.stale ? 'Устаревший scrape' : target.up ? 'UP' : 'TARGET_DOWN'}
                </span>
                <small>
                  Последняя попытка scrape:{' '}
                  {target.sampledAt ? date(target.sampledAt) : 'неизвестна'}
                </small>
              </article>
            ))}
          </div>
          {test.warnings.map((warning, index) => (
            <p
              className="infra-hint"
              key={index}
            >
              {warning}
            </p>
          ))}
        </section>
      )}
    </div>
  )
}
