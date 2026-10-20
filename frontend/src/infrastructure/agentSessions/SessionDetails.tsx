import { actionsFor, eventNames, formatDate, statusNames } from './model'
import type { SessionState } from './controller'
export default function SessionDetails({
  state,
  serviceName,
  command,
}: {
  state: SessionState
  serviceName: string
  command: (action: 'start' | 'cancel') => void
}) {
  const session = state.session
  if (!session)
    return (
      <p>
        {state.selectedId ? 'Подробности сессии пока недоступны.' : 'Выберите сессию в истории.'}
      </p>
    )
  return (
    <section
      className="agent-card"
      aria-labelledby="session-title"
    >
      <h2 id="session-title">Подробности сессии</h2>
      <dl className="agent-facts">
        <dt>ID</dt>
        <dd>{session.id}</dd>
        <dt>Сервис</dt>
        <dd>{serviceName}</dd>
        <dt>Статус</dt>
        <dd>{statusNames[session.status] ?? session.status}</dd>
        <dt>Создана</dt>
        <dd>{formatDate(session.createdAt)}</dd>
        <dt>Запущена</dt>
        <dd>{formatDate(session.startedAt)}</dd>
        <dt>Завершена</dt>
        <dd>{formatDate(session.finishedAt)}</dd>
        <dt>Обновлена</dt>
        <dd>{formatDate(session.updatedAt)}</dd>
        <dt>Попыток восстановления</dt>
        <dd>{session.recoveryAttempts}</dd>
      </dl>
      <div className="infra-actions">
        {actionsFor(session.status).map((action) => (
          <button
            key={action}
            className="infra-button"
            disabled={state.mutating || !!state.error}
            onClick={() => command(action)}
          >
            {action === 'start' ? 'Запустить' : 'Отменить'}
          </button>
        ))}
      </div>
      {session.summary && (
        <>
          <h3>Итог</h3>
          <p className="agent-text">{session.summary}</p>
        </>
      )}
      {session.errorMessage && (
        <>
          <h3>Ошибка</h3>
          <p className="agent-text message error">{session.errorMessage}</p>
        </>
      )}
      <h3>История событий</h3>
      {!state.events.length && <p className="infra-muted">События пока не получены.</p>}
      <ol className="agent-events">
        {state.events.map((event) => (
          <li key={event.id}>
            <time>{formatDate(event.createdAt)}</time>
            <strong>{eventNames[event.type] ?? `Неизвестное событие: ${event.type}`}</strong>
            <p className="agent-text">{event.message}</p>
          </li>
        ))}
      </ol>
    </section>
  )
}
