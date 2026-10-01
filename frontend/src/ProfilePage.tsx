import { useEffect } from 'react'
import type { User } from './api'
import './ProfilePage.css'

interface ProfilePageProps {
  user: User
  busy: boolean
  error: string
  onSignOut: () => Promise<void>
}

export default function ProfilePage({ user, busy, error, onSignOut }: ProfilePageProps) {
  useEffect(() => {
    const previous = document.title
    document.title = 'Профиль — SRE Platform'
    return () => { document.title = previous }
  }, [])

  const role = user.role === 'ADMIN' ? 'Администратор' : 'Инженер'
  const initials = user.name.trim().split(/\s+/).slice(0, 2).map(part => part.charAt(0)).join('').toUpperCase()

  return <div className="app-shell profile-shell">
    <header className="topbar">
      <a className="brand" href="/profile"><span className="profile-brand-icon" aria-hidden="true">◇</span><span>SRE<span className="brand-light"> PLATFORM</span></span></a>
      <span className="topbar-caption">ЛИЧНОЕ ПРОСТРАНСТВО</span>
      <div className="profile-top-user"><span className="profile-mini-avatar" aria-hidden="true">{initials}</span><span>{user.name}</span></div>
    </header>
    <div className="profile-layout">
      <aside className="profile-sidebar">
        <div className="eyebrow">АККАУНТ</div>
        <nav aria-label="Личный кабинет"><a href="/infrastructure" className="profile-nav-link"><span aria-hidden="true">▤</span> Инфраструктура</a><a href="/profile" className="profile-nav-current" aria-current="page"><span aria-hidden="true">◎</span> Мой профиль</a></nav>
        <div className="profile-sidebar-note">SRE Platform<br/><span>Ваше рабочее пространство</span></div>
      </aside>
      <main className="profile-main">
        <div className="profile-breadcrumb">Аккаунт <span>/</span> <strong>Мой профиль</strong></div>
        <div className="profile-page-heading"><div><h1>Мой профиль</h1><p>Информация о вашем аккаунте и доступе к платформе.</p></div><span className="profile-status"><i/> Сессия активна</span></div>
        <section className="profile-identity" aria-label="Пользователь">
          <div className="profile-large-avatar" aria-hidden="true">{initials}</div>
          <div className="profile-identity-text"><h2>{user.name}</h2><p>{user.email}</p><span className="role-badge">{role}</span></div>
          <span className="profile-identity-decoration" aria-hidden="true">◎</span>
        </section>
        <div className="profile-panels">
          <section className="profile-panel" aria-labelledby="account-heading">
            <div className="profile-panel-heading"><span className="eyebrow">ОСНОВНОЕ</span><h2 id="account-heading">Данные аккаунта</h2></div>
            <dl className="profile-data"><div><dt>Имя</dt><dd>{user.name}</dd></div><div><dt>Email</dt><dd>{user.email}</dd></div><div><dt>Роль в системе</dt><dd>{role}</dd></div></dl>
          </section>
          <section className="profile-panel profile-session" aria-labelledby="session-heading">
            <div className="profile-panel-heading"><span className="eyebrow">ДОСТУП</span><h2 id="session-heading">Текущая сессия</h2></div>
            <p>Вы вошли в платформу. После выхода для доступа к профилю потребуется повторная авторизация.</p>
            {error && <div className="message error" role="alert">{error}</div>}
            <button type="button" className="secondary-button" onClick={onSignOut} disabled={busy}>{busy ? 'Выходим…' : 'Выйти из аккаунта'}<span aria-hidden="true"> ↗</span></button>
          </section>
        </div>
        <footer className="profile-footer">SRE PLATFORM <span>/</span> Интеллектуальное управление инфраструктурой</footer>
      </main>
    </div>
  </div>
}
