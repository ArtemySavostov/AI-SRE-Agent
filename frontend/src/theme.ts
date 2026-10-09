import { useSyncExternalStore } from 'react'

export type Theme = 'dark' | 'light'
const storageKey = 'sre:theme'
const changeEvent = 'sre:theme-change'

function savedTheme(): Theme {
  try {
    return localStorage.getItem(storageKey) === 'light' ? 'light' : 'dark'
  } catch {
    return 'dark'
  }
}

export function initializeTheme() {
  document.documentElement.dataset.theme = savedTheme()
}

export function setTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  try {
    localStorage.setItem(storageKey, theme)
  } catch {
    /* The theme still works when browser storage is disabled. */
  }
  window.dispatchEvent(new Event(changeEvent))
}

function subscribe(callback: () => void) {
  const onStorage = (event: StorageEvent) => {
    if (event.key !== storageKey && event.key !== null) return
    initializeTheme()
    callback()
  }
  window.addEventListener(changeEvent, callback)
  window.addEventListener('storage', onStorage)
  return () => {
    window.removeEventListener(changeEvent, callback)
    window.removeEventListener('storage', onStorage)
  }
}

export function useTheme(): Theme {
  return useSyncExternalStore(
    subscribe,
    () => (document.documentElement.dataset.theme === 'light' ? 'light' : 'dark'),
    () => 'dark',
  )
}
