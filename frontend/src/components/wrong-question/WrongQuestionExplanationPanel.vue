<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { MagicStick, RefreshRight, WarningFilled } from '@element-plus/icons-vue'
import { generateWrongQuestionExplanation } from '../../api/wrong-question'
import type {
  RagDegradedReason,
  RagExplanationMode,
  WrongQuestionExplanation,
} from '../../types/wrong-question'

const props = defineProps<{
  questionId: number
}>()

const loading = ref(false)
const result = ref<WrongQuestionExplanation | null>(null)
const errorMessage = ref('')
const requestVersion = ref(0)

const modeLabel: Record<RagExplanationMode, string> = {
  RAG: '知识库增强',
  MODEL_ONLY: 'AI 通用讲解',
  REFERENCE_ANSWER: '参考答案',
}

const degradedReasonLabel: Record<RagDegradedReason, string> = {
  RAG_DISABLED: '知识库功能当前未启用',
  NO_RELEVANT_KNOWLEDGE: '知识库中暂未检索到相关内容',
  RETRIEVAL_UNAVAILABLE: '知识检索服务暂时不可用',
  AI_UNAVAILABLE: 'AI 服务暂时不可用',
}

const modeText = computed(() => (result.value ? modeLabel[result.value.mode] : ''))
const canRetry = computed(() => Boolean(result.value || errorMessage.value))

function reset() {
  requestVersion.value++
  loading.value = false
  result.value = null
  errorMessage.value = ''
}

async function generateExplanation() {
  if (loading.value) return

  const version = ++requestVersion.value
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await generateWrongQuestionExplanation(props.questionId)
    if (version === requestVersion.value) {
      result.value = response
    }
  } catch {
    if (version === requestVersion.value) {
      errorMessage.value = '讲解生成失败，请稍后重试。已有参考答案不受影响。'
    }
  } finally {
    if (version === requestVersion.value) {
      loading.value = false
    }
  }
}

watch(() => props.questionId, reset, { immediate: true })
</script>

<template>
  <section class="explanation-panel">
    <div class="panel-heading">
      <div>
        <p class="panel-eyebrow">AI REVIEW</p>
        <h3>错题深度讲解</h3>
      </div>
      <el-button
        type="primary"
        :icon="canRetry ? RefreshRight : MagicStick"
        :loading="loading"
        @click="generateExplanation"
      >
        {{ canRetry ? '重新生成' : '生成讲解' }}
      </el-button>
    </div>

    <p v-if="!result && !errorMessage" class="panel-intro">
      结合当前题目与知识库，整理核心知识点、常见遗漏和复习建议。
    </p>

    <div v-if="loading && !result" class="panel-loading" aria-live="polite">
      <span class="loading-dot" />
      正在检索知识并组织讲解，请稍候...
    </div>

    <div v-if="errorMessage" class="panel-error" role="alert">
      <el-icon><WarningFilled /></el-icon>
      <span>{{ errorMessage }}</span>
    </div>

    <div v-if="result" class="explanation-content" aria-live="polite">
      <div class="mode-row">
        <span class="mode-badge" :class="`mode-badge--${result.mode.toLowerCase()}`">
          {{ modeText }}
        </span>
        <span v-if="result.ragApplied" class="mode-note">已引用项目知识库</span>
      </div>

      <p class="summary">{{ result.summary }}</p>

      <div v-if="result.keyPoints.length" class="content-block">
        <h4>核心知识点</h4>
        <ul>
          <li v-for="point in result.keyPoints" :key="point">{{ point }}</li>
        </ul>
      </div>

      <div v-if="result.commonMistakes.length" class="content-block content-block--warning">
        <h4>常见遗漏</h4>
        <ul>
          <li v-for="mistake in result.commonMistakes" :key="mistake">{{ mistake }}</li>
        </ul>
      </div>

      <div v-if="result.reviewAdvice" class="advice-block">
        <span>复习建议</span>
        <p>{{ result.reviewAdvice }}</p>
      </div>

      <div v-if="result.degradedReasons.length" class="degraded-block">
        <p v-for="reason in result.degradedReasons" :key="reason">
          {{ degradedReasonLabel[reason] }}，本次结果已自动降级处理。
        </p>
      </div>

      <div v-if="result.citations.length" class="citation-block">
        <h4>知识来源</h4>
        <article v-for="citation in result.citations" :key="citation.chunkId" class="citation-item">
          <div class="citation-title">
            <span>{{ citation.index }}</span>
            <strong>{{ citation.documentTitle }}</strong>
          </div>
          <p v-if="citation.headingPath" class="citation-path">{{ citation.headingPath }}</p>
          <p class="citation-excerpt">{{ citation.excerpt }}</p>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.explanation-panel {
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid var(--zm-border);
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.panel-eyebrow {
  margin-bottom: 5px;
  color: var(--zm-accent);
  font-family: var(--zm-font-mono);
  font-size: 11px;
}

.panel-heading h3 {
  font-size: 18px;
  line-height: 1.4;
}

.panel-intro,
.panel-loading {
  margin-top: 14px;
  color: var(--zm-ink-soft);
  font-size: 13px;
  line-height: 1.7;
}

.panel-loading {
  display: flex;
  align-items: center;
  gap: 9px;
}

.loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--zm-accent);
  animation: pulse 1.2s ease-in-out infinite;
}

