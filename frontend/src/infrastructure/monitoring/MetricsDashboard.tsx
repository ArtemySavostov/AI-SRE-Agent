import { date, formatValue, metrics, seriesName, statusText } from './model'
import type { MetricResult } from './model'
import MetricChart from './MetricChart'
import { useMetrics } from './useMetrics'

function Metadata({ result, now }: { result: MetricResult; now: number }) {
  const age = Math.max(0, Math.floor((now - Date.parse(result.fetchedAt)) / 1000))
  const scrapeAge = result.target.lastScrapeAt == null ? null : now - result.target.lastScrapeAt
  return (
    <div className="monitoring-meta">
      <div className="monitoring-status">
        <span className={`infra-badge ${result.status === 'AVAILABLE' ? 'good' : 'neutral'}`}>
          {statusText(result.status)}
        </span>
        <span
          className={`infra-badge ${result.target.status === 'UP' && scrapeAge !== null && scrapeAge <= 60_000 ? 'good' : 'bad'}`}
        >
          {statusText(result.target.status)}
          {result.target.status === 'UP' && scrapeAge !== null && scrapeAge > 60_000
            ? ' · состояние устарело'
            : ''}
        </span>
      </div>
      <small className={age > 60 ? 'monitoring-stale' : ''}>
        Результат получен: {date(result.fetchedAt)} · {age} с назад{age > 60 ? ' · устарел' : ''}
      </small>
      <small>
        Последняя попытка scrape:{' '}
        {result.target.lastScrapeAt == null ? 'нет данных' : date(result.target.lastScrapeAt)}
      </small>
      {result.warnings.map((warning, index) => (
        <p
          className="infra-hint"
          key={index}
        >
          {warning}
        </p>
      ))}
    </div>
  )
}

export default function MetricsDashboard({
  path,
  hours,
  containers,
}: {
  path: string
  hours: number
  containers: boolean
}) {
  const data = useMetrics(path, hours, containers)
  const selected = metrics.filter(([id]) => id.startsWith('container_') === containers)
  return (
    <div aria-busy={data.busy}>
      <div className="infra-actions monitoring-toolbar">
        <button
          className="infra-button"
          disabled={data.busy}
          onClick={data.refresh}
        >
          {data.busy ? 'Загружаем метрики…' : 'Обновить метрики'}
        </button>
        <label>
          <input
            type="checkbox"
            checked={data.auto}
            onChange={(event) => data.setAuto(event.target.checked)}
          />{' '}
          Автообновление · 30 с
        </label>
        <small className="infra-muted">При скрытой вкладке автообновление приостановлено.</small>
      </div>
      {data.error && (
        <div
          className="message error"
          role="alert"
        >
          {data.error}
          <p>
            Цикл загрузки остановлен. Сохранённые результаты ниже могли устареть. Для повторной
            попытки нажмите «Обновить метрики».
          </p>
          {data.auto && <p>Автообновление приостановлено до успешной ручной загрузки.</p>}
        </div>
      )}
      {!containers && (
        <div className="monitoring-summary">
          {metrics.slice(0, 4).map(([id, title]) => {
            const result = data.summary.find((item) => item.metric === id)
            return (
              <article key={id}>
                <h4>{title}</h4>
                {result ? (
                  <>
                    <strong>
                      {formatValue(result.series[0]?.points.at(-1)?.value, result.unit)}
                    </strong>
                    <Metadata
                      result={result}
                      now={data.now}
                    />
                  </>
                ) : (
                  <p>{data.busy ? 'Загрузка сводки…' : 'Сводка не получена'}</p>
                )}
              </article>
            )
          })}
        </div>
      )}
      {containers && (
        <p className="infra-hint">
          CPU — число используемых ядер; память — working set. Ряды различаются по полному набору
          labels. После пересоздания контейнера новый id образует отдельный ряд.
        </p>
      )}
      <div className="monitoring-charts">
        {selected.map(([id, title]) => {
          const result = data.history[id]
          const hasPoints = result?.series.some((series) =>
            series.points.some((point) => point.value !== null),
          )
          return (
            <article
              className="monitoring-card"
              key={id}
            >
              <h3>{title}</h3>
              {result ? (
                <>
                  {hasPoints ? (
                    <MetricChart
                      result={result}
                      title={title}
                    />
                  ) : (
                    <div className="infra-empty compact">
                      Нет данных за выбранный период. Проверьте target; для CPU и скоростей нужны
                      образцы за две минуты.
                    </div>
                  )}
                  <Metadata
                    result={result}
                    now={data.now}
                  />
                  <small className="infra-muted">
                    Период: {date(result.start)} — {date(result.end)} · шаг {result.stepSeconds} с
                  </small>
                  {result.series.length > 0 && (
                    <details className="monitoring-labels">
                      <summary>Ряды · {result.series.length}</summary>
                      {result.series.map((series, index) => (
                        <div key={index}>
                          {index + 1}. {seriesName(series.labels)}
                        </div>
                      ))}
                    </details>
                  )}
                </>
              ) : (
                <p
                  className="infra-muted"
                  role="status"
                >
                  {data.busy ? 'Ожидаем загрузку…' : 'Данные не загружены: цикл прерван.'}
                </p>
              )}
            </article>
          )
        })}
      </div>
    </div>
  )
}
