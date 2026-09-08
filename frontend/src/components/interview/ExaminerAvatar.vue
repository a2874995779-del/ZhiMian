<script setup lang="ts">
defineProps<{
  speaking: boolean
  compact?: boolean
}>()
</script>

<template>
  <div class="examiner" :class="{ 'examiner--compact': compact }">
    <!-- 呼吸式脉冲涟漪:模拟 AI 考官正在说话 -->
    <div class="ripples" :class="{ 'ripples--active': speaking }" aria-hidden="true">
      <span class="ripple" v-for="i in 3" :key="i" :style="{ animationDelay: (i - 1) * 0.7 + 's' }"></span>
    </div>

    <div class="face">
      <img class="face-image" src="/ai-interviewer.png" alt="AI 面试官" />
    </div>

    <!-- 声波条 -->
    <div class="wavebar" :class="{ 'wavebar--active': speaking }" aria-hidden="true">
      <span v-for="i in 5" :key="i" class="bar" :style="{ animationDelay: i * 0.12 + 's' }"></span>
    </div>
  </div>
</template>

<style scoped>
.examiner {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  padding: 12px;
}

.ripples {
  position: absolute;
  top: 12px;
  width: 120px;
  height: 120px;
  pointer-events: none;
}

.ripple {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 1.5px solid var(--zm-accent);
  opacity: 0;
}

.ripples--active .ripple {
  animation: ripple-out 2.1s cubic-bezier(0.25, 1, 0.5, 1) infinite;
}

@keyframes ripple-out {
  0% {
    transform: scale(0.85);
    opacity: 0.55;
  }
  100% {
    transform: scale(1.65);
    opacity: 0;
  }
}

.face {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zm-bg-elevated);
  box-shadow: var(--zm-shadow-sm);
  position: relative;
  z-index: 1;
}

.face-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: inherit;
}

.wavebar {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 26px;
}

.bar {
  width: 4px;
  height: 6px;
  border-radius: var(--zm-radius-pill);
  background: var(--zm-accent);
  opacity: 0.35;
  transition: opacity var(--zm-dur-base) var(--zm-ease);
}

.wavebar--active .bar {
  opacity: 1;
  animation: bar-bounce 0.9s ease-in-out infinite;
}

@keyframes bar-bounce {
  0%,
  100% {
    height: 6px;
  }
  50% {
    height: 22px;
  }
}

.examiner--compact {
  gap: 0;
  padding: 0;
}

.examiner--compact .ripples {
  top: 0;
  width: 44px;
  height: 44px;
}

.examiner--compact .face {
  width: 44px;
  height: 44px;
  box-shadow: 0 5px 14px rgba(10, 132, 255, 0.16);
}

.examiner--compact .wavebar {
  display: none;
}
</style>
