import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { currentUser, login, logout, register } from './api'
import type { User } from './api'
import './App.css'

type Page = 'login' | 'register'
const initialPage = (): Page => window.location.pathname === '/register' ? 'register' : 'login'

function Mark() {
  return <span className="brand-mark" aria-hidden="true"><svg viewBox="0 0 32 32" fill="none"><path d="m16 3 12 7v12l-12 7L4 22V10Z" stroke="currentColor" strokeWidth="2"/><path d="m8 18 5-7 5 10 6-7" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/></svg></span>
}
function App() {
  const [page, setPage] = useState<Page>(initialPage)
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    let active = true
    currentUser().then(value => { if (active) setUser(value) })
      .catch(() => { if (active) setError('Не удалось связаться с сервером. Формы доступны, но для входа нужен работающий backend.') })
      .finally(() => { if (active) setLoading(false) })
    const onPop = () => { setPage(initialPage()); setError(''); setNotice(''); setVisible(false) }
    window.addEventListener('popstate', onPop)
    return () => { active = false; window.removeEventListener('popstate', onPop) }
  }, [])

  function navigate(next: Page) {
    if (busy) return
    window.history.pushState({}, '', `/${next}`)
    setPage(next); setError(''); setNotice(''); setVisible(false)
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    const email = String(values.get('email') || '').trim()
    const password = String(values.get('password') || '')
    const name = String(values.get('name') || '').trim()
    setError(''); setNotice('')
    if (!password.trim() || (page === 'register' && !name)) { setError('Заполните все поля.'); return }
    if (page === 'register' && password !== values.get('confirm')) { setError('Пароли не совпадают.'); return }
    if (new TextEncoder().encode(password).length > 72) { setError('Пароль слишком длинный: максимум 72 байта в UTF-8.'); return }
    setBusy(true)
    try {
      if (page === 'register') {
        await register(name, email, password)
        form.reset(); setPage('login'); setVisible(false)
        window.history.replaceState({}, '', '/login')
        setNotice('Аккаунт создан. Войдите с вашим email и паролем.')
      } else {
        const signedIn = await login(email, password)
        form.reset(); setUser(signedIn); window.history.replaceState({}, '', '/profile')
      }
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Не удалось выполнить запрос.') }
    finally { setBusy(false) }
  }
  async function signOut() {
    setBusy(true); setError('')
    try { await logout(); setUser(null); setPage('login'); setNotice('Вы вышли из аккаунта.'); window.history.replaceState({}, '', '/login') }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Не удалось выйти.') }
    finally { setBusy(false) }
  }

  return <div className="app-shell">
    <header className="topbar"><a className="brand" href={user ? '/profile' : '/login'}><Mark/><span>SRE<span className="brand-light"> PLATFORM</span></span></a><span className="topbar-caption">INFRASTRUCTURE INTELLIGENCE</span><span className="edition">WORKSPACE</span></header>
    <main className={`workspace ${user ? 'workspace-profile' : ''}`}>
      <aside className="story">
        <div className="eyebrow"><span className="tiny-square"/> ОТ СИГНАЛА К РЕШЕНИЮ</div>
        <h1>Инфраструктура.<br/>Под вашим<br/><span>контролем.</span></h1>
        <p className="story-copy">Единое пространство для мониторинга сервисов, расследования инцидентов и работы с AI-агентом.</p>
        <div className="signal-panel" aria-label="Этапы расследования инцидента">
          <div className="panel-heading"><span>ЦИКЛ РАССЛЕДОВАНИЯ</span><span className="panel-dots">···</span></div>
          <div className="signal-art" aria-hidden="true"><div className="grid-lines"/><svg viewBox="0 0 480 100" preserveAspectRatio="none"><path d="M0 66H80L90 57L102 70L118 60H174L187 25L200 84L218 46L236 61H296L311 53L323 66H375L390 59H480" fill="none" stroke="currentColor" strokeWidth="2"/></svg><span className="signal-label">SIGNAL → CONTEXT → ACTION</span></div>
          <div className="steps"><div><b>01</b><span>Обнаружение</span></div><div><b>02</b><span>Диагностика</span></div><div><b>03</b><span>Восстановление</span></div></div>
        </div>
        <div className="story-note"><span aria-hidden="true">◇</span><p>AI исследует. Вы принимаете решения.<br/><strong>Действия — в рамках ваших политик.</strong></p></div>
      </aside>
      <section className="access" aria-label={user ? 'Профиль пользователя' : 'Авторизация'}>
        {loading ? <div className="loading" role="status"><span className="spinner"/> Проверяем сессию…</div> : user ? <div className="auth-card profile-card">
          <div className="eyebrow">ВАШЕ РАБОЧЕЕ ПРОСТРАНСТВО</div><div className="avatar">{user.name.slice(0, 1).toUpperCase()}</div><h2>Здравствуйте, {user.name}</h2><p className="subtitle">Вы вошли в SRE Platform.</p>
          <dl className="profile-details"><div><dt>Имя</dt><dd>{user.name}</dd></div><div><dt>Email</dt><dd>{user.email}</dd></div><div><dt>Роль</dt><dd><span className="role-badge">{user.role === 'ADMIN' ? 'Администратор' : 'Инженер'}</span></dd></div></dl>
          {error && <div className="message error" role="alert">{error}</div>}
          <button className="secondary-button" onClick={signOut} disabled={busy}>{busy ? 'Выходим…' : 'Выйти из аккаунта'}</button>
        </div> : <div className="auth-card">
          <div className="card-topline"><span className="eyebrow">ДОСТУП К ПЛАТФОРМЕ</span><span className="small-lock" aria-hidden="true">▣</span></div>
          <h2>{page === 'login' ? 'С возвращением' : 'Создайте аккаунт'}</h2><p className="subtitle">{page === 'login' ? 'Войдите, чтобы продолжить работу.' : 'Ваш первый шаг к управлению инфраструктурой.'}</p>
          <nav className="auth-tabs" aria-label="Способ входа"><button type="button" className={page === 'login' ? 'selected' : ''} aria-current={page === 'login' ? 'page' : undefined} onClick={() => navigate('login')} disabled={busy}>Вход</button><button type="button" className={page === 'register' ? 'selected' : ''} aria-current={page === 'register' ? 'page' : undefined} onClick={() => navigate('register')} disabled={busy}>Регистрация</button></nav>
          {error && <div className="message error" role="alert">{error}</div>}{notice && <div className="message success" role="status">{notice}</div>}
          <form key={page} onSubmit={submit}>
            <fieldset disabled={busy}>
              {page === 'register' && <label htmlFor="name">Имя<input id="name" name="name" autoComplete="name" placeholder="Как к вам обращаться" maxLength={255} required/></label>}
              <label htmlFor="email">Email<input id="email" name="email" type="email" autoComplete="email" placeholder="you@company.com" maxLength={255} required/></label>
              <label htmlFor="password">Пароль<span className="password-field"><input id="password" name="password" type={visible ? 'text' : 'password'} autoComplete={page === 'login' ? 'current-password' : 'new-password'} placeholder={page === 'login' ? 'Введите пароль' : 'Придумайте пароль'} required/><button type="button" className="reveal" onClick={() => setVisible(!visible)} aria-label={visible ? 'Скрыть пароль' : 'Показать пароль'} aria-pressed={visible}>{visible ? 'Скрыть' : 'Показать'}</button></span></label>
              {page === 'register' && <label htmlFor="confirm">Повторите пароль<input id="confirm" name="confirm" type={visible ? 'text' : 'password'} autoComplete="new-password" placeholder="Введите пароль ещё раз" required/></label>}
              <button className="primary-button" type="submit">{busy ? <><span className="spinner"/> {page === 'login' ? 'Входим…' : 'Создаём аккаунт…'}</> : <>{page === 'login' ? 'Войти в платформу' : 'Создать аккаунт'}<span aria-hidden="true">→</span></>}</button>
            </fieldset>
          </form>
          <div className="card-footer">{page === 'login' ? 'Ещё нет аккаунта?' : 'Уже зарегистрированы?'} <button type="button" disabled={busy} onClick={() => navigate(page === 'login' ? 'register' : 'login')}>{page === 'login' ? 'Зарегистрироваться' : 'Войти'}</button></div>
        </div>}
        <p className="access-note">SRE PLATFORM <span>/</span> Единая точка доступа</p>
      </section>
    </main>
    <footer className="site-footer"><span>SRE Platform <span className="footer-divider">/</span> Интеллектуальное управление инфраструктурой</span><span>НАБЛЮДАЙТЕ. ИССЛЕДУЙТЕ. ДЕЙСТВУЙТЕ.</span></footer>
  </div>
}
export default App
