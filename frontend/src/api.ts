export interface User {
  id: string
  name: string
  email: string
  role: 'ADMIN' | 'ENGINEER'
}

export class ApiError extends Error {
  readonly status: number
  constructor(message: string, status: number) { super(message); this.status = status }
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  let response: Response
  try {
    response = await fetch(`/api/auth${path}`, { ...init, credentials: 'same-origin' })
  } catch {
    throw new ApiError('Нет соединения с сервером. Проверьте подключение и повторите попытку.', 0)
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null
    throw new ApiError(body?.message || (response.status >= 500
      ? 'Сервер временно недоступен. Попробуйте позже.'
      : response.status === 403 ? 'Сессия формы истекла. Повторите попытку.'
      : 'Не удалось выполнить запрос.'), response.status)
  }
  return response
}

async function mutate(path: string, body?: BodyInit, contentType?: string) {
  // Obtain a fresh token: Spring rotates it after login and logout.
  const response = await request('/csrf')
  const csrf = await response.json() as { headerName: string; token: string }
  return request(path, {
    method: 'POST',
    headers: { [csrf.headerName]: csrf.token, ...(contentType ? { 'Content-Type': contentType } : {}) },
    body,
  })
}

export async function currentUser(): Promise<User | null> {
  try { return await (await request('/me')).json() as User }
  catch (error) { if (error instanceof ApiError && error.status === 401) return null; throw error }
}
export async function login(email: string, password: string) {
  try {
    await mutate('/login', new URLSearchParams({ email, password }), 'application/x-www-form-urlencoded')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) throw new ApiError('Неверный email или пароль, либо аккаунт отключён.', 401)
    throw error
  }
  const user = await currentUser()
  if (!user) throw new ApiError('Не удалось сохранить сессию. Попробуйте войти снова.', 401)
  return user
}
export async function register(name: string, email: string, password: string) {
  await mutate('/register', JSON.stringify({ name, email, password }), 'application/json')
}
export async function logout() { await mutate('/logout') }
