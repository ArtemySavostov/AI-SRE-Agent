import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { activeJob, messageOf, read, write } from './api'
import type { ConnectionTest, DiscoveryJob, ManagedService, Server, SshSettings } from './api'
import { CreationDialog } from './Forms'
import DiscoveryView from './DiscoveryView'

const date = (value: string) => new Date(value).toLocaleString('ru-RU')
type Tab = 'docker' | 'ssh' | 'services'
type Draft = Omit<SshSettings, 'port'> & { port: string }
export default function ServerDetails({ base, server, initial }: { base: string; server: Server; initial: { ssh: SshSettings | null; job: DiscoveryJob | null; services: ManagedService[] } }) {
  const [tab, setTab] = useState<Tab>(initial.ssh ? 'docker' : 'ssh')
  const [ssh, setSsh] = useState(initial.ssh)
  const [draft, setDraft] = useState<Draft>(() => initial.ssh ? { ...initial.ssh, port: String(initial.ssh.port) } : { host: '', port: '22', username: '', credentialId: '', hostKeyFingerprint: '' })
  const [job, setJob] = useState(initial.job)
  const [services, setServices] = useState(initial.services)
  const [action, setAction] = useState<'save' | 'test' | 'discover' | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [test, setTest] = useState<ConnectionTest | null>(null)
  const [creating, setCreating] = useState(false)
  const [pollError, setPollError] = useState('')
  const [pollPaused, setPollPaused] = useState(false)
  const [pollRevision, setPollRevision] = useState(0)
  const running = activeJob(job)
  const settings: SshSettings = { host: draft.host.trim(), port: Number(draft.port), username: draft.username.trim(), credentialId: draft.credentialId.trim(), hostKeyFingerprint: draft.hostKeyFingerprint.trim() }
  const dirty = !ssh || (Object.keys(settings) as (keyof SshSettings)[]).some(key => settings[key] !== ssh[key])
  useEffect(() => {
    if (!running) return
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout>
    let failures = 0
    async function poll() {
      try {
        const next = await read<DiscoveryJob>(base + '/discovery', controller.signal)
        if (controller.signal.aborted) return
        setJob(next); setPollError(''); setPollPaused(false); failures = 0
        if (activeJob(next)) timer = setTimeout(poll, 2000)
      } catch (cause) {
        if (controller.signal.aborted) return
        setPollError(messageOf(cause)); failures++
        if (failures < 3) timer = setTimeout(poll, 4000)
        else setPollPaused(true)
      }
    }
    timer = setTimeout(poll, 1200)
    return () => { controller.abort(); clearTimeout(timer) }
  }, [base, running, pollRevision])
  function edit(key: keyof Draft, value: string) { setDraft(previous => ({ ...previous, [key]: value })); setTest(null); setNotice('') }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setAction('save'); setError(''); setNotice(''); setTest(null)
    try { const saved = await write<SshSettings>(base + '/ssh', settings, 'PUT'); setSsh(saved); setDraft({ ...saved, port: String(saved.port) }); setNotice('SSH-настройки сохранены. Теперь проверьте подключение.') }
    catch (cause) { setError(messageOf(cause)) }
    finally { setAction(null) }
  }
  async function testConnection() {
    setAction('test'); setError(''); setNotice(''); setTest(null)
    try { const result = await write<ConnectionTest>(base + '/ssh/test'); setTest(result) }
    catch (cause) { setError(messageOf(cause)) }
    finally { setAction(null) }
  }
  async function discover() {
    setAction('discover'); setError(''); setNotice(''); setPollError(''); setPollPaused(false)
    try { setJob(await write<DiscoveryJob>(base + '/discovery')); setTab('docker') }
    catch (cause) {
      setError(messageOf(cause))
      // Another client may already have started a job. Recover its state without retrying the POST.
      try { setJob(await read<DiscoveryJob>(base + '/discovery')) } catch { /* Keep the visible request error. */ }
    } finally { setAction(null) }
  }
  return <section className="infra-server-panel" aria-label={`Сервер ${server.name}`}>
    <div className="infra-detail-heading"><div><div className="eyebrow">ВЫБРАННЫЙ СЕРВЕР</div><h2>{server.name}</h2><p>{server.hostname} <span>·</span> {server.ipAddress} <span>·</span> {server.os}</p></div><button className="infra-button accent" disabled={!ssh || dirty || !!action || running} onClick={discover}>{running ? <><span className="spinner"/> Сбор выполняется</> : action === 'discover' ? 'Запускаем…' : 'Запустить discovery'}</button></div>
    {(!ssh || dirty) && <p className="infra-hint">Для запуска discovery {ssh ? 'сохраните изменения SSH-настроек' : 'настройте SSH-подключение'}.</p>}
    <nav className="infra-tabs" aria-label="Разделы сервера">{([['docker', 'Docker discovery'], ['ssh', 'SSH-подключение'], ['services', `Сервисы · ${services.length}`]] as const).map(([id, title]) => <button key={id} className={tab === id ? 'selected' : ''} aria-current={tab === id ? 'page' : undefined} onClick={() => setTab(id)}>{title}</button>)}</nav>
    <div className="infra-tab-body">
      {error && <p className="message error" role="alert">{error}</p>}
      {notice && <p className="message success" role="status">{notice}</p>}
      {pollError && <div className="message error" role="alert">Не удалось обновить состояние: {pollError}<p>{pollPaused ? 'Опрос приостановлен. Задача на сервере могла продолжить работу.' : 'Пробуем восстановить соединение…'}</p>{pollPaused && <button className="infra-button" onClick={() => { setPollPaused(false); setPollError(''); setPollRevision(value => value + 1) }}>Возобновить проверку</button>}</div>}
      {tab === 'ssh' && <div className="infra-ssh-layout"><form onSubmit={save} className="infra-form"><h3>Параметры подключения</h3><p className="infra-muted">Адрес должен быть доступен с backend. Для demo через Docker Desktop используйте host.docker.internal и порт 2522.</p><fieldset disabled={!!action || running}>
        <div className="infra-form-grid"><label>Адрес SSH<input name="host" value={draft.host} onChange={e => edit('host', e.target.value)} maxLength={255} pattern="[a-zA-Z0-9._:%\-]+" placeholder="host.docker.internal" required/></label><label>Порт<input name="port" type="number" value={draft.port} onChange={e => edit('port', e.target.value)} min={1} max={65535} step={1} required/></label></div>
        <div className="infra-form-grid"><label>Пользователь<input name="username" value={draft.username} onChange={e => edit('username', e.target.value)} maxLength={64} pattern="[a-zA-Z_][a-zA-Z0-9_.\-]*" placeholder="git" autoComplete="off" required/></label><label>Идентификатор ключа<input name="credentialId" value={draft.credentialId} onChange={e => edit('credentialId', e.target.value)} maxLength={64} pattern="[a-zA-Z0-9][a-zA-Z0-9_\-]*" placeholder="demo_ed25519" required/></label></div>
        <label>SHA256-отпечаток сервера<input className="infra-mono" name="hostKeyFingerprint" value={draft.hostKeyFingerprint} onChange={e => edit('hostKeyFingerprint', e.target.value)} maxLength={51} pattern="SHA256:[A-Za-z0-9+\/]{43}=?" placeholder="SHA256:…" required/><small>Укажите проверенный отпечаток ключа сервера, а не клиентского ключа.</small></label>
        <div className="infra-actions"><button className="infra-button accent" type="submit" disabled={!dirty}>{action === 'save' ? 'Сохраняем…' : 'Сохранить настройки'}</button><button className="infra-button" type="button" disabled={!ssh || dirty} onClick={testConnection}>{action === 'test' ? 'Проверяем…' : 'Проверить SSH'}</button></div>
      </fieldset>{test && <p className={`message ${test.connected ? 'success' : 'error'}`} role="status">{test.connected ? `SSH доступен · ${test.hostname}` : 'Соединение не подтверждено'}<br/><small>Проверено: {date(test.checkedAt)}. Доступ к Docker проверяется при discovery.</small></p>}</form><aside className="infra-help"><span className="eyebrow">ПОДКЛЮЧЕНИЕ ПО КЛЮЧУ</span><h3>Ключ хранится на backend</h3><p>Введите идентификатор ключа без пути и расширения из настроенного каталога backend. Загружать приватный ключ или вводить SSH-пароль здесь не нужно.</p><ol><li>Сохраните параметры.</li><li>Проверьте SSH-доступ.</li><li>Запустите Docker discovery.</li></ol><p>Сбор читает информацию о сервере и контейнерах. Действующие сервисы не изменяются.</p></aside></div>}
      {tab === 'docker' && <DiscoveryView job={job} configured={!!ssh} onConfigure={() => setTab('ssh')}/>}
      {tab === 'services' && <><div className="infra-section-heading"><div><h3>Сервисы сервера</h3><p className="infra-muted">Сервисы, добавленные вручную. Обнаруженные контейнеры доступны во вкладке Docker discovery.</p></div><button className="infra-button" onClick={() => setCreating(true)}>+ Добавить сервис</button></div>{services.length ? <div className="infra-service-list">{services.map(service => <article key={service.id}><div><h3>{service.name}</h3><span className={'infra-badge ' + (service.status === 'UP' ? 'good' : service.status === 'DOWN' ? 'bad' : 'neutral')}>{service.status === 'UNKNOWN' ? 'Состояние неизвестно' : service.status}</span></div>{service.description && <p>{service.description}</p>}<small>Healthcheck: <span className="infra-mono">{service.healthcheckUrl || 'не задан'}</span></small></article>)}</div> : <div className="infra-empty compact"><h3>Сервисы пока не добавлены</h3><p>Добавьте приложение, API или другую службу этого сервера.</p></div>}</>}
    </div>
    {creating && <CreationDialog kind="service" onClose={() => setCreating(false)} onSubmit={async values => { const service = await write<ManagedService>(base + '/service', values); setServices(previous => [service, ...previous]) }}/ >}
  </section>
}
