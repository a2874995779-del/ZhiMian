<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, Delete, RefreshRight, StarFilled, View } from '@element-plus/icons-vue'
import type { FavoriteQuestionItem } from '../../types/favorite'
import type { WrongQuestionItem } from '../../types/wrong-question'

type CollectionItem = FavoriteQuestionItem | WrongQuestionItem

const props = defineProps<{
  item: CollectionItem
  mode: 'favorite' | 'wrong'
  busyAction?: string | null
}>()

const emit = defineEmits<{
  open: [questionId: number]
  remove: [questionId: number]
  'toggle-mastered': [questionId: number, mastered: boolean]
}>()

const difficultyMeta = computed(() => {
  const map = {
    1: { label: '简单', color: 'var(--zm-green)' },
    2: { label: '中等', color: 'var(--zm-orange)' },
    3: { label: '困难', color: 'var(--zm-red)' },
  } as const
  return map[props.item.difficulty as 1 | 2 | 3] ?? map[2]
})

const isWrong = computed(() => props.mode === 'wrong')
const wrongItem = computed(() => (isWrong.value ? (props.item as WrongQuestionItem) : null))
const isMastered = computed(() => wrongItem.value?.status === 1)
const favoriteItem = computed(() => (!isWrong.value ? (props.item as FavoriteQuestionItem) : null))
const viewCount = computed(() => favoriteItem.value?.viewCount ?? 0)
const favoriteTime = computed(() => favoriteItem.value?.favoriteTime ?? '')
const actionKey = (action: string) => `${props.item.questionId}:${action}`
</script>

<template>
  <article class="collection-card zm-glass zm-glass--hoverable">
    <div class="collection-card__top">
      <span class="difficulty">
        <span class="difficulty-dot" :style="{ background: difficultyMeta.color }"></span>
        {{ difficultyMeta.label }}
      </span>
      <span class="category zm-prompt">{{ item.categoryName }}</span>
    </div>

    <button class="title-button" type="button" @click="emit('open', item.questionId)">
      <span class="title-icon"><el-icon><component :is="isWrong ? CircleCheck : StarFilled" /></el-icon></span>
      <span>{{ item.title }}</span>
    </button>

    <div class="collection-tags">
      <span v-for="tag in item.tags ?? []" :key="tag" class="zm-tag">{{ tag }}</span>
      <span v-if="!item.tags?.length" class="empty-tags">暂无标签</span>
    </div>

    <div class="collection-meta">
      <template v-if="isWrong && wrongItem">
        <span>错 {{ wrongItem.wrongCount }} 次</span>
        <span>对 {{ wrongItem.correctCount }} 次</span>
        <span :class="isMastered ? 'status-mastered' : 'status-unmastered'">
          {{ isMastered ? '已掌握' : '未掌握' }}
        </span>
      </template>
      <template v-else>
        <span>浏览 {{ viewCount }}</span>
        <span>{{ favoriteTime ? favoriteTime.slice(0, 10) : '刚刚收藏' }}</span>
      </template>
    </div>

    <div class="collection-actions">
      <el-button text size="small" :icon="View" @click="emit('open', item.questionId)">查看题目</el-button>
      <el-button
        v-if="isWrong"
        text
        size="small"
        :icon="isMastered ? RefreshRight : CircleCheck"
        :loading="busyAction === actionKey(isMastered ? 'unmastered' : 'mastered')"
        @click="emit('toggle-mastered', item.questionId, !isMastered)"
      >
        {{ isMastered ? '重新标记' : '标记掌握' }}
      </el-button>
      <el-button
        text
        type="danger"
        size="small"
        :icon="Delete"
        :loading="busyAction === actionKey('remove')"
        @click="emit('remove', item.questionId)"
      >
        {{ isWrong ? '移出错题本' : '取消收藏' }}
      </el-button>
    </div>
  </article>
</template>

<style scoped>
.collection-card {
  min-height: 248px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.collection-card__top,
.collection-meta,
.collection-actions {
  display: flex;
  align-items: center;
}

.collection-card__top,
.collection-meta {
  justify-content: space-between;
  gap: 12px;
}

.difficulty {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--zm-ink-soft);
  font-size: 12px;
  font-weight: 600;
}

.difficulty-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.category {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}

.title-button {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-height: 54px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--zm-ink);
  font: inherit;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
}

.title-button:hover {
  color: var(--zm-accent);
}

.title-icon {
  color: var(--zm-accent);
  flex-shrink: 0;
  margin-top: 3px;
}

.collection-tags {
  min-height: 25px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.empty-tags {
  color: var(--zm-ink-faint);
  font-size: 12px;
}

.collection-meta {
  padding-top: 12px;
  border-top: 1px solid var(--zm-border);
  color: var(--zm-ink-faint);
  font-size: 11px;
}

.status-mastered {
  color: var(--zm-green);
}

.status-unmastered {
  color: var(--zm-orange);
}

.collection-actions {
  flex-wrap: wrap;
  gap: 2px;
  margin-top: auto;
}

.collection-actions :deep(.el-button) {
  margin-left: 0;
}

@media (max-width: 560px) {
  .collection-card {
    min-height: 0;
  }

  .collection-meta {
    align-items: flex-start;
    flex-wrap: wrap;
    justify-content: flex-start;
  }
}
</style>
