<script setup lang="ts">
defineProps<{
  speaking: boolean
}>()
</script>

<template>
  <div class="examiner">
    <!-- 呼吸式脉冲涟漪:模拟 AI 考官正在说话 -->
    <div class="ripples" :class="{ 'ripples--active': speaking }" aria-hidden="true">
      <span class="ripple" v-for="i in 3" :key="i" :style="{ animationDelay: (i - 1) * 0.7 + 's' }"></span>
    </div>

    <div class="face">
      <span class="face-glyph zm-prompt">AI</span>
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
  background: linear-gradient(135deg, var(--zm-accent) 0%, #123a63 100%);
  box-shadow: var(--zm-shadow-glow-accent);
  position: relative;
  z-index: 1;
}

.face-glyph {
  font-size: 34px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92) !important;
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
</style>
