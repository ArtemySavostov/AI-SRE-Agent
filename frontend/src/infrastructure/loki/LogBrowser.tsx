import { memo, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { read } from '../api'
import { defaults, formattedJson, parseResult, queryParams, timestampDate } from './model'
import type { LogEntry, LogResult, Query } from './model'
import { logsError } from './errors'
import { createPolling } from './polling'

const date = (value: string) => new Date(value).toLocaleString('ru-RU')
export default function LogBrowser({ path }: { path: string }) {
  const [draft, setDraft] = useState<Query>(defaults)
  const [applied, setApplied] = useState<Query>(defaults)
  const [error, setError] = useState('')
  const [auto, setAuto] = useState(false)
  function edit<K extends keyof Query>(key: K, value: Query[K]) {
    setDraft((old) => ({ ...old, [key]: value }))
  }
  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      queryParams(draft)
      setApplied({ ...draft })
      setError('')
    } catch (cause) {
      setError(logsError(cause))
    }
  }
  return (
    <section aria-label="Просмотр логов">
      <form
        className="infra-form loki-filters"
        onSubmit={apply}
      >
        <div className="loki-filter-grid">
          {(
            [
              ['service', 'Сервис (service_name)'],
              ['container', 'Контейнер (container)'],
              ['composeProject', 'Compose-проект (compose_project)'],
              ['search', 'Поиск в исходной строке'],
            ] as const
          ).map(([key, title]) => (
            <label key={key}>
              {title}
              <input
                value={draft[key]}
                maxLength={key === 'search' ? 1024 : 255}
                onChange={(e) => edit(key, e.target.value)}
              />
            </label>
          ))}
          <label>
            Период
            <select
              value={draft.minutes}
              onChange={(e) => edit('minutes', e.target.value)}
            >
              {(
                [
                  ['15', '15 минут'],
                  ['60', '1 час'],
                  ['360', '6 часов'],
                  ['1440', '24 часа'],
                  ['10080', '7 дней'],
                  ['custom', 'Свой диапазон'],
                ] as const
              ).map(([v, label]) => (
                <option
                  key={v}
                  value={v}
                >
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Лимит
            <select
              value={draft.limit}
              onChange={(e) => edit('limit', e.target.value)}
            >
              {[100, 500, 1000].map((v) => (
                <option key={v}>{v}</option>
              ))}
            </select>
          </label>
          <label>
            Порядок
            <select
              value={draft.direction}
              onChange={(e) => edit('direction', e.target.value as Query['direction'])}
            >
              <option value="backward">Новые сначала</option>
              <option value="forward">Старые сначала</option>
            </select>
          </label>
          {draft.minutes === 'custom' && (
            <>
              <label>
                Начало (включительно)
                <input
                  type="datetime-local"
                  step="1"
                  required
                  value={draft.start}
                  onChange={(e) => edit('start', e.target.value)}
                />
              </label>
              <label>
                Конец (исключительно)
                <input
                  type="datetime-local"
                  step="1"
                  required
                  value={draft.end}
                  onChange={(e) => edit('end', e.target.value)}
                />
              </label>
            </>
          )}
        </div>
        <p className="infra-muted">
          Метки совпадают точно. Поиск подстроки учитывает регистр. Без меток выбираются записи с
          непустым service_name. Compose-проект — фильтр, а не граница доступа.
        </p>
        <p className="infra-muted">
          Время — в часовом поясе браузера ({Intl.DateTimeFormat().resolvedOptions().timeZone}).
          Максимальный диапазон — 7 дней.
        </p>
        <div className="infra-actions">
          <button className="infra-button accent">Применить</button>
          {JSON.stringify(draft) !== JSON.stringify(applied) && (
            <span>Есть неприменённые изменения. Ниже — результат применённых фильтров.</span>
          )}
        </div>
        {error && (
          <p
            className="message error"
            role="alert"
          >
            {error}
          </p>
        )}
      </form>
      <Results
        key={`${path}:${JSON.stringify(applied)}`}
        path={path}
        query={applied}
        auto={auto}
        setAuto={setAuto}
      />
    </section>
  )
}

function Results({
  path,
  query,
  auto,
  setAuto,
}: {
  path: string
  query: Query
  auto: boolean
  setAuto: (value: boolean) => void
}) {
  const [data, setData] = useState<LogResult | null>(null)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState('')
  const [hidden, setHidden] = useState(() => document.hidden)
  const poll = useRef<ReturnType<typeof createPolling<LogResult>> | null>(null)
  useEffect(() => {
    const loop = createPolling({
      load: async (signal) =>
        parseResult(await read<unknown>(`${path}?${queryParams(query)}`, signal)),
      success: (result) => {
        setData(result)
        setError('')
      },
      failure: (cause) => setError(logsError(cause)),
      busy: setBusy,
      visible: () => !document.hidden,
      schedule: (callback, ms) => {
        const timer = setTimeout(callback, ms)
        return () => clearTimeout(timer)
      },
    })
    poll.current = loop
    const visibility = () => {
      setHidden(document.hidden)
      loop.visibilityChanged()
    }
    document.addEventListener('visibilitychange', visibility)
    void loop.refresh()
    return () => {
      loop.dispose()
      poll.current = null
      document.removeEventListener('visibilitychange', visibility)
    }
  }, [path, query])
  useEffect(() => {
    poll.current?.setAuto(auto)
  }, [auto, path, query])
  return (
    <>
      <div className="infra-actions loki-toolbar">
        <button
          className="infra-button"
          disabled={busy}
          onClick={() => void poll.current?.refresh()}
        >
          {busy
            ? data
              ? 'Обновляем…'
              : 'Загружаем…'
            : error
              ? 'Повторить / возобновить'
              : 'Обновить'}
        </button>
        <label>
          <input
            type="checkbox"
            checked={auto}
            onChange={(e) => setAuto(e.target.checked)}
          />{' '}
          Автообновление раз в 30 секунд
        </label>
      </div>
      <p className="infra-muted">
        Автообновление повторно получает ограниченную выдачу: это не live tail и не полный поток.{' '}
        {auto && hidden && 'При скрытой вкладке автообновление приостановлено.'}
      </p>
      {error && (
        <div
          className="message error"
          role="alert"
        >
          {error}
          <p>Автообновление приостановлено до ручного обновления или применения фильтров.</p>
          {data && (
            <strong>Показана предыдущая выдача. Её диапазон и время получения указаны ниже.</strong>
          )}
        </div>
      )}
      {busy && <p role="status">{data ? 'Обновление выдачи…' : 'Первая загрузка логов…'}</p>}
      {data && (
        <LogList
          key={data.fetchedAt}
          data={data}
        />
      )}
    </>
  )
}

function LogList({ data }: { data: LogResult }) {
  const [page, setPage] = useState(0)
  const pages = Math.ceil(data.entries.length / 100)
  const [wrap, setWrap] = useState(true)
  return (
    <div className="loki-results">
      <div className="loki-result-meta">
        <p>
          Диапазон выдачи: <time dateTime={data.start}>{date(data.start)}</time> —{' '}
          <time dateTime={data.end}>{date(data.end)}</time> (конец не включён).
        </p>
        <p>
          Ответ получен: <time dateTime={data.fetchedAt}>{date(data.fetchedAt)}</time> · Записей:{' '}
          {data.entries.length} ·{' '}
          {data.direction === 'backward' ? 'Новые сначала' : 'Старые сначала'}
        </p>
      </div>
      {data.limitReached && (
        <p
          className="loki-warning"
          role="status"
        >
          <strong>Выдача неполная: достигнут лимит.</strong> Сузьте период или фильтры. Увеличение
          лимита не гарантирует полноту.
        </p>
      )}
      {data.warnings.map((warning, i) => (
        <p
          className="infra-hint"
          key={i}
        >
          {warning}
        </p>
      ))}
      {!data.entries.length && (
        <div className="infra-empty compact">
          <h3>В этом диапазоне записи не найдены</h3>
          <p>
            Это не означает остановку приложения или неисправность Alloy. Проверьте период и точные
            значения фильтров.
          </p>
        </div>
      )}
      {pages > 1 && (
        <nav
          className="infra-actions loki-toolbar"
          aria-label="Страницы полученной выдачи"
        >
          <button
            className="infra-button"
            disabled={page === 0}
            onClick={() => setPage((v) => v - 1)}
          >
            Назад
          </button>
          <span>
            Страница {page + 1} из {pages} уже полученных записей
          </span>
          <button
            className="infra-button"
            disabled={page + 1 >= pages}
            onClick={() => setPage((v) => v + 1)}
          >
            Вперёд
          </button>
        </nav>
      )}
      <label className="loki-wrap">
        <input
          type="checkbox"
          checked={wrap}
          onChange={(e) => setWrap(e.target.checked)}
        />{' '}
        Перенос строк
      </label>
      <div
        className={`loki-entries ${wrap ? '' : 'loki-nowrap'}`}
        aria-label="Записи логов"
      >
        {data.entries.slice(page * 100, (page + 1) * 100).map((entry, i) => (
          <Entry
            key={`${page * 100 + i}:${entry.timestampNs}`}
            entry={entry}
          />
        ))}
      </div>
    </div>
  )
}
const Entry = memo(function Entry({ entry }: { entry: LogEntry }) {
  const [open, setOpen] = useState(false)
  const [json, setJson] = useState<{ text?: string } | null>(null)
  const timestamp = timestampDate(entry.timestampNs)
  return (
    <details
      className="loki-entry"
      onToggle={(e) => setOpen(e.currentTarget.open)}
    >
      <summary className="loki-row">
        <time
          dateTime={timestamp.toISOString()}
          title={`Unix nanoseconds: ${entry.timestampNs}`}
        >
          {timestamp.toLocaleString('ru-RU', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            fractionalSecondDigits: 3,
          })}
        </time>
        <span
          className="loki-stream"
          title={JSON.stringify(entry.labels)}
        >
          {entry.labels.service_name || entry.labels.container || '—'}
        </span>
        <span className="loki-preview">
          {entry.line.slice(0, 4000)}
          {entry.line.length > 4000 ? '…' : ''}
        </span>
      </summary>
      {open && (
        <div className="loki-entry-details">
          <p>
            Время записи: {timestamp.toISOString()} · Unix nanoseconds:{' '}
            <code>{entry.timestampNs}</code>
          </p>
          <h4>Исходная строка</h4>
          <pre>{entry.line}</pre>
          <h4>Labels</h4>
          <pre>{JSON.stringify(entry.labels, null, 2)}</pre>
          <h4>Metadata</h4>
          <pre>{JSON.stringify(entry.metadata, null, 2)}</pre>
          {json === null ? (
            <button
              className="infra-button"
              onClick={() => setJson({ text: formattedJson(entry.line) })}
            >
              Разобрать как JSON
            </button>
          ) : json.text !== undefined ? (
            <>
              <h4>Форматированный JSON</h4>
              <pre>{json.text}</pre>
            </>
          ) : (
            <p>Строка не является JSON.</p>
          )}
        </div>
      )}
    </details>
  )
})
