import { getToken } from '@/utils/token'

const LEARNED_WORDS_STORAGE_PREFIX = 'learned_words:'
const COMPLETED_WORDS_STORAGE_PREFIX = 'completed_words:'

const buildWordKey = (wordType, wordId) => `${String(wordType || '').toLowerCase()}:${Number(wordId)}`

const resolveScope = (userId) => userId || getToken() || 'anonymous'

const readSet = (prefix, userId) => {
  const key = `${prefix}${resolveScope(userId)}`
  const raw = localStorage.getItem(key)
  if (!raw) return new Set()
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return new Set()
    return new Set(parsed.filter((item) => typeof item === 'string' && item.includes(':')))
  } catch {
    localStorage.removeItem(key)
    return new Set()
  }
}

const writeSet = (prefix, userId, setValue) => {
  const key = `${prefix}${resolveScope(userId)}`
  localStorage.setItem(key, JSON.stringify(Array.from(setValue)))
}

const addWordToSet = (prefix, userId, wordType, wordId) => {
  const setValue = readSet(prefix, userId)
  setValue.add(buildWordKey(wordType, wordId))
  writeSet(prefix, userId, setValue)
}

export const getWordKey = buildWordKey

export const getLearnedWordSet = (userId) => readSet(LEARNED_WORDS_STORAGE_PREFIX, userId)

export const getCompletedWordSet = (userId) => readSet(COMPLETED_WORDS_STORAGE_PREFIX, userId)

export const markWordLearned = (userId, wordType, wordId) => {
  addWordToSet(LEARNED_WORDS_STORAGE_PREFIX, userId, wordType, wordId)
}

export const markWordCompleted = (userId, wordType, wordId) => {
  addWordToSet(COMPLETED_WORDS_STORAGE_PREFIX, userId, wordType, wordId)
}
