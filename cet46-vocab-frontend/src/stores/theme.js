import { defineStore } from 'pinia'

const THEME_KEY = 'theme:mode:v1'

function getSystemPrefersDark() {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function resolveInitialTheme() {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    if (saved === 'dark' || saved === 'light') {
      return saved
    }
  } catch {
    // Ignore storage errors.
  }
  return getSystemPrefersDark() ? 'dark' : 'light'
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    mode: 'light',
    initialized: false
  }),
  getters: {
    isDark: (state) => state.mode === 'dark'
  },
  actions: {
    initTheme() {
      if (this.initialized) return
      this.mode = resolveInitialTheme()
      this.applyTheme()
      this.initialized = true
    },
    setTheme(mode) {
      if (mode !== 'light' && mode !== 'dark') return
      this.mode = mode
      this.applyTheme()
      try {
        localStorage.setItem(THEME_KEY, mode)
      } catch {
        // Ignore storage errors.
      }
    },
    toggleTheme() {
      this.setTheme(this.isDark ? 'light' : 'dark')
    },
    applyTheme() {
      if (typeof document === 'undefined') return
      const root = document.documentElement
      root.classList.toggle('dark', this.isDark)
      root.setAttribute('data-theme', this.mode)
    }
  }
})
