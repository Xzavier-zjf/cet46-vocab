export const WORD_TYPES = Object.freeze({
  CET4: 'cet4',
  CET4_LX: 'cet4lx',
  CET6: 'cet6',
  CET6_LX: 'cet6lx'
})

export const WORD_TYPE_OPTIONS_ZH = Object.freeze([
  { label: '四级正序', value: WORD_TYPES.CET4 },
  { label: '四级乱序', value: WORD_TYPES.CET4_LX },
  { label: '六级正序', value: WORD_TYPES.CET6 },
  { label: '六级乱序', value: WORD_TYPES.CET6_LX }
])

export const WORD_TYPE_VALUES = Object.freeze(WORD_TYPE_OPTIONS_ZH.map((item) => item.value))

export const normalizeWordType = (value, fallback = WORD_TYPES.CET4) => {
  const normalized = String(value || '').trim().toLowerCase()
  return WORD_TYPE_VALUES.includes(normalized) ? normalized : fallback
}
