<script setup lang="ts">
import { computed } from 'vue'
import { Trophy } from '@element-plus/icons-vue'
import type { RankUser } from '../../types/rank'
import UserAvatar from './UserAvatar.vue'

const props = defineProps<{ user: RankUser }>()
defineEmits<{ select: [userId: number] }>()

const isChampion = computed(() => props.user.rank === 1)

const placeMeta = computed(() => {
  const map = {
    1: { label: 'CHAMPION', ring: 'var(--zm-gold)' },
    2: { label: 'RUNNER-UP', ring: 'rgba(160, 168, 180, 0.9)' },
    3: { label: 'THIRD', ring: 'rgba(196, 132, 92, 0.9)' },
  } as const
  return map[props.user.rank as 1 | 2 | 3]
})
</script>

<template>
  <article
    class="podium zm-glass zm-glass--hoverable"
    :class="{ 'podium--champion': isChampion }"
    @click="$emit('select', user.userId)"
  >
    <el-icon v-if="isChampion" class="crown" :size="26"><Trophy /></el-icon>

    <div class="podium-rank zm-prompt">#{{ user.rank }} · {{ placeMeta.label }}</div>

    <UserAvatar :nickname="user.nickname" :hue="user.avatarHue" :size="isChampion ? 76 : 60" halo />

    <h3 class="podium-name">{{ user.nickname }}</h3>

    <p class="podium-power">
      {{ user.count.toLocaleString() }}
      <span class="podium-power-unit">题答对</span>
    </p>

    <div class="podium-stats">
      <span>{{ user.badges[0] }}</span>
      <span class="stat-divider"></span>
      <span>第 {{ user.rank }} 名</span>
    </div>
  </article>
</template>

<style scoped>
.podium {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 28px 22px 22px;
  cursor: pointer;
  border-color: v-bind('placeMeta.ring');
}

.podium--champion {
  padding-top: 36px;
  border-width: 1.5px;
  box-shadow: var(--zm-shadow-glow-gold), var(--zm-shadow-md);
}

.crown {
  position: absolute;
  top: -14px;
  color: var(--zm-gold);
  filter: drop-shadow(0 0 10px var(--zm-gold-glow));
  animation: crown-float 2.6s ease-in-out infinite;
}

@keyframes crown-float {
  0%,
  100% {
    transform: translateY(0) rotate(-4deg);
  }
  50% {
    transform: translateY(-5px) rotate(4deg);
  }
}

.podium-rank {
  font-size: 11px;
  letter-spacing: 0.08em;
}

.podium-name {
  font-size: 16px;
}

.podium-power {
  font-family: var(--zm-font-display);
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--zm-ink);
}

.podium--champion .podium-power {
  color: var(--zm-gold);
}

.podium-power-unit {
  font-size: 12px;
  font-weight: 600;
  color: var(--zm-ink-faint);
  margin-left: 2px;
}

.podium-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--zm-ink-soft);
}

.stat-divider {
  width: 1px;
  height: 10px;
  background: var(--zm-border-strong);
}
</style>
