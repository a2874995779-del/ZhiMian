<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    nickname: string
    hue: number
    size?: number
    halo?: boolean
  }>(),
  { size: 44, halo: false },
)

const initial = props.nickname.trim().charAt(0)
</script>

<template>
  <span
    class="avatar"
    :class="{ 'avatar--halo': halo }"
    :style="{
      width: size + 'px',
      height: size + 'px',
      fontSize: size * 0.4 + 'px',
      background: `linear-gradient(135deg, hsl(${hue} 72% 58%), hsl(${hue + 36} 68% 44%))`,
      '--halo-color': `hsl(${hue} 80% 60% / 0.45)`,
    }"
  >
    {{ initial }}
  </span>
</template>

<style scoped>
.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: white;
  font-weight: 700;
  font-family: var(--zm-font-display);
  flex-shrink: 0;
  user-select: none;
}

.avatar--halo {
  box-shadow: 0 0 0 3px var(--zm-bg-elevated), 0 0 24px var(--halo-color);
}
</style>
