<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Clock,
  Files,
  Grid,
  Microphone,
  Management,
  Moon,
  Notebook,
  Reading,
  Star,
  Sunny,
  SwitchButton,
  Trophy,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/auth'
import LoginDialog from './LoginDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loginDialogVisible = ref(false)

const navIcons: Record<string, any> = {
  grid: Grid,
  book: Reading,
  trophy: Trophy,
  mic: Microphone,
  history: Clock,
  wrong: Notebook,
  favorites: Star,
  review: Management,
  knowledge: Files,
  user: User,
}

const navItems = computed(() =>
  router.options.routes
    .filter((r) => !r.meta?.requiresAdmin || auth.user?.role === 'admin')
    .map((r) => ({
      path: r.path,
      name: r.name as string,
      label: (r.meta?.label as string) ?? '',
      icon: navIcons[(r.meta?.icon as string) ?? 'grid'],
    })),
)

const currentPageLabel = computed(() => {
  const current = navItems.value.find((item) => item.path === route.path)
  return current?.label ?? '智面'
})

const isInterviewSession = computed(() => route.path === '/interview' && typeof route.query.sessionId === 'string')

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

watch(
  () => route.query.login,
  (login) => {
    if (login === '1' && !auth.user) {
      loginDialogVisible.value = true
    }
  },
  { immediate: true },
)

watch(
  () => auth.token,
  (token) => {
    if (!token) {
      if (route.meta.requiresAuth) {
        router.replace({
          path: '/questions',
          query: {
            login: '1',
            redirect: route.fullPath,
          },
        })
      }
      return
    }
    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect.startsWith('/')) {
      router.replace(redirect)
    }
  },
)

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
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">
          <span class="brand-symbol"><img class="brand-logo" src="/favicon.svg" alt="" /></span>
          <span>智面</span>
        </div>
        <p class="brand-sub">ZhiMian AI Interview</p>
      </div>

      <nav class="nav">
        <p class="nav-caption">工作区</p>
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

      <div class="user-block">
        <template v-if="auth.user">
          <span class="user-avatar">{{ (auth.user.nickname || auth.user.username).slice(0, 1).toUpperCase() }}</span>
          <span class="user-name">{{ auth.user.nickname || auth.user.username }}</span>
          <button class="icon-btn" type="button" aria-label="退出登录" @click="auth.logout()">
            <el-icon><SwitchButton /></el-icon>
          </button>
        </template>
        <button v-else class="login-btn" type="button" @click="loginDialogVisible = true">
          <el-icon><User /></el-icon>
          <span>登录</span>
        </button>
      </div>

      <button class="theme-toggle" type="button" @click="toggleTheme" :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'">
        <el-icon><component :is="isDark ? Sunny : Moon" /></el-icon>
        <span>{{ isDark ? '浅色模式' : '深色模式' }}</span>
      </button>
    </aside>

    <LoginDialog v-model="loginDialogVisible" />

    <div class="main" :class="{ 'main--interview': isInterviewSession }">
      <div class="mobile-account-bar">
        <RouterLink class="mobile-brand" to="/questions">智面</RouterLink>
        <template v-if="auth.user">
          <span class="user-avatar">{{ (auth.user.nickname || auth.user.username).slice(0, 1).toUpperCase() }}</span>
          <span class="user-name">{{ auth.user.nickname || auth.user.username }}</span>
          <button class="icon-btn" type="button" aria-label="退出登录" @click="auth.logout()">
            <el-icon><SwitchButton /></el-icon>
          </button>
        </template>
        <button v-else class="login-btn" type="button" @click="loginDialogVisible = true">
          <el-icon><User /></el-icon>
          <span>登录</span>
        </button>
      </div>
      <header v-if="!isInterviewSession" class="topbar">
        <div class="topbar-copy">
          <p class="topbar-kicker">{{ greeting }}</p>
          <h1 class="topbar-title">{{ currentPageLabel }}</h1>
        </div>
        <div class="topbar-status"><span class="status-dot"></span>训练空间在线</div>
      </header>

      <nav v-if="!isInterviewSession" class="mobile-nav" aria-label="移动端导航">
        <RouterLink
          v-for="item in navItems"
          :key="`mobile-${item.path}`"
          :to="item.path"
          class="mobile-nav-item"
          :class="{ 'mobile-nav-item--active': route.path === item.path }"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <main class="content" :class="{ 'content--interview': isInterviewSession }">
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
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
}

.sidebar {
  position: relative;
  z-index: 1;
  width: 250px;
  height: 100%;
  min-height: 0;
  flex-shrink: 0;
  margin: 0;
  padding: 24px 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  border: 0;
  border-right: 1px solid var(--zm-border);
  border-radius: 0;
  background: var(--zm-bg-elevated);
  box-shadow: none;
}