.panel-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid rgba(255, 59, 48, 0.22);
  border-radius: var(--zm-radius-sm);
  color: var(--zm-red);
  background: rgba(255, 59, 48, 0.06);
  font-size: 13px;
  line-height: 1.6;
}

.explanation-content {
  margin-top: 18px;
}

.mode-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.mode-badge {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 3px 9px;
  border: 1px solid rgba(0, 122, 255, 0.22);
  border-radius: 6px;
  color: var(--zm-accent);
  background: rgba(0, 122, 255, 0.07);
  font-size: 12px;
  font-weight: 600;
}

.mode-badge--model_only,
.mode-badge--reference_answer {
  border-color: rgba(255, 159, 10, 0.24);
  color: var(--zm-orange);
  background: rgba(255, 159, 10, 0.08);
}

.mode-note {
  color: var(--zm-ink-faint);
  font-size: 12px;
}

.summary {
  white-space: pre-line;
  color: var(--zm-ink);
  font-size: 14px;
  line-height: 1.85;
}

.content-block,
.advice-block,
.citation-block {
  margin-top: 20px;
}

.content-block h4,
.citation-block h4 {
  margin-bottom: 9px;
  color: var(--zm-ink);
  font-size: 13px;
}

.content-block ul {
  display: grid;
  gap: 7px;
  margin: 0;
  padding-left: 20px;
}

.content-block li {
  color: var(--zm-ink-soft);
  font-size: 13px;
  line-height: 1.7;
}

.content-block--warning li::marker {
  color: var(--zm-orange);
}

.advice-block {
  padding: 14px 16px;
  border-left: 3px solid var(--zm-accent);
  background: rgba(0, 122, 255, 0.055);
}

.advice-block span {
  color: var(--zm-accent);
  font-size: 12px;
  font-weight: 600;
}

.advice-block p {
  margin-top: 6px;
  white-space: pre-line;
  color: var(--zm-ink-soft);
  font-size: 13px;
  line-height: 1.75;
}

.degraded-block {
  margin-top: 16px;
  color: var(--zm-ink-faint);
  font-size: 12px;
  line-height: 1.6;
}

.citation-block {
  padding-top: 18px;
  border-top: 1px dashed var(--zm-border);
}

.citation-item {
  padding: 11px 0;
}

.citation-item + .citation-item {
  border-top: 1px solid var(--zm-border);
}

.citation-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.citation-title span {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border-radius: 50%;
  color: white;
  background: var(--zm-accent);
  font-size: 11px;
}

.citation-title strong {
  overflow: hidden;
  color: var(--zm-ink);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.citation-path {
  margin: 5px 0 0 28px;
  color: var(--zm-accent);
  font-size: 12px;
}

.citation-excerpt {
  margin: 6px 0 0 28px;
  color: var(--zm-ink-soft);
  font-size: 12px;
  line-height: 1.65;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.85);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 560px) {
  .panel-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
