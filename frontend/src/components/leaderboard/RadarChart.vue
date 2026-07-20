<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { SkillPoint } from '../../types/rank'

const props = withDefaults(
  defineProps<{
    skills: SkillPoint[]
    size?: number
  }>(),
  { size: 220 },
)

const center = computed(() => props.size / 2)
const radius = computed(() => props.size / 2 - 34) // 留出标签空间

// 第 i 根轴的角度:从正上方开始,顺时针均分
function axisPoint(i: number, ratio: number) {
  const angle = -Math.PI / 2 + (i * 2 * Math.PI) / props.skills.length
  return {
    x: center.value + radius.value * ratio * Math.cos(angle),
    y: center.value + radius.value * ratio * Math.sin(angle),
  }
}

function ringPoints(ratio: number): string {
  return props.skills.map((_, i) => {
    const p = axisPoint(i, ratio)
    return `${p.x},${p.y}`
  }).join(' ')
}

const dataPoints = computed(() =>
  props.skills.map((s, i) => {
    const p = axisPoint(i, s.value / 100)
    return `${p.x},${p.y}`
  }).join(' '),
)

const labels = computed(() =>
  props.skills.map((s, i) => {
    const p = axisPoint(i, 1.22)
    return { ...s, x: p.x, y: p.y }
  }),
)

// 入场动画:数据多边形从中心缩放展开
const mounted = ref(false)
onMounted(() => requestAnimationFrame(() => (mounted.value = true)))
</script>

<template>
  <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" class="radar">
    <!-- 网格环 -->
    <polygon
      v-for="ratio in [0.25, 0.5, 0.75, 1]"
      :key="ratio"
      :points="ringPoints(ratio)"
      fill="none"
      stroke="var(--zm-border-strong)"
      :stroke-width="ratio === 1 ? 1.2 : 0.7"
    />
    <!-- 轴线 -->
    <line
      v-for="(s, i) in skills"
      :key="s.name"
      :x1="center"
      :y1="center"
      :x2="axisPoint(i, 1).x"
      :y2="axisPoint(i, 1).y"
      stroke="var(--zm-border)"
      stroke-width="0.7"
    />
    <!-- 数据多边形 -->
    <g
      class="data-layer"
      :style="{
        transform: mounted ? 'scale(1)' : 'scale(0.1)',
        transformOrigin: `${center}px ${center}px`,
      }"
    >
      <polygon :points="dataPoints" fill="var(--zm-accent-soft)" stroke="var(--zm-accent)" stroke-width="2" stroke-linejoin="round" />
      <circle
        v-for="(s, i) in skills"
        :key="s.name"
        :cx="axisPoint(i, s.value / 100).x"
        :cy="axisPoint(i, s.value / 100).y"
        r="3"
        fill="var(--zm-bg-elevated)"
        stroke="var(--zm-accent)"
        stroke-width="2"
      />
    </g>
    <!-- 标签 -->
    <text
      v-for="l in labels"
      :key="l.name"
      :x="l.x"
      :y="l.y"
      text-anchor="middle"
      dominant-baseline="middle"
      class="radar-label"
    >
      {{ l.name }}
    </text>
  </svg>
</template>

<style scoped>
.radar {
  display: block;
}

.data-layer {
  transition: transform 0.9s cubic-bezier(0.25, 1, 0.5, 1);
}

.data-layer polygon {
  filter: drop-shadow(0 0 8px var(--zm-accent-glow));
}

.radar-label {
  font-size: 11px;
  font-weight: 600;
  fill: var(--zm-ink-soft);
  font-family: var(--zm-font-body);
}
</style>
