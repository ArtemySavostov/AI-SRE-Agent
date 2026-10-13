import { useCallback, useEffect, useState } from 'react'
import type { User } from '../../api'
import ThemeToggle from '../../ThemeToggle'
import { optional, read, serverPath } from '../api'
import type { Project, Server, SshSettings } from '../api'
import { useResource } from '../useResource'
import { logsError } from './errors'
import LogsView from './LogsView'
import '../../ProfilePage.css'
import '../Infrastructure.css'

export default function LogsPage({
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
  const [projectId, setProjectId] = useState(
    () => new URLSearchParams(location.search).get('project') || '',
  )
  const load = useCallback((signal: AbortSignal) => read<Project[]>('/api/project', signal), [])
  const projects = useResource(load)
  const project =
    projects.data?.find((p) => p.id === projectId) ?? (!projectId ? projects.data?.[0] : undefined)
  useEffect(() => {
    document.title = 'Логи — SRE Platform'
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
        <span className="topbar-caption">ЛОГИ</span>
        <a
          className="profile-top-user"
          href="/profile"
        >
          {user.name}
        </a>
        <ThemeToggle />
      </header>
      <main className="loki-page">
        <nav
          className="infra-actions loki-toolbar"
          aria-label="Рабочее пространство"
        >
          <a
            className="infra-button"
            href="/infrastructure"
          >
            Инфраструктура
          </a>
          <a
            className="infra-button"
            href="/logs"
            aria-current="page"
          >
            Логи
          </a>
          <a
            className="infra-button"
            href="/profile"
          >
            Мой профиль
          </a>
          <button
            className="infra-button"
            disabled={busy}
            onClick={onSignOut}
          >
            Выйти
          </button>
        </nav>
        <h1>Логи</h1>
        <p className="infra-muted">Выберите проект и сервер для просмотра записей Loki.</p>
        {error && (
          <p
            className="message error"
            role="alert"
          >
            {error}
          </p>
        )}
        {projects.loading && <p role="status">Загружаем проекты…</p>}
        {projects.error && (
          <p
            className="message error"
            role="alert"
          >
            {projects.error}{' '}
            <button
              className="infra-button"
              onClick={projects.reload}
            >
              Повторить
            </button>
          </p>
        )}
        <div className="infra-form loki-filter-grid">
          <label>
            Проект
            <select
              value={project?.id ?? ''}
              onChange={(e) => {
                setProjectId(e.target.value)
                history.replaceState(
                  {},
                  '',
                  '/logs?' + new URLSearchParams({ project: e.target.value }),
                )
              }}
            >
              <option
                value=""
                disabled
              >
                Выберите проект
              </option>
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
        {!projects.loading && !projects.error && !project && (
          <p>
            Выберите доступный проект. Если проектов ещё нет, создайте его в разделе
            «Инфраструктура».
          </p>
        )}
        {project && (
          <ProjectLogs
            key={project.id}
            projectId={project.id}
          />
        )}
      </main>
    </div>
  )
}
function ProjectLogs({ projectId }: { projectId: string }) {
  const [serverId, setServerId] = useState(
    () => new URLSearchParams(location.search).get('server') || '',
  )
  const load = useCallback(
    (signal: AbortSignal) =>
      read<Server[]>(`/api/project/${encodeURIComponent(projectId)}/server`, signal),
    [projectId],
  )
  const servers = useResource(load)
  const server =
    servers.data?.find((s) => s.id === serverId) ?? (!serverId ? servers.data?.[0] : undefined)
  return (
    <>
      <div className="infra-form loki-filter-grid">
        <label>
          Сервер
          <select
            value={server?.id ?? ''}
            onChange={(e) => {
              setServerId(e.target.value)
              history.replaceState(
                {},
                '',
                '/logs?' + new URLSearchParams({ project: projectId, server: e.target.value }),
              )
            }}
          >
            <option
              value=""
              disabled
            >
              Выберите сервер
            </option>
            {servers.data?.map((s) => (
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
      {servers.loading && <p role="status">Загружаем серверы…</p>}
      {servers.error && (
        <p
          className="message error"
          role="alert"
        >
          {servers.error}{' '}
          <button
            className="infra-button"
            onClick={servers.reload}
          >
            Повторить
          </button>
        </p>
      )}
      {!servers.loading && !servers.error && !server && (
        <p>Выберите доступный сервер. Серверы добавляются в разделе «Инфраструктура».</p>
      )}
      {server && (
        <ServerLogs
          key={server.id}
          server={server}
        />
      )}
    </>
  )
}
function ServerLogs({ server }: { server: Server }) {
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await optional<SshSettings>(serverPath(server.projectId, server.id) + '/ssh', signal)
      } catch (cause) {
        throw new Error(logsError(cause), { cause })
      }
    },
    [server.projectId, server.id],
  )
  const ssh = useResource(load)
  if (ssh.loading) return <p role="status">Проверяем настройки сервера…</p>
  if (ssh.error)
    return (
      <p
        className="message error"
        role="alert"
      >
        {ssh.error}{' '}
        <button
          className="infra-button"
          onClick={ssh.reload}
        >
          Повторить
        </button>
      </p>
    )
  return (
    <>
      <p>
        <a
          href={
            '/infrastructure?' +
            new URLSearchParams({ project: server.projectId, server: server.id })
          }
        >
          Настройки сервера {server.name}
        </a>
      </p>
      <LogsView
        projectId={server.projectId}
        serverId={server.id}
        sshConfigured={!!ssh.data}
      />
    </>
  )
}
