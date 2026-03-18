<template>
  <div class="flip-wrap" @click="$emit('flip')">
    <div class="flip-inner" :class="{ flipped: isFlipped }">
      <section class="card-face card-front">
        <div class="card-meta">
          <span class="meta-badge">第 {{ currentIndex + 1 }} / {{ total }} 词</span>
          <span v-if="word?.pos" class="meta-badge soft">{{ word.pos }}</span>
        </div>
        <div class="word-row">
          <h2 class="word">{{ word?.english || '-' }}</h2>
          <div class="speak-actions">
            <button class="speak-btn" @click.stop="handleSpeak('uk')">🔊 英</button>
            <button class="speak-btn" @click.stop="handleSpeak('us')">🔊 美</button>
          </div>
        </div>
        <p class="phonetic">{{ word?.phonetic || '' }}</p>
        <p class="hint">点击翻转查看释义</p>
      </section>

      <section class="card-face card-back">
        <div class="card-meta">
          <span class="meta-badge">释义面</span>
          <button class="meta-btn" @click.stop="$emit('flip')">点击切回</button>
        </div>
        <div class="section-label">中文释义</div>
        <p class="chinese">{{ word?.chinese || '-' }}</p>
        <div v-if="exampleSentence" class="section-label">例句</div>
        <p v-if="exampleSentence" class="example" v-html="highlightedSentenceEn" />
        <div v-if="exampleSentenceZh" class="section-label">中文例句</div>
        <p v-if="exampleSentenceZh" class="example zh" v-html="highlightedSentenceZh" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { speakWord } from '@/utils/speech'

defineEmits(['flip'])

const props = defineProps({
  word: { type: Object, default: () => ({}) },
  isFlipped: { type: Boolean, default: false },
  currentIndex: { type: Number, default: 0 },
  total: { type: Number, default: 0 }
})

const exampleSentence = computed(() => {
  return (
    props.word?.sentenceEn ||
    props.word?.llmContent?.sentence?.sentenceEn ||
    ''
  )
})

const exampleSentenceZh = computed(() => {
  return props.word?.sentenceZh || props.word?.llmContent?.sentence?.sentenceZh || ''
})

const escapedWord = computed(() => escapeRegExp(props.word?.english || ''))

const primaryZhKeyword = computed(() => {
  const chinese = String(props.word?.chinese || '')
  const chunks = chinese
    .split(/[；;，,、\s()（）【】\[\]\/]/)
    .map((part) => part.trim())
    .filter((part) => part.length >= 2)
  return chunks[0] || ''
})

const highlightedSentenceEn = computed(() => {
  return highlightSafe(exampleSentence.value, escapedWord.value, 'gi')
})

const highlightedSentenceZh = computed(() => {
  const keyword = escapeRegExp(primaryZhKeyword.value)
  if (!keyword) return escapeHtml(exampleSentenceZh.value)
  const highlighted = highlightSafe(exampleSentenceZh.value, keyword, 'g')
  if (highlighted.includes('word-focus')) return highlighted
  return escapeHtml(exampleSentenceZh.value)
})

const handleSpeak = (accent) => {
  const result = speakWord(props.word?.english, accent)
  if (result.ok) return
  if (result.reason === 'unsupported') {
    ElMessage.warning('当前浏览器不支持语音播放')
  }
}

function highlightSafe(text, pattern, flags = 'g') {
  const safe = escapeHtml(text || '')
  if (!pattern) return safe
  const regex = new RegExp(`(${pattern})`, flags)
  return safe.replace(regex, '<mark class="word-focus">$1</mark>')
}

function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeRegExp(text) {
  return String(text || '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
</script>

<style scoped>
.flip-wrap {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
  perspective: 1200px;
  cursor: pointer;
}

.flip-inner {
  position: relative;
  width: 100%;
  min-height: 320px;
  transform-style: preserve-3d;
  transition: transform 0.4s ease;
}

.flip-inner.flipped {
  transform: rotateY(180deg);
}

.card-face {
  position: absolute;
  inset: 0;
  padding: 42px 48px;
  border-radius: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-soft);
  box-shadow: var(--shadow-card);
  backface-visibility: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.card-meta {
  position: absolute;
  top: 16px;
  left: 18px;
  right: 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.meta-badge {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1;
  background: var(--color-surface-soft);
  color: var(--color-muted-strong);
  border: 1px solid var(--color-border-soft);
}

.meta-badge.soft {
  color: var(--color-muted);
}

.meta-btn {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1;
  background: var(--color-surface-soft);
  color: var(--color-muted);
  border: 1px solid var(--color-border-soft);
  cursor: pointer;
}

.meta-btn:hover {
  color: var(--color-primary-strong);
  border-color: var(--color-warning);
}

.card-front {
  align-items: center;
  text-align: center;
}

.word-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.word {
  margin: 0;
  color: var(--color-primary-strong);
  font-size: 32px;
  line-height: 1.2;
  font-weight: 700;
}

.speak-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.speak-btn {
  border: 1px solid var(--color-border-soft);
  background: var(--color-surface-soft);
  border-radius: 14px;
  color: var(--color-muted-strong);
  font-size: 12px;
  padding: 3px 8px;
  cursor: pointer;
}

.phonetic {
  margin: 14px 0 28px;
  color: var(--color-muted-soft);
  font-size: 16px;
}

.hint {
  margin: 0;
  color: var(--color-muted-soft);
  font-size: 14px;
}

.card-back {
  transform: rotateY(180deg);
}

.section-label {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.chinese {
  margin: 0 0 14px;
  color: var(--color-text);
  font-size: 18px;
  line-height: 1.7;
}

.example {
  margin: 0;
  color: var(--color-warning);
  font-size: 14px;
  font-style: italic;
  line-height: 1.8;
}

.example.zh {
  color: var(--color-text);
  font-style: normal;
}

:deep(.word-focus) {
  background: var(--color-warning-soft);
  color: var(--color-primary-strong);
  padding: 0 4px;
  border-radius: 4px;
  border: 1px solid var(--color-warning);
}

@media (max-width: 768px) {
  .flip-inner {
    min-height: 280px;
  }

  .card-face {
    padding: 28px 22px;
  }

  .card-meta {
    top: 10px;
    left: 12px;
    right: 12px;
  }

  .word {
    font-size: 28px;
  }
}
</style>
