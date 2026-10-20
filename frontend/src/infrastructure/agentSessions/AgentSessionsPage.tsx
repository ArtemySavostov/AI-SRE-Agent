import { useCallback, useEffect, useRef, useState } from 'react'
import type { User } from '../../api'
import ThemeToggle from '../../ThemeToggle'
import { read, serverPath } from '../api'
import type { Project, Server, ManagedService } from '../api'
import ProjectNavigation from '../ProjectNavigation'
import { useResource } from '../useResource'
import { sessionApi } from './api'
import { createSessionController, initialState } from './controller'
import { errorText, formatDate, statusNames } from './model'
import SessionDetails from './SessionDetails'
import '../../ProfilePage.css'
import '../Infrastructure.css'
import './AgentSessions.css'

type Context = { project: string; server: string; service: string; session: string }
function readContext(): Context {
  const query = new URLSearchParams(location.search)
  return {
    project: query.get('project') || '',
    server: query.get('server') || '',
    service: query.get('service') || '',
    session: query.get('session') || '',
  }
}
function useList<T>(path: string) {
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await read<T[]>(path, signal)
      } catch (error) {
        throw new Error(errorText(error), { cause: error })
      }
    },
    [path],
  )
  return useResource(load)
}
function ListStatus({
  resource,
  label,
  selected,
  found,
}: {
  resource: { loading: boolean; error: string; data?: unknown[]; reload: () => void }
  label: string
  selected: string
  found: boolean
}) {
  return (
    <>
      {resource.loading && <p role="status">Загружаем {label}…</p>}
      {resource.error && (
        <p
          className="message error"
          role="alert"
        >
          {resource.error}{' '}
          <button
            className="infra-button"
            onClick={resource.reload}
          >
            Повторить
          </button>
        </p>
      )}
      {!resource.loading &&
        !resource.error &&
        (!resource.data?.length ? (
          <p>Нет доступных ресурсов: {label}.</p>
        ) : selected && !found ? (
          <p role="alert">Выбранный ресурс недоступен в текущем контексте. Выберите другой.</p>
        ) : null)}
    </>
  )
}
export default function AgentSessionsPage({
  user,
  busy,
  error,
  onSignOut,
}: {
  user: User
  busy: boolean
  error: string
  onSignOut: () => Promise<void>
}) {
  const [context, setContext] = useState(readContext)
  const projects = useList<Project>('/api/project')
  const project = projects.data?.find((item) => item.id === context.project)
  const update = useCallback((next: Context) => {
    const query = new URLSearchParams(Object.entries(next).filter(([, value]) => !!value))
    history.pushState({}, '', '/agent-sessions' + (query.size ? '?' + query : ''))
    setContext(next)
  }, [])
  useEffect(() => {
    document.title = 'Сессии агента — SRE Platform'
  }, [])
  return (
    <div className="app-shell infra-shell">
      <header className="topbar">
        <a
          className="brand"
          href="/infrastructure"
        >
          ◇ SRE PLATFORM
        </a>
        <span className="topbar-caption">СЕССИИ АГЕНТА</span>
        <a
          className="profile-top-user"
          href="/profile"
        >
          {user.name}
        </a>
        <ThemeToggle />
      </header>
      <div className="infra-layout">
        <aside className="infra-sidebar">
          <nav aria-label="Рабочее пространство">
            <a
              className="profile-nav-link"
              href="/infrastructure"
            >
              Инфраструктура
            </a>
            <a
              className="profile-nav-link"
              href="/profile"
            >
              Мой профиль
            </a>
          </nav>
          <span className="eyebrow">ПРОЕКТЫ</span>
          <ProjectNavigation
            projects={projects.data ?? []}
            currentId={project?.id}
            section="agent-sessions"
          />
          <div className="infra-sidebar-bottom">
            <button
              className="infra-link"
              disabled={busy}
              onClick={onSignOut}
            >
              Выйти из аккаунта
            </button>
          </div>
        </aside>
        <main className="agent-page">
          <div className="profile-breadcrumb">{project?.name ?? 'Проект'} / Сессии агента</div>
          <h1>Сессии агента</h1>
          <p className="agent-warning">
            Тестовый режим: реальная диагностика, AI-анализ и восстановление не выполняются
          </p>
          <p className="infra-muted">
            Время показано как на backend; часовой пояс в API не указан.
          </p>
          {error && (
            <p
              className="message error"
              role="alert"
            >
              {error}
            </p>
          )}
          <div className="infra-form agent-filter">
            <label>
              Проект
              <select
                value={context.project}
                onChange={(e) =>
                  update({ project: e.target.value, server: '', service: '', session: '' })
                }
              >
                <option value="">Выберите проект</option>
                {projects.data?.map((p) => (
                  <option
                    key={p.id}
                    value={p.id}
                  >
                    {p.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <ListStatus
            resource={projects}
            label="проекты"
            selected={context.project}
            found={!!project}
          />
          {project && (
            <ProjectContext
              key={project.id}
              context={context}
              update={update}
            />
          )}
        </main>
      </div>
    </div>
  )
}
function ProjectContext({
  context,
  update,
}: {
  context: Context
  update: (next: Context) => void
}) {
  const servers = useList<Server>(`/api/project/${encodeURIComponent(context.project)}/server`)
  const server = servers.data?.find(
    (s) => s.id === context.server && s.projectId === context.project,
  )
  return (
    <>
      <div className="infra-form agent-filter">
        <label>
          Сервер
          <select
            value={context.server}
            onChange={(e) =>
              update({ ...context, server: e.target.value, service: '', session: '' })
            }
          >
            <option value="">Выберите сервер</option>
            {servers.data
              ?.filter((s) => s.projectId === context.project)
              .map((s) => (
                <option
                  key={s.id}
                  value={s.id}
                >
                  {s.name} · {s.hostname}
                </option>
              ))}
          </select>
        </label>
      </div>
      <ListStatus
        resource={servers}
        label="серверы"
        selected={context.server}
        found={!!server}
      />
      {server && (
        <ServerContext
          key={server.id}
          context={context}
          update={update}
        />
      )}
    </>
  )
}
function ServerContext({ context, update }: { context: Context; update: (next: Context) => void }) {
  const services = useList<ManagedService>(serverPath(context.project, context.server) + '/service')
  const belongs = (s: ManagedService) =>
    s.projectId === context.project && s.serverId === context.server
  const service = services.data?.find((s) => s.id === context.service && belongs(s))
  // The service workspace keeps its controller when only the selected session changes.
  const onSelect = useCallback(
    (id: string) =>
      update({
        project: context.project,
        server: context.server,
        service: context.service,
        session: id,
      }),
    [context.project, context.server, context.service, update],
  )
  return (
    <>
      <div className="infra-form agent-filter">
        <label>
          Сервис
          <select
            value={context.service}
            onChange={(e) => update({ ...context, service: e.target.value, session: '' })}
          >
            <option value="">Выберите сервис</option>
            {services.data?.filter(belongs).map((s) => (
              <option
                key={s.id}
                value={s.id}
              >
                {s.name}
              </option>
            ))}
          </select>
        </label>
      </div>
      <ListStatus
        resource={services}
        label="сервисы"
        selected={context.service}
        found={!!service}
      />
      {service && (
        <ServiceSessions
          key={service.id}
          service={service}
          initialId={context.session}
          onSelect={onSelect}
        />
      )}
    </>
  )
}
function ServiceSessions({
  service,
  initialId,
  onSelect,
}: {
  service: ManagedService
  initialId: string
  onSelect: (id: string) => void
}) {
  const [state, setState] = useState(() => initialState(initialId))
  const initial = useRef(initialId)
  const controller = useRef<ReturnType<typeof createSessionController> | null>(null)
  useEffect(() => {
    const current = createSessionController({
      serviceId: service.id,
      selectedId: initial.current,
      api: sessionApi,
      change: setState,
      select: onSelect,
      visible: () => !document.hidden,
      schedule: (callback, ms) => {
        const timer = setTimeout(callback, ms)
        return () => clearTimeout(timer)
      },
    })
    controller.current = current
    void current.refresh()
    document.addEventListener('visibilitychange', current.visibilityChanged)
    return () => {
      current.dispose()
      document.removeEventListener('visibilitychange', current.visibilityChanged)
      controller.current = null
    }
  }, [service.id, onSelect])
  return (
    <>
      <div className="infra-actions agent-toolbar">
        <button
          className="infra-button accent"
          disabled={state.mutating}
          onClick={() => void controller.current?.command('create')}
        >
          {state.mutating ? 'Выполняем команду…' : 'Создать сессию'}
        </button>
        <button
          className="infra-button"
          disabled={state.loading || state.mutating}
          onClick={() => void controller.current?.refresh()}
        >
          Обновить историю и сессию
        </button>
      </div>
      {state.loading && <p role="status">Обновляем данные…</p>}
      {state.notice && (
        <p
          className="message"
          role="status"
        >
          {state.notice}
        </p>
      )}
      {state.error && (
        <p
          className="message error"
          role="alert"
        >
          {state.error} Последние полученные данные сохранены.{' '}
          <button
            className="infra-button"
            disabled={state.mutating || state.loading}
            onClick={() => void controller.current?.refresh()}
          >
            Повторить
          </button>
        </p>
      )}
      <div className="agent-columns">
        <section
          className="agent-card"
          aria-labelledby="history-title"
        >
          <h2 id="history-title">История · {service.name}</h2>
          {!state.history.length && !state.loading && (
            <p>
              {state.error
                ? 'История пока недоступна.'
                : 'Сессий пока нет. Создайте сессию, затем запустите её отдельной кнопкой.'}
            </p>
          )}
          <ul className="agent-history">
            {state.history.map((session) => (
              <li key={session.id}>
                <span className="infra-badge neutral">
                  {statusNames[session.status] ?? session.status}
                </span>
                <p>Создана: {formatDate(session.createdAt)}</p>
                {session.startedAt && <p>Запущена: {formatDate(session.startedAt)}</p>}
                {session.finishedAt && <p>Завершена: {formatDate(session.finishedAt)}</p>}
                <details>
                  <summary>ID: {session.id.slice(0, 8)}…</summary>
                  <span>{session.id}</span>
                </details>
                <button
                  className="infra-button"
                  aria-pressed={session.id === state.selectedId}
                  onClick={() => controller.current?.select(session.id)}
                >
                  Открыть сессию {session.id.slice(0, 8)}
                </button>
              </li>
            ))}
          </ul>
        </section>
        <SessionDetails
          state={state}
          serviceName={service.name}
          command={(action) => void controller.current?.command(action)}
        />
      </div>
    </>
  )
}
