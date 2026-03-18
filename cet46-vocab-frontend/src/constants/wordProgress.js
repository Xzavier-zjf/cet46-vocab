export const WORD_PROGRESS = {
  COMPLETED: 'COMPLETED',
  LEARNING: 'LEARNING',
  NOT_LEARNING: 'NOT_LEARNING'
}

export const WORD_PROGRESS_META = {
  [WORD_PROGRESS.COMPLETED]: {
    label: '已完成',
    tone: 'completed',
    icon: '✓'
  },
  [WORD_PROGRESS.LEARNING]: {
    label: '学习中',
    tone: 'learning',
    icon: '◔'
  },
  [WORD_PROGRESS.NOT_LEARNING]: {
    label: '待学习',
    tone: 'pending',
    icon: '○'
  }
}

export const normalizeProgressStatus = (item) => {
  const raw = String(item?.progressStatus || item?.status || '').trim().toUpperCase()
  if (raw === WORD_PROGRESS.COMPLETED || raw === WORD_PROGRESS.LEARNING || raw === WORD_PROGRESS.NOT_LEARNING) {
    return raw
  }
  return item?.isLearning ? WORD_PROGRESS.LEARNING : WORD_PROGRESS.NOT_LEARNING
}

export const getProgressMeta = (status) => {
  return WORD_PROGRESS_META[status] || WORD_PROGRESS_META[WORD_PROGRESS.NOT_LEARNING]
}
