<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    value: number // 0-100
    size?: number
    stroke?: number
    color?: string
    trackColor?: string
    label?: string
  }>(),
  {
    size: 168,
    stroke: 14,
    color: 'var(--zm-accent)',
    trackColor: 'var(--zm-border-strong)',
    label: '',
  },
)

const radius = computed(() => (props.size - props.stroke) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)

const animatedValue = ref(0)
const displayNumber = ref(0)

onMounted(() => {
  // 下一帧再设目标值,保证 stroke-dashoffset 的 transition 能被浏览器捕捉到起始态
  requestAnimationFrame(() => {
    animatedValue.value = props.value
  })

  const duration = 900
  const start = performance.now()
  function tick(now: number) {
    const progress = Math.min(1, (now - start) / duration)
    const eased = 1 - Math.pow(1 - progress, 3)
    displayNumber.value = Math.round(props.value * eased)
    if (progress < 1) requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
})

const dashOffset = computed(() => circumference.value * (1 - animatedValue.value / 100))
</script>

<template>
  <div class="ring-wrap" :style="{ width: size + 'px', height: size + 'px' }">
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`">
      <circle
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="none"
        :stroke="trackColor"
        :stroke-width="stroke"
      />
      <circle
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="none"
        :stroke="color"
        :stroke-width="stroke"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
        class="progress-arc"
        :transform="`rotate(-90 ${size / 2} ${size / 2})`"
      />
    </svg>
    <div class="ring-center">
      <span class="ring-number">{{ displayNumber }}</span>
      <span v-if="label" class="ring-label">{{ label }}</span>
    </div>
  </div>
</template>

<style scoped>
.ring-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.progress-arc {
  transition: stroke-dashoffset 1.1s cubic-bezier(0.25, 1, 0.5, 1);
  filter: drop-shadow(0 0 6px var(--zm-accent-glow));
}

.ring-center {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring-number {
  font-family: var(--zm-font-display);
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--zm-ink);
}

.ring-label {
  font-size: 12px;
  color: var(--zm-ink-faint);
  margin-top: 2px;
}
</style>
