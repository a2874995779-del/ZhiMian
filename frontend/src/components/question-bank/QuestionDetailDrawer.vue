<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Star, StarFilled } from '@element-plus/icons-vue'
import { addFavorite, checkFavorite, removeFavorite } from '../../api/favorite'
import { useAuthStore } from '../../stores/auth'
import type { QuestionDetail } from '../../types/question'
import MockCodeBlock from './MockCodeBlock.vue'

const props = defineProps<{
  modelValue: boolean
  question: QuestionDetail | null
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'favorite-changed': [favorited: boolean]
}>()

const difficultyLabel: Record<number, string> = { 1: '简单', 2: '中等', 3: '困难' }
const auth = useAuthStore()
const favorited = ref(false)
const favoriteLoading = ref(false)

async function loadFavoriteState() {
  if (!props.modelValue || !props.question || !auth.token) {
    favorited.value = false
    return
  }

  const questionId = props.question.id
  favoriteLoading.value = true
  try {
    const state = await checkFavorite(questionId)
    if (props.question?.id === questionId) {
      favorited.value = state
    }
  } finally {
    favoriteLoading.value = false
  }
}

async function toggleFavorite() {
  if (!props.question || !auth.token || favoriteLoading.value) return

  const questionId = props.question.id
  favoriteLoading.value = true
  try {
    if (favorited.value) {
      await removeFavorite(questionId)
    } else {
      await addFavorite(questionId)
    }
    favorited.value = !favorited.value
    emit('favorite-changed', favorited.value)
    ElMessage.success(favorited.value ? '已加入收藏夹' : '已取消收藏')
  } finally {
    favoriteLoading.value = false
  }
}

watch(
  () => [props.question?.id, props.modelValue, auth.token],
  loadFavoriteState,
  { immediate: true },
)
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="72%"
    direction="rtl"
    :with-header="false"
    destroy-on-close
    @update:model-value="(v: boolean) => $emit('update:modelValue', v)"
  >
    <div v-if="question" class="detail">
      <header class="detail-header">
        <div class="detail-header-row">
          <div class="detail-meta">
            <span class="zm-tag zm-tag--active">{{ question.categoryName }}</span>
            <span class="zm-tag">{{ difficultyLabel[question.difficulty] }}</span>
            <span v-for="tag in question.tags" :key="tag" class="zm-tag">{{ tag }}</span>
          </div>
          <el-button
            v-if="auth.token"
            text
            :type="favorited ? 'warning' : 'primary'"
            :icon="favorited ? StarFilled : Star"
            :loading="favoriteLoading"
            @click="toggleFavorite"
          >
            {{ favorited ? '已收藏' : '收藏' }}
          </el-button>
        </div>
        <h2 class="detail-title">{{ question.title }}</h2>
      </header>

      <div class="detail-body" :class="{ 'detail-body--single': !question.codeSnippet }">
        <section class="detail-analysis">
          <template v-if="question.content">
            <h4 class="zm-prompt">&gt; 题干说明</h4>
            <p class="analysis-text">{{ question.content }}</p>
          </template>

          <h4 class="zm-prompt" :class="{ 'analysis-answer-title': question.content }">&gt; 参考答案</h4>
          <p class="analysis-text analysis-text--answer">{{ question.answer }}</p>

          <slot name="after-answer" />
        </section>

        <section v-if="question.codeSnippet" class="detail-code">
          <MockCodeBlock :language="question.codeSnippet.language" :code="question.codeSnippet.code" />
        </section>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.detail {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 36px 40px;
}

.detail-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-header-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.detail-title {
  font-size: 24px;
  line-height: 1.4;
}

.detail-body {
  margin-top: 28px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.detail-body--single {
  grid-template-columns: 1fr;
  max-width: 720px;
}

/* 长答案按换行符分段展示 */
.analysis-text--answer {
  white-space: pre-line;
}

.detail-analysis h4 {
  font-size: 13px;
  margin-bottom: 10px;
}

.analysis-answer-title {
  margin-top: 22px;
}

.analysis-text {
  font-size: 14px;
  line-height: 1.9;
  color: var(--zm-ink-soft);
}

.detail-code {
  position: sticky;
  top: 0;
  align-self: start;
}

@media (max-width: 860px) {
  .detail-body {
    grid-template-columns: 1fr;
  }

  .detail-header-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
