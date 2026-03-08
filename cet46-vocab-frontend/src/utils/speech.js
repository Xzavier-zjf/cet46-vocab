const LANG_BY_ACCENT = {
  uk: 'en-GB',
  us: 'en-US'
}

export function canSpeak() {
  return typeof window !== 'undefined'
    && 'speechSynthesis' in window
    && typeof window.SpeechSynthesisUtterance !== 'undefined'
}

function pickVoice(lang) {
  const voices = window.speechSynthesis.getVoices() || []
  return voices.find((v) => v.lang === lang)
    || voices.find((v) => String(v.lang || '').toLowerCase().startsWith(lang.toLowerCase().slice(0, 2)))
    || null
}

export function speakWord(text, accent = 'us') {
  const word = String(text || '').trim()
  if (!word) return { ok: false, reason: 'empty' }
  if (!canSpeak()) return { ok: false, reason: 'unsupported' }

  const lang = LANG_BY_ACCENT[accent] || LANG_BY_ACCENT.us
  const utterance = new window.SpeechSynthesisUtterance(word)
  utterance.lang = lang
  utterance.rate = 0.95
  utterance.pitch = 1
  const voice = pickVoice(lang)
  if (voice) {
    utterance.voice = voice
  }

  window.speechSynthesis.cancel()
  window.speechSynthesis.speak(utterance)
  return { ok: true }
}
