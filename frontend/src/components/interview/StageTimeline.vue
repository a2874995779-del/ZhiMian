<script setup lang="ts">
import type { InterviewStage } from '../../types/interview'

defineProps<{
  stages: InterviewStage[]
  currentIndex: number
}>()
</script>

<template>
  <ol class="timeline">
    <li
      v-for="(stage, i) in stages"
      :key="stage.id"
      class="step"
      :class="{
        'step--done': i < currentIndex,
        'step--current': i === currentIndex,
      }"
    >
      <span class="step-dot"></span>
      <span class="step-label">{{ stage.name }}</span>
      <span v-if="i < stages.length - 1" class="step-line" aria-hidden="true"></span>
    </li>
  </ol>
</template>

<style scoped>
.timeline {
  display: flex;
  align-items: flex-start;
  list-style: none;
  margin: 0;
  padding: 0;
}

.step {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.step-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--zm-border-strong);
  transition: all var(--zm-dur-base) var(--zm-ease);
  position: relative;
  z-index: 1;
}

.step--done .step-dot {
  background: var(--zm-green);
  animation: dot-glow 2.2s ease-in-out infinite;
}

@keyframes dot-glow {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(52, 199, 89, 0.4);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(52, 199, 89, 0);
  }
}

.step--current .step-dot {
  background: var(--zm-accent);
  transform: scale(1.5);
  box-shadow: 0 0 12px var(--zm-accent-glow);
}

.step-label {
  font-size: 12px;
  color: var(--zm-ink-faint);
  font-weight: 600;
  transition: all var(--zm-dur-base) var(--zm-ease);
  white-space: nowrap;
}

.step--done .step-label {
  color: var(--zm-ink-soft);
}

.step--current .step-label {
  color: var(--zm-accent);
  transform: scale(1.08);
}

.step-line {
  position: absolute;
  top: 4.5px;
  left: calc(50% + 12px);
  width: calc(100% - 24px);
  height: 1.5px;
  background: var(--zm-border-strong);
}

.step--done .step-line {
  background: var(--zm-green);
  opacity: 0.5;
}
</style>
