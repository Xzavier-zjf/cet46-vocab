<template>
  <section class="meta-panel">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="例句" name="sentence">
        <transition name="fade">
          <div :key="`${genStatus}-sentence`" class="tab-content">
            <LlmSkeleton v-if="genStatus === 'pending' && !pollStalled && !hasSentence" />

            <template v-else-if="hasSentence">
              <p v-if="sentenceEn" class="line">{{ sentenceEn }}</p>
              <p v-if="sentenceZh" class="line zh">{{ sentenceZh }}</p>
            </template>

            <p v-else-if="genStatus === 'pending' && pollStalled" class="empty">AI仍在生成中，请稍后刷新</p>
            <p v-else-if="genStatus === 'fallback'" class="empty">暂无可用结果，可点击“重试AI生成”</p>
            <p v-else class="empty">暂无AI内容</p>
          </div>
        </transition>
      </el-tab-pane>

      <el-tab-pane label="近义词" name="synonym">
        <transition name="fade">
          <div :key="`${genStatus}-synonym`" class="tab-content">
            <LlmSkeleton v-if="genStatus === 'pending' && !pollStalled && !hasSynonyms" />

            <template v-else-if="hasSynonyms">
              <div v-for="(item, idx) in synonyms" :key="idx" class="syn-item">
                <p class="line"><strong>{{ item.synonym }}</strong></p>
                <p v-if="item.difference" class="line">{{ item.difference }}</p>
                <p v-if="item.example" class="line ex">{{ item.example }}</p>
              </div>
            </template>

            <p v-else-if="genStatus === 'pending' && pollStalled" class="empty">AI仍在生成中，请稍后刷新</p>
            <p v-else-if="hasAnyContent" class="empty">近义词仍在补充中，可点击“重试AI生成”</p>
            <p v-else-if="genStatus === 'fallback'" class="empty">暂无AI内容，仅显示基础释义</p>
            <p v-else class="empty">暂无AI内容</p>
          </div>
        </transition>
      </el-tab-pane>

      <el-tab-pane label="助记" name="mnemonic">
        <transition name="fade">
          <div :key="`${genStatus}-mnemonic`" class="tab-content">
            <LlmSkeleton v-if="genStatus === 'pending' && !pollStalled && !hasMnemonic" />

            <template v-else-if="hasMnemonic">
              <p v-if="mnemonic" class="line">{{ mnemonic }}</p>
              <p v-if="rootAnalysis" class="line ex">{{ rootAnalysis }}</p>
            </template>

            <p v-else-if="genStatus === 'pending' && pollStalled" class="empty">AI仍在生成中，请稍后刷新</p>
            <p v-else-if="hasAnyContent" class="empty">助记仍在补充中，可点击“重试AI生成”</p>
            <p v-else-if="genStatus === 'fallback'" class="empty">暂无AI内容，仅显示基础释义</p>
            <p v-else class="empty">暂无AI内容</p>
          </div>
        </transition>
      </el-tab-pane>

      <el-tab-pane label="智能解释" name="explain">
        <transition name="fade">
          <div :key="`${genStatus}-explain`" class="tab-content">
            <LlmSkeleton v-if="showExplainLoading" />
            <p v-else-if="smartExplain" class="line">{{ smartExplain }}</p>
            <p v-else-if="explainStatus === 'pending'" class="empty">智能解释生成中，请稍后</p>
            <p v-else class="empty">暂无智能解释，可点击“重试AI生成”或切换本地模型</p>
          </div>
        </transition>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import LlmSkeleton from '@/components/word/LlmSkeleton.vue'

const props = defineProps({
  llmContent: { type: Object, default: () => ({}) },
  genStatus: { type: String, default: 'pending' },
  pollStalled: { type: Boolean, default: false }
})
const emit = defineEmits(['need-generate'])

const activeTab = ref('sentence')

const sentenceEn = computed(() => props.llmContent?.sentence?.sentenceEn || '')
const sentenceZh = computed(() => props.llmContent?.sentence?.sentenceZh || '')
const synonyms = computed(() => (Array.isArray(props.llmContent?.synonyms) ? props.llmContent.synonyms : []))
const mnemonic = computed(() => props.llmContent?.mnemonic?.mnemonic || '')
const rootAnalysis = computed(() => props.llmContent?.mnemonic?.rootAnalysis || '')
const smartExplain = computed(() => props.llmContent?.smartExplain || '')
const explainStatus = computed(() => props.llmContent?.explainStatus || 'pending')
const hasSentence = computed(() => !!(sentenceEn.value || sentenceZh.value))
const hasSynonyms = computed(() => synonyms.value.length > 0)
const hasMnemonic = computed(() => !!(mnemonic.value || rootAnalysis.value))
const showExplainLoading = computed(() => explainStatus.value === 'pending' && !smartExplain.value)
const hasAnyContent = computed(() => hasSentence.value || hasSynonyms.value || hasMnemonic.value)

watch(activeTab, (tab) => {
  if (tab === 'synonym' && !hasSynonyms.value) {
    emit('need-generate', { section: 'synonym' })
  }
  if (tab === 'mnemonic' && !hasMnemonic.value) {
    emit('need-generate', { section: 'mnemonic' })
  }
  if (tab === 'explain' && !smartExplain.value) {
    emit('need-generate', { section: 'explain' })
  }
})
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
