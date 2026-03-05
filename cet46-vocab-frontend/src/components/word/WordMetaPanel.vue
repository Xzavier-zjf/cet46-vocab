<template>
  <section class="meta-panel">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="例句" name="sentence">
        <transition name="fade">
          <div :key="`${genStatus}-sentence`" class="tab-content">
            <LlmSkeleton v-if="genStatus === 'pending'" />

            <template v-else-if="genStatus === 'full' || genStatus === 'partial'">
              <template v-if="sentenceEn || sentenceZh">
                <p v-if="sentenceEn" class="line">{{ sentenceEn }}</p>
                <p v-if="sentenceZh" class="line zh">{{ sentenceZh }}</p>
              </template>
              <p v-else class="empty">暂无AI内容</p>
            </template>

            <p v-else class="empty">暂无AI内容，仅显示基础释义</p>
          </div>
        </transition>
      </el-tab-pane>

      <el-tab-pane label="近义词" name="synonym">
        <transition name="fade">
          <div :key="`${genStatus}-synonym`" class="tab-content">
            <LlmSkeleton v-if="genStatus === 'pending'" />

            <template v-else-if="genStatus === 'full'">
              <template v-if="synonyms.length">
                <div v-for="(item, idx) in synonyms" :key="idx" class="syn-item">
                  <p class="line"><strong>{{ item.synonym }}</strong></p>
                  <p v-if="item.difference" class="line">{{ item.difference }}</p>
                  <p v-if="item.example" class="line ex">{{ item.example }}</p>
                </div>
              </template>
              <p v-else class="empty">暂无AI内容</p>
            </template>

            <p v-else-if="genStatus === 'partial'" class="empty">暂无AI内容</p>
            <p v-else class="empty">暂无AI内容，仅显示基础释义</p>
          </div>
        </transition>
      </el-tab-pane>

      <el-tab-pane label="助记" name="mnemonic">
        <transition name="fade">
          <div :key="`${genStatus}-mnemonic`" class="tab-content">
            <LlmSkeleton v-if="genStatus === 'pending'" />

            <template v-else-if="genStatus === 'full'">
              <template v-if="mnemonic || rootAnalysis">
                <p v-if="mnemonic" class="line">{{ mnemonic }}</p>
                <p v-if="rootAnalysis" class="line ex">{{ rootAnalysis }}</p>
              </template>
              <p v-else class="empty">暂无AI内容</p>
            </template>

            <p v-else-if="genStatus === 'partial'" class="empty">暂无AI内容</p>
            <p v-else class="empty">暂无AI内容，仅显示基础释义</p>
          </div>
        </transition>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import LlmSkeleton from '@/components/word/LlmSkeleton.vue'

const props = defineProps({
  llmContent: { type: Object, default: () => ({}) },
  genStatus: { type: String, default: 'pending' }
})

const activeTab = ref('sentence')

const sentenceEn = computed(() => props.llmContent?.sentence?.sentenceEn || '')
const sentenceZh = computed(() => props.llmContent?.sentence?.sentenceZh || '')
const synonyms = computed(() => (Array.isArray(props.llmContent?.synonyms) ? props.llmContent.synonyms : []))
const mnemonic = computed(() => props.llmContent?.mnemonic?.mnemonic || '')
const rootAnalysis = computed(() => props.llmContent?.mnemonic?.rootAnalysis || '')
</script>

<style scoped>
.meta-panel {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 8px 18px 12px;
}

.tab-content {
  min-height: 140px;
  padding: 8px 0;
}

.line {
  margin: 0 0 10px;
  color: #2C3E50;
  line-height: 1.7;
}

.line.zh {
  color: #4d5c70;
}

.line.ex {
  color: #4A6FA5;
  font-style: italic;
}

.syn-item {
  padding: 10px 0;
  border-bottom: 1px dashed #e9edf4;
}

.syn-item:last-child {
  border-bottom: 0;
}

.empty {
  margin: 8px 0 0;
  color: #8896A8;
}

.fade-enter-active {
  transition: opacity 0.5s;
}

.fade-enter-from {
  opacity: 0;
}
</style>
