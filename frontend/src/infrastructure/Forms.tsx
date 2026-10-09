import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { messageOf } from './api'

export type CreationKind = 'project' | 'server' | 'service'
const titles: Record<CreationKind, string> = {
  project: 'Новый проект',
  server: 'Добавить сервер',
  service: 'Добавить сервис',
}
export function CreationDialog({
  kind,
  onClose,
  onSubmit,
}: {
  kind: CreationKind
  onClose: () => void
  onSubmit: (values: Record<string, string>) => Promise<void>
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    const node = dialog.current
    node?.showModal()
    node?.querySelector('input')?.focus()
    return () => node?.close()
  }, [])
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const fields = Object.fromEntries(
      [...new FormData(event.currentTarget)].map(([key, value]) => [key, String(value).trim()]),
    )
    if (
      !fields.name ||
      (kind === 'project' && !fields.description) ||
      (kind === 'server' && (!fields.hostname || !fields.ipAddress || !fields.os))
    ) {
      setError('Заполните обязательные поля.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await onSubmit(fields)
      onClose()
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setBusy(false)
    }
  }
  return (
    <dialog
      className="infra-dialog"
      ref={dialog}
      onCancel={(event) => {
        event.preventDefault()
        if (!busy) onClose()
      }}
      aria-labelledby="create-title"
    >
      <div className="infra-dialog-heading">
        <h2 id="create-title">{titles[kind]}</h2>
        <button
          type="button"
          className="infra-icon-button"
          onClick={onClose}
          disabled={busy}
          aria-label="Закрыть"
        >
          ×
        </button>
      </div>
      <p className="infra-muted">
        {kind === 'project'
          ? 'Объедините серверы и сервисы в одном пространстве.'
          : kind === 'server'
            ? 'SSH-подключение можно настроить после добавления сервера.'
            : 'Сервис будет добавлен к выбранному серверу.'}
      </p>
      {error && (
        <p
          className="message error"
          role="alert"
        >
          {error}
        </p>
      )}
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <label>
            Название
            <input
              name="name"
              maxLength={255}
              required
              placeholder={
                kind === 'project'
                  ? 'Например, Production'
                  : kind === 'server'
                    ? 'Например, Ubuntu demo'
                    : 'Например, Backend API'
              }
            />
          </label>
          {kind === 'server' ? (
            <>
              <label>
                Hostname
                <input
                  name="hostname"
                  maxLength={255}
                  required
                  placeholder="demo-server"
                />
              </label>
              <div className="infra-form-grid">
                <label>
                  IP-адрес
                  <input
                    name="ipAddress"
                    maxLength={64}
                    required
                    placeholder="192.168.1.10"
                  />
                </label>
                <label>
                  Операционная система
                  <input
                    name="os"
                    maxLength={100}
                    required
                    placeholder="Ubuntu"
                  />
                </label>
              </div>
            </>
          ) : (
            <label>
              Описание{kind === 'service' && ' · необязательно'}
              <textarea
                name="description"
                rows={3}
                required={kind === 'project'}
              />
            </label>
          )}
          {kind === 'service' && (
            <label>
              Healthcheck URL · необязательно
              <input
                name="healthcheckUrl"
                type="url"
                maxLength={255}
                placeholder="http://service:8080/health"
              />
            </label>
          )}
          <div className="infra-actions">
            <button
              type="button"
              className="infra-button"
              onClick={onClose}
            >
              Отмена
            </button>
            <button
              type="submit"
              className="infra-button accent"
            >
              {busy ? 'Сохраняем…' : 'Добавить'}
            </button>
          </div>
        </fieldset>
      </form>
    </dialog>
  )
}
