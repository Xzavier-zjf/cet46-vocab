<template>
  <div class="flip-wrap" @click="$emit('flip')">
    <div class="flip-inner" :class="{ flipped: isFlipped }">
      <section class="card-face card-front">
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
        <p class="chinese">{{ word?.chinese || '-' }}</p>
        <p v-if="exampleSentence" class="example">{{ exampleSentence }}</p>
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
  isFlipped: { type: Boolean, default: false }
})

const exampleSentence = computed(() => {
  return (
    props.word?.sentenceEn ||
    props.word?.llmContent?.sentence?.sentenceEn ||
    ''
  )
})

const handleSpeak = (accent) => {
  const result = speakWord(props.word?.english, accent)
  if (result.ok) return
  if (result.reason === 'unsupported') {
    ElMessage.warning('当前浏览器不支持语音播放')
  }
}
</script>

<style scoped>
.flip-wrap {
  width: 100%;
  max-width: 900px;
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

@media (max-width: 768px) {
  .flip-inner {
    min-height: 280px;
  }

  .card-face {
    padding: 28px 22px;
  }

  .word {
    font-size: 28px;
  }
}
</style>

