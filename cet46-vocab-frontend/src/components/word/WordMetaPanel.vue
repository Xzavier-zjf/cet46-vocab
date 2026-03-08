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
            <p v-else-if="displaySmartExplain" class="line explain-text">{{ displaySmartExplain }}</p>
            <p v-else-if="explainStatus === 'pending'" class="empty">智能解释生成中，请稍后</p>
            <p v-else class="empty">暂无智能解释，可点击“重试AI生成”或切换本地模型</p>
          </div>
        </transition>
      </el-tab-pane>

      <el-tab-pane label="语法用法" name="grammar">
        <transition name="fade">
          <div :key="`${genStatus}-grammar`" class="tab-content">
            <LlmSkeleton v-if="showGrammarLoading" />
            <p v-else-if="displayGrammarUsage" class="line explain-text">{{ displayGrammarUsage }}</p>
            <p v-else-if="explainStatus === 'pending'" class="empty">语法用法生成中，请稍后</p>
            <p v-else class="empty">暂无语法用法，可点击“重试AI生成”</p>
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
const displaySmartExplain = computed(() => formatSmartExplain(smartExplain.value))
const displayGrammarUsage = computed(() => {
  const direct = (props.llmContent?.grammarUsage || '').trim()
  if (direct) return direct
  return extractPrefixedLine(displaySmartExplain.value, '语法用法：')
})
const explainStatus = computed(() => props.llmContent?.explainStatus || 'pending')
const hasSentence = computed(() => !!(sentenceEn.value || sentenceZh.value))
const hasSynonyms = computed(() => synonyms.value.length > 0)
const hasMnemonic = computed(() => !!(mnemonic.value || rootAnalysis.value))
const showExplainLoading = computed(() => explainStatus.value === 'pending' && !displaySmartExplain.value)
const showGrammarLoading = computed(() => explainStatus.value === 'pending' && !displayGrammarUsage.value)
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
  if (tab === 'grammar' && !displayGrammarUsage.value) {
    emit('need-generate', { section: 'grammar' })
  }
})

function formatSmartExplain(raw) {
  const text = (raw || '').trim()
  if (!text) return ''

  const parsed = tryParseExplainJson(text)
  if (!parsed) {
    if (!text.startsWith('{')) return text
    const fallback = parseJsonLikeFallback(text)
    return fallback || text
  }

  const lines = []
  if (parsed.word) lines.push(`词条：${parsed.word}`)
  if (Array.isArray(parsed.core_meanings) && parsed.core_meanings.length > 0) {
    const meanings = parsed.core_meanings
      .map((item) => item?.cn_explanation || item?.sense || '')
      .filter(Boolean)
      .slice(0, 3)
    if (meanings.length) lines.push(`核心义项：${meanings.join('；')}`)
  }
  if (parsed.exam_usage?.note) lines.push(`考试用法：${parsed.exam_usage.note}`)
  if (parsed.memory_tip) lines.push(`记忆提示：${parsed.memory_tip}`)
  const grammarUsage = buildGrammarUsage(parsed.grammar_usage)
  if (grammarUsage) lines.push(`语法用法：${grammarUsage}`)
  if (Array.isArray(parsed.confusables) && parsed.confusables.length > 0) {
    const first = parsed.confusables[0] || {}
    if (first.word || first.difference) {
      lines.push(`易混词：${first.word || ''}${first.difference ? `：${first.difference}` : ''}`)
    }
  }
  return lines.length ? lines.join('\n') : text
}

function tryParseExplainJson(text) {
  try {
    const obj = JSON.parse(text)
    return obj && typeof obj === 'object' ? obj : null
  } catch {
    return null
  }
}

function parseJsonLikeFallback(text) {
  const lines = []
  const word = pickOne(text, /"word"\s*:\s*"([^"]+)"/)
  if (word) lines.push(`词条：${word}`)

  const meaningMatches = [...text.matchAll(/"cn_explanation"\s*:\s*"([^"]+)"/g)]
  const meanings = meaningMatches.map((m) => m[1]).filter(Boolean).slice(0, 3)
  if (meanings.length) lines.push(`核心义项：${meanings.join('；')}`)

  const examNote = pickOne(text, /"exam_usage"\s*:\s*\{[\s\S]*?"note"\s*:\s*"([^"]+)"/)
  if (examNote) lines.push(`考试用法：${examNote}`)

  const memoryTip = pickOne(text, /"memory_tip"\s*:\s*"([^"]+)"/)
  if (memoryTip) lines.push(`记忆提示：${memoryTip}`)

  const countability = pickOne(text, /"grammar_usage"\s*:\s*\{[\s\S]*?"countability"\s*:\s*"([^"]+)"/)
  const usageTip = pickOne(text, /"grammar_usage"\s*:\s*\{[\s\S]*?"usage_tip"\s*:\s*"([^"]+)"/)
  const patterns = [...text.matchAll(/"verb_patterns"\s*:\s*\[([\s\S]*?)\]/g)]
  const structures = [...text.matchAll(/"common_structures"\s*:\s*\[([\s\S]*?)\]/g)]
  const grammarParts = []
  if (countability) grammarParts.push(countability)
  if (patterns.length) grammarParts.push(`动词搭配: ${pickArrayValues(patterns[0][1]).slice(0, 2).join(' / ')}`)
  if (structures.length) grammarParts.push(`常见结构: ${pickArrayValues(structures[0][1]).slice(0, 2).join(' / ')}`)
  if (usageTip) grammarParts.push(usageTip)
  if (grammarParts.length) lines.push(`语法用法：${grammarParts.join('；')}`)

  const confuseWord = pickOne(text, /"confusables"\s*:\s*\[[\s\S]*?"word"\s*:\s*"([^"]+)"/)
  const confuseDiff = pickOne(text, /"confusables"\s*:\s*\[[\s\S]*?"difference"\s*:\s*"([^"]+)"/)
  if (confuseWord || confuseDiff) {
    lines.push(`易混词：${confuseWord || ''}${confuseDiff ? `：${confuseDiff}` : ''}`)
  }

  return lines.join('\n')
}

function pickOne(text, regex) {
  const match = text.match(regex)
  return match && match[1] ? match[1] : ''
}

function extractPrefixedLine(text, prefix) {
  if (!text) return ''
  const line = text.split('\n').find((item) => item.startsWith(prefix))
  return line ? line.replace(prefix, '').trim() : ''
}

function buildGrammarUsage(grammar) {
  if (!grammar || typeof grammar !== 'object') return ''
  const parts = []
  if (grammar.countability) parts.push(grammar.countability)
  if (Array.isArray(grammar.verb_patterns) && grammar.verb_patterns.length) {
    parts.push(`动词搭配: ${grammar.verb_patterns.slice(0, 2).join(' / ')}`)
  }
  if (Array.isArray(grammar.common_structures) && grammar.common_structures.length) {
    parts.push(`常见结构: ${grammar.common_structures.slice(0, 2).join(' / ')}`)
  }
  if (grammar.usage_tip) parts.push(grammar.usage_tip)
  return parts.join('；')
}

function pickArrayValues(rawArrayText) {
  if (!rawArrayText) return []
  return [...rawArrayText.matchAll(/"([^"]+)"/g)].map((m) => m[1]).filter(Boolean)
}
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

.explain-text {
  white-space: pre-line;
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
