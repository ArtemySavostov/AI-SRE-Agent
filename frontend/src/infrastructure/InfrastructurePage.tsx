import { useCallback, useEffect, useState } from 'react'
import type { User } from '../api'
import { read, optional, write, serverPath } from './api'
import type { Project, Server, ManagedService, SshSettings, DiscoveryJob } from './api'
import { useResource } from './useResource'
import { CreationDialog } from './Forms'
import ServerDetails from './ServerDetails'
import '../ProfilePage.css'
import './Infrastructure.css'

function selection(projectId: string | null, serverId?: string | null) {
  const query = new URLSearchParams()
  if (projectId) query.set('project', projectId)
  if (serverId) query.set('server', serverId)
  window.history.replaceState({}, '', '/infrastructure' + (query.size ? '?' + query : ''))
}
export default function InfrastructurePage({ user, busy, error, onSignOut }: { user: User; busy: boolean; error: string; onSignOut: () => Promise<void> }) {
  const [selected, setSelected] = useState<string | null>(() => new URLSearchParams(window.location.search).get('project'))
  const [creating, setCreating] = useState(false)
  const load = useCallback((signal: AbortSignal) => read<Project[]>('/api/project', signal), [])
  const projects = useResource(load)
  const current = selected ? projects.data?.find(project => project.id === selected) : projects.data?.[0]
  useEffect(() => { document.title = 'Инфраструктура — SRE Platform' }, [])
  function select(id: string | null) { setSelected(id); selection(id) }
  return <div className="app-shell infra-shell">
    <header className="topbar"><a className="brand" href="/infrastructure"><span className="profile-brand-icon" aria-hidden="true">◇</span><span>SRE<span className="brand-light"> PLATFORM</span></span></a><span className="topbar-caption">ИНФРАСТРУКТУРА</span><a className="profile-top-user" href="/profile">{user.name} <span aria-hidden="true">↗</span></a></header>
    <div className="infra-layout"><aside className="infra-sidebar">
      <nav aria-label="Рабочее пространство"><a href="/infrastructure" className="profile-nav-current" aria-current="page">▤ Инфраструктура</a><a href="/profile" className="profile-nav-link">◎ Мой профиль</a></nav>
      <div className="infra-project-heading"><span className="eyebrow">ПРОЕКТЫ · {projects.data?.length ?? '—'}</span><button className="infra-icon-button" aria-label="Создать проект" onClick={() => setCreating(true)}>+</button></div>
      {projects.loading && !projects.data && <p role="status" className="infra-muted">Загружаем проекты…</p>}
      {projects.error && <div className="infra-inline-error" role="alert">{projects.error}<button className="infra-link" onClick={projects.reload}>Повторить</button></div>}
      <div className="infra-project-list">{projects.data?.map(project => <button key={project.id} className={current?.id === project.id ? 'selected' : ''} aria-pressed={current?.id === project.id} onClick={() => select(project.id)}><span className="infra-project-icon" aria-hidden="true">{project.name.slice(0, 1).toUpperCase()}</span><span>{project.name}</span></button>)}</div>
      <div className="infra-sidebar-bottom"><span className="infra-muted">{user.role === 'ADMIN' ? 'Администратор' : 'Инженер'}</span><button className="infra-link" disabled={busy} onClick={onSignOut}>{busy ? 'Выходим…' : 'Выйти из аккаунта'}</button></div>
    </aside><main className="infra-main">
      {error && <p className="message error" role="alert">{error}</p>}
      <div className="infra-page-heading"><div><div className="eyebrow">ВАШЕ РАБОЧЕЕ ПРОСТРАНСТВО</div><h1>Инфраструктура</h1><p>Проекты, серверы и сервисы — от подключения до инвентаризации.</p></div><button className="infra-button" onClick={() => setCreating(true)}>+ Создать проект</button></div>
      {!projects.loading && projects.data?.length === 0 && <div className="infra-empty"><span aria-hidden="true">◇</span><h2>Начните с проекта</h2><p>Добавьте проект, затем сервер и настройте SSH для обнаружения Docker-контейнеров.</p><button className="infra-button accent" onClick={() => setCreating(true)}>Создать первый проект</button></div>}
      {current && <ProjectWorkspace key={current.id} project={current}/>}
      {!projects.loading && selected && projects.data && !current && projects.data.length > 0 && <div className="infra-empty"><h2>Проект недоступен</h2><p>Выберите проект из списка.</p><button className="infra-button" onClick={() => select(null)}>Открыть первый проект</button></div>}
    </main></div>
    {creating && <CreationDialog kind="project" onClose={() => setCreating(false)} onSubmit={async values => { const project = await write<Project>('/api/project', values); select(project.id); projects.reload() }}/ >}
  </div>
}
function ProjectWorkspace({ project }: { project: Project }) {
  const [selected, setSelected] = useState<string | null>(() => new URLSearchParams(window.location.search).get('server'))
  const [creating, setCreating] = useState(false)
  const load = useCallback((signal: AbortSignal) => read<Server[]>(`/api/project/${project.id}/server`, signal), [project.id])
  const servers = useResource(load)
  const current = selected ? servers.data?.find(server => server.id === selected) : servers.data?.[0]
  function select(id: string | null) { setSelected(id); selection(project.id, id) }
  return <>
    <section className="infra-project-summary"><div><span className="eyebrow">ПРОЕКТ</span><h2>{project.name}</h2><p>{project.description}</p></div><span className="infra-count">{servers.data?.length ?? '—'}<small>серверов</small></span></section>
    <div className="infra-section-heading"><h2>Серверы</h2><div className="infra-actions"><button className="infra-button subtle" disabled={servers.loading} onClick={servers.reload}>Обновить</button><button className="infra-button" onClick={() => setCreating(true)}>+ Добавить сервер</button></div></div>
    {servers.error && <div className="message error" role="alert">{servers.error}<button className="infra-link" onClick={servers.reload}>Повторить</button></div>}
    {servers.loading ? <p role="status" className="infra-wait"><span className="spinner"/> Загружаем серверы…</p> : servers.data && <>
      {servers.data.length === 0 ? <div className="infra-empty compact"><h3>В проекте пока нет серверов</h3><p>Добавьте Linux-сервер, чтобы настроить подключение и собрать информацию.</p><button className="infra-button accent" onClick={() => setCreating(true)}>Добавить сервер</button></div> : <div className="infra-server-list">{servers.data.map(server => <button key={server.id} className={current?.id === server.id ? 'selected' : ''} aria-pressed={current?.id === server.id} onClick={() => select(server.id)}><span className="infra-server-glyph" aria-hidden="true">▤</span><strong>{server.name}</strong><span>{server.ipAddress}</span><small>{server.os}</small></button>)}</div>}
      {current && <ServerWorkspace key={current.id} projectId={project.id} server={current}/>}
      {selected && !current && servers.data.length > 0 && <div className="infra-empty compact"><h3>Сервер недоступен</h3><button className="infra-button" onClick={() => select(null)}>Выбрать первый сервер</button></div>}
    </>}
    {creating && <CreationDialog kind="server" onClose={() => setCreating(false)} onSubmit={async values => { const server = await write<Server>(`/api/project/${project.id}/server`, values); select(server.id); servers.reload() }}/ >}
  </>
}
function ServerWorkspace({ projectId, server }: { projectId: string; server: Server }) {
  const base = serverPath(projectId, server.id)
  const load = useCallback(async (signal: AbortSignal) => {
    const [ssh, job, services] = await Promise.all([optional<SshSettings>(base + '/ssh', signal), optional<DiscoveryJob>(base + '/discovery', signal), read<ManagedService[]>(base + '/service', signal)])
    return { ssh, job, services }
  }, [base])
  const initial = useResource(load)
  if (initial.loading) return <p role="status" className="infra-wait"><span className="spinner"/> Загружаем данные сервера…</p>
  if (initial.error || !initial.data) return <div className="message error" role="alert">{initial.error}<button className="infra-link" onClick={initial.reload}>Повторить</button></div>
  return <ServerDetails base={base} server={server} initial={initial.data}/>
}
