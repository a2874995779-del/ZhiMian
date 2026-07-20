<script setup lang="ts">
import { computed } from 'vue'
import type { QuestionListItem } from '../../types/question'

const props = defineProps<{ question: QuestionListItem }>()
defineEmits<{ open: [id: number] }>()

const difficultyMeta = computed(() => {
  const map = {
    1: { label: '简单', color: 'var(--zm-green)' },
    2: { label: '中等', color: 'var(--zm-orange)' },
    3: { label: '困难', color: 'var(--zm-red)' },
  } as const
  return map[props.question.difficulty as 1 | 2 | 3] ?? map[2]
})
</script>

<template>
  <article class="q-card zm-glass zm-glass--hoverable" @click="$emit('open', question.id)">
    <div class="q-card-glow" aria-hidden="true"></div>

    <div class="q-card-top">
      <span class="difficulty">
        <span class="difficulty-dot" :style="{ background: difficultyMeta.color }"></span>
        {{ difficultyMeta.label }}
      </span>
      <span class="category zm-prompt">{{ question.categoryName }}</span>
    </div>

    <h3 class="q-title">{{ question.title }}</h3>

    <div class="q-tags">
      <span v-for="tag in question.tags" :key="tag" class="zm-tag">{{ tag }}</span>
    </div>

    <div class="q-meta">
      <div v-if="question.mastery !== undefined" class="mastery">
        <div class="mastery-track">
          <div class="mastery-fill" :style="{ width: question.mastery + '%' }"></div>
        </div>
        <span class="mastery-label">掌握度 {{ question.mastery }}%</span>
      </div>
      <span v-else class="mastery-label">浏览 {{ question.viewCount }}</span>
      <span class="read-time">{{ question.readMinutes !== undefined ? `约 ${question.readMinutes} 分钟` : '' }}</span>
    </div>
  </article>
</template>

<style scoped>
.q-card {
  position: relative;
  padding: 22px 24px;
  cursor: pointer;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.q-card-glow {
  position: absolute;
  top: -40%;
  right: -30%;
  width: 60%;
  height: 180%;
  background: radial-gradient(circle, var(--zm-accent-glow) 0%, transparent 70%);
  opacity: 0;
  transition: opacity var(--zm-dur-base) var(--zm-ease);
  pointer-events: none;
}

.q-card:hover .q-card-glow {
  opacity: 0.5;
}

.q-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.difficulty {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--zm-ink-soft);
}

.difficulty-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.category {
  font-size: 11px;
}

.q-title {
  font-size: 16px;
  line-height: 1.5;
  font-weight: 700;
  letter-spacing: -0.01em;
  min-height: 2.4em;
}

.q-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.q-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--zm-border);
}

.mastery {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.mastery-track {
  width: 56px;
  height: 4px;
  border-radius: var(--zm-radius-pill);
  background: var(--zm-border-strong);
  overflow: hidden;
  flex-shrink: 0;
}

.mastery-fill {
  height: 100%;
  border-radius: var(--zm-radius-pill);
  background: linear-gradient(90deg, var(--zm-accent), var(--zm-gold));
  transition: width var(--zm-dur-slow) var(--zm-ease);
}

.mastery-label {
  font-size: 11px;
  color: var(--zm-ink-faint);
  white-space: nowrap;
}

.read-time {
  font-size: 11px;
  color: var(--zm-ink-faint);
  white-space: nowrap;
}
</style>