.brand {
  flex-shrink: 0;
  padding: 0 10px 10px;
}

.brand-mark {
  display: flex;
  align-items: center;
  gap: 11px;
  font-size: 20px;
  font-weight: 750;
}

.brand-symbol {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--zm-accent);
  color: #fff;
  box-shadow: 0 6px 14px rgba(10, 132, 255, 0.2);
}

.brand-logo {
  width: 27px;
  height: 27px;
  object-fit: contain;
  filter: brightness(0) invert(1);
}

.brand-sub {
  margin-top: 8px;
  font-size: 11px;
  font-family: var(--zm-font-mono);
  color: var(--zm-ink-faint);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
}

.nav-caption {
  padding: 0 12px;
  color: var(--zm-ink-faint);
  font-size: 11px;
  font-weight: 650;
}

.nav-item {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: var(--zm-radius-sm);
  color: var(--zm-ink-soft);
  font-size: 14px;
  font-weight: 550;
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
  left: -18px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: var(--zm-radius-pill);
  background: var(--zm-accent);
  box-shadow: none;
}

.nav-icon {
  font-size: 17px;
}

.user-block {
  display: flex;
  flex-shrink: 0;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 8px;
  border-top: 1px solid var(--zm-border);
  border-bottom: 1px solid var(--zm-border);
}

.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--zm-accent-soft);
  color: var(--zm-accent);
  font-size: 12px;
  font-weight: 700;
}

.user-name {
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  flex: 1;
  color: var(--zm-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: var(--zm-radius-sm);
  background: transparent;
  color: var(--zm-ink-faint);
  cursor: pointer;
  flex-shrink: 0;
  transition: all var(--zm-dur-fast) var(--zm-ease);
}

.icon-btn:hover {
  background: var(--zm-surface-strong);
  color: var(--zm-red);
}

.login-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 9px 12px;
  border-radius: var(--zm-radius-sm);
  border: 1px solid var(--zm-border-strong);
  background: var(--zm-bg-elevated);
  color: var(--zm-accent);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all var(--zm-dur-fast) var(--zm-ease);
}

.login-btn:hover {
  background: var(--zm-accent);
  color: white;
}

.theme-toggle {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--zm-radius-sm);
  border: 1px solid var(--zm-border);
  background: var(--zm-bg);
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
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  display: flex;
  flex-shrink: 0;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 28px 40px 18px;
  border-bottom: 1px solid var(--zm-border);
}

.topbar-kicker {
  color: var(--zm-ink-faint);
  font-size: 12px;
  font-weight: 600;
}

.topbar-title {
  margin-top: 3px;
  font-size: 24px;
  letter-spacing: 0;
}

.topbar-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 10px;
  border: 1px solid var(--zm-border);
  border-radius: var(--zm-radius-pill);
  color: var(--zm-ink-soft);
  font-size: 12px;
  white-space: nowrap;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--zm-green);
  box-shadow: 0 0 0 3px rgba(52, 199, 89, 0.12);
}

.mobile-nav,
.mobile-account-bar {
  display: none;
}

.content {
  flex: 1;
  min-height: 0;
  min-width: 0;
  padding: 26px 40px 44px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.content--interview {
  padding: 0;
}

@media (max-width: 900px) {
  .sidebar {
    display: none;
  }

  .mobile-account-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
    min-height: 48px;
    flex-shrink: 0;
    padding: 7px 16px;
    border-bottom: 1px solid var(--zm-border);
    background: var(--zm-bg-elevated);
  }

  .mobile-brand {
    margin-right: auto;
    flex-shrink: 0;
    font-size: 16px;
    font-weight: 700;
  }

  .mobile-account-bar .user-name {
    flex: 0 1 auto;
    max-width: 50%;
  }

  .mobile-account-bar .login-btn {
    width: auto;
    padding: 5px 12px;
  }
  .topbar,
  .content {
    padding-left: 20px;
    padding-right: 20px;
  }

  .topbar {
    padding-top: 20px;
    padding-bottom: 14px;
  }

  .mobile-nav {
    display: flex;
    flex-shrink: 0;
    gap: 6px;
    overflow-x: auto;
    padding: 12px 20px 0;
  }

  .content--interview {
    padding: 0;
  }

  .mobile-nav-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
    padding: 8px 10px;
    border-radius: var(--zm-radius-sm);
    color: var(--zm-ink-soft);
    font-size: 12px;
    font-weight: 600;
  }

  .mobile-nav-item--active {
    background: var(--zm-accent-soft);
    color: var(--zm-accent);
  }
}

@media (max-width: 520px) {
  .topbar-status {
    display: none;
  }

  .topbar-title {
    font-size: 21px;
  }
}

:global(:root[data-theme='dark']) .sidebar {
  background: rgba(27, 34, 45, 0.94);
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.22);
}
</style>
