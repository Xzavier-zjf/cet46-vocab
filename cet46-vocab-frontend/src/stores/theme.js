import { defineStore } from 'pinia'

const THEME_KEY = 'theme:mode:v1'
const USER_THEME_KEY_PREFIX = 'theme:mode:user:v1:'

function getSystemPrefersDark() {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function normalizeIdentity(identity) {
  if (identity == null) return ''
  const value = String(identity).trim()
  return value || ''
}

function getUserThemeKey(identity) {
  const normalized = normalizeIdentity(identity)
  return normalized ? `${USER_THEME_KEY_PREFIX}${normalized}` : ''
}

function readStoredTheme(key) {
  if (!key) return ''
  try {
    const saved = localStorage.getItem(key)
    if (saved === 'dark' || saved === 'light') {
      return saved
    }
  } catch {
    // Ignore storage errors.
  }
  return ''
}

function resolveInitialTheme(identity) {
  const userThemeKey = getUserThemeKey(identity)
  const userTheme = readStoredTheme(userThemeKey)
  if (userTheme) return userTheme

  const globalTheme = readStoredTheme(THEME_KEY)
  if (globalTheme) return globalTheme

  return getSystemPrefersDark() ? 'dark' : 'light'
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    mode: 'light',
    initialized: false,
    activeIdentity: ''
  }),
  getters: {
    isDark: (state) => state.mode === 'dark'
  },
  actions: {
    initTheme(identity = '') {
      const normalizedIdentity = normalizeIdentity(identity)
      this.activeIdentity = normalizedIdentity
      this.mode = resolveInitialTheme(normalizedIdentity)
      this.applyTheme()
      this.initialized = true
    },
    setActiveIdentity(identity = '') {
      this.initTheme(identity)
    },
    setTheme(mode, identity = this.activeIdentity) {
      if (mode !== 'light' && mode !== 'dark') return
      const normalizedIdentity = normalizeIdentity(identity)
      this.activeIdentity = normalizedIdentity
      this.mode = mode
      this.applyTheme()
      try {
        localStorage.setItem(THEME_KEY, mode)
        const userThemeKey = getUserThemeKey(normalizedIdentity)
        if (userThemeKey) {
          localStorage.setItem(userThemeKey, mode)
        }
      } catch {
        // Ignore storage errors.
      }
    },
    toggleTheme() {
      this.setTheme(this.isDark ? 'light' : 'dark', this.activeIdentity)
    },
    applyTheme() {
      if (typeof document === 'undefined') return
      const root = document.documentElement
      root.classList.toggle('dark', this.isDark)
      root.setAttribute('data-theme', this.mode)
    }
  }
})
