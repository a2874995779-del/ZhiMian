<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Grid, Reading, Trophy, Microphone, User, Moon, Sunny } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const navIcons: Record<string, any> = {
  grid: Grid,
  book: Reading,
  trophy: Trophy,
  mic: Microphone,
  user: User,
}

const navItems = computed(() =>
  router.options.routes.map((r) => ({
    path: r.path,
    name: r.name as string,
    label: (r.meta?.label as string) ?? '',
    icon: navIcons[(r.meta?.icon as string) ?? 'grid'],
  })),
)

const isDark = ref(false)

function applyTheme() {
  document.documentElement.dataset.theme = isDark.value ? 'dark' : 'light'
}

function toggleTheme() {
  isDark.value = !isDark.value
  localStorage.setItem('zm-theme', isDark.value ? 'dark' : 'light')
  applyTheme()
}

onMounted(() => {
  const saved = localStorage.getItem('zm-theme')
  if (saved) {
    isDark.value = saved === 'dark'
  } else {
    isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  applyTheme()
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})
</script>

<template>
  <div class="shell">
    <div class="ambient" aria-hidden="true">
      <span class="blob blob--accent"></span>
      <span class="blob blob--gold"></span>
    </div>

    <aside class="sidebar zm-glass">
      <div class="brand">
        <div class="brand-mark">智面</div>
        <p class="brand-sub">
          <span class="zm-prompt">&gt; Interview Elite Master</span><span class="zm-cursor">&nbsp;</span>
        </p>
      </div>

      <nav class="nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ 'nav-item--active': route.path === item.path }"
        >
          <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <button class="theme-toggle" type="button" @click="toggleTheme" :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'">
        <el-icon><component :is="isDark ? Sunny : Moon" /></el-icon>
        <span>{{ isDark ? '浅色模式' : '深色模式' }}</span>
      </button>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar-greeting">
          {{ greeting }},<span class="zm-prompt">准备好迎接下一题了吗</span><span class="zm-cursor">&nbsp;</span>
        </div>
      </header>

      <main class="content">
        <RouterView v-slot="{ Component }">
          <Transition name="zm-fade" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  position: relative;
  display: flex;
  min-height: 100vh;
  overflow: hidden;
}

.ambient {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.35;
}

.blob--accent {
  width: 46vw;
  height: 46vw;
  background: var(--zm-accent);
  top: -12vw;
  right: -10vw;
}

.blob--gold {
  width: 34vw;
  height: 34vw;
  background: var(--zm-gold);
  bottom: -14vw;
  left: -8vw;
  opacity: 0.22;
}

.sidebar {
  position: relative;
  z-index: 1;
  width: 252px;
  flex-shrink: 0;
  margin: 16px 0 16px 16px;
  padding: 22px 16px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  border-radius: var(--zm-radius-lg);
}

.brand-mark {
  font-family: var(--zm-font-display);
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.brand-sub {
  margin-top: 4px;
  font-size: 12px;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--zm-radius-sm);
  color: var(--zm-ink-soft);
  font-size: 14px;
  font-weight: 600;
  position: relative;
  transition: background var(--zm-dur-fast) var(--zm-ease), color var(--zm-dur-fast) var(--zm-ease),
    transform var(--zm-dur-fast) var(--zm-ease);
}

.nav-item:hover {
  background: var(--zm-surface-strong);
  color: var(--zm-ink);
  transform: translateX(2px);
}

.nav-item--active {
  color: var(--zm-accent);
  background: var(--zm-accent-soft);
}

.nav-item--active::before {
  content: '';
  position: absolute;
  left: -16px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: var(--zm-radius-pill);
  background: var(--zm-accent);
  box-shadow: var(--zm-shadow-glow-accent);
}

.nav-icon {
  font-size: 17px;
}

.theme-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--zm-radius-sm);
  border: 1px solid var(--zm-border);
  background: transparent;
  color: var(--zm-ink-soft);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--zm-dur-fast) var(--zm-ease);
}

.theme-toggle:hover {
  background: var(--zm-surface-strong);
  color: var(--zm-ink);
}

.main {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  padding: 28px 40px 8px;
}

.topbar-greeting {
  font-family: var(--zm-font-display);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--zm-ink);
}

.content {
  flex: 1;
  padding: 12px 40px 40px;
  overflow-y: auto;
}

@media (max-width: 900px) {
  .sidebar {
    display: none;
  }
  .topbar,
  .content {
    padding-left: 20px;
    padding-right: 20px;
  }
}
</style>
