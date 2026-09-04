<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Medal, Refresh } from '@element-plus/icons-vue'
import { fetchAnswerRanks, type RankType } from '../api/rank'
import PodiumCard from '../components/leaderboard/PodiumCard.vue'
import UserAvatar from '../components/leaderboard/UserAvatar.vue'
import type { RankRecord, RankUser } from '../types/rank'

const rankType = ref<RankType>('total')
const rankUsers = ref<RankUser[]>([])
const loading = ref(false)
const errorMessage = ref('')
const expandedUserId = ref<number | null>(null)

function avatarHue(userId: number) {
  return (userId * 47) % 360
}

function badgesFor(user: RankRecord) {
  if (user.rank === 1) return ['当前榜首', '稳定输出']
  if (user.rank <= 3) return ['前三名', '高频练习']
  if (user.count >= 100) return ['百题斩', '持续积累']
  if (user.count >= 30) return ['进阶中', '手感在线']
  return ['正在起步']
}

function toRankUser(user: RankRecord): RankUser {
  return {
    rank: user.rank,
    userId: user.userId,
    nickname: user.nickname || `用户 ${user.userId}`,
    avatarHue: avatarHue(user.userId),
    count: user.count,
    badges: badgesFor(user),
  }
}

async function loadRanks() {
  loading.value = true
  errorMessage.value = ''
  expandedUserId.value = null
  try {
    const records = await fetchAnswerRanks(rankType.value, 10)
    rankUsers.value = records.map(toRankUser)
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '排行榜加载失败'
  } finally {
    loading.value = false
  }
}

function changeRankType(type: RankType) {
  if (rankType.value === type) return
  rankType.value = type
  loadRanks()
}

// 名人堂按 2-1-3 排布,冠军居中且抬高
const podium = computed(() => {
  const top3 = rankUsers.value.slice(0, 3)
  return [top3[1], top3[0], top3[2]].filter(Boolean) as RankUser[]
})

const restRows = computed(() => rankUsers.value.slice(3))

function toggleExpand(userId: number) {
  expandedUserId.value = expandedUserId.value === userId ? null : userId
}

const expandedUser = computed(() => rankUsers.value.find((u) => u.userId === expandedUserId.value) ?? null)

onMounted(loadRanks)
</script>

<template>
  <div class="leaderboard">
    <div class="rank-toolbar">
      <div class="rank-tabs" role="tablist" aria-label="排行榜类型">
        <button class="rank-tab" :class="{ 'rank-tab--active': rankType === 'total' }" type="button" @click="changeRankType('total')">
          总榜
        </button>
        <button class="rank-tab" :class="{ 'rank-tab--active': rankType === 'daily' }" type="button" @click="changeRankType('daily')">
          今日榜
        </button>
      </div>
      <el-button :icon="Refresh" :loading="loading" circle aria-label="刷新排行榜" @click="loadRanks" />
    </div>

    <!-- 前三名名人堂 -->
    <section v-if="rankUsers.length > 0" class="hall" v-loading="loading">
      <PodiumCard
        v-for="(user, i) in podium"
        :key="user.userId"
        :user="user"
        :class="{ 'hall-center': i === 1 }"
        @select="toggleExpand"
      />
    </section>

    <!-- 名人堂选中用户的雷达展开区 -->
    <el-collapse-transition>
      <section v-if="expandedUser && expandedUser.rank <= 3" class="expand-panel zm-glass">
        <div class="expand-info">
          <p class="expand-eyebrow zm-prompt">&gt; rank_profile · {{ expandedUser.nickname }}</p>
          <div class="badge-wall">
            <span v-for="badge in expandedUser.badges" :key="badge" class="badge">
              <el-icon :size="13"><Medal /></el-icon>
              {{ badge }}
            </span>
          </div>
        </div>
      </section>
    </el-collapse-transition>

    <!-- 4 名以后的滚动排行 -->
    <section class="rank-table zm-glass" v-loading="loading">
      <div class="table-head">
        <span class="col col--rank">名次</span>
        <span class="col col--user">用户</span>
        <span class="col col--num">答对数</span>
      </div>

      <div v-for="user in restRows" :key="user.userId">
        <button
          class="table-row"
          :class="{ 'table-row--expanded': expandedUserId === user.userId }"
          type="button"
          @click="toggleExpand(user.userId)"
        >
          <span class="col col--rank zm-prompt">{{ String(user.rank).padStart(2, '0') }}</span>
          <span class="col col--user">
            <UserAvatar :nickname="user.nickname" :hue="user.avatarHue" :size="36" />
            <span class="row-name">{{ user.nickname }}</span>
          </span>
          <span class="col col--num row-power">{{ user.count.toLocaleString() }}</span>
        </button>

        <el-collapse-transition>
          <div v-if="expandedUserId === user.userId" class="row-expand">
            <div class="badge-wall">
              <span v-for="badge in user.badges" :key="badge" class="badge">
                <el-icon :size="13"><Medal /></el-icon>
                {{ badge }}
              </span>
            </div>
          </div>
        </el-collapse-transition>
      </div>

      <el-empty v-if="!loading && !errorMessage && rankUsers.length === 0" description="暂无排行数据，答对题目后会出现在这里" />
      <el-result v-if="!loading && errorMessage" icon="warning" :title="errorMessage">
        <template #extra>
          <el-button type="primary" @click="loadRanks">重新加载</el-button>
        </template>
      </el-result>
    </section>
  </div>
</template>

<style scoped>
.leaderboard {
  display: flex;
  flex-direction: column;
  gap: 22px;
  max-width: 920px;
}

.rank-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rank-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: var(--zm-radius-sm);
  background: var(--zm-surface-strong);
  border: 1px solid var(--zm-border);
}

.rank-tab {
  border: none;
  border-radius: var(--zm-radius-sm);
  padding: 7px 14px;
  background: transparent;
  color: var(--zm-ink-soft);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.rank-tab--active {
  background: var(--zm-bg-elevated);
  color: var(--zm-accent);
  box-shadow: var(--zm-shadow-sm);
}

.hall {
  display: grid;
  grid-template-columns: 1fr 1.15fr 1fr;
  gap: 18px;
  align-items: end;
}

.hall-center {
  transform: translateY(-14px);
}

.expand-panel {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 20px 32px;
}

.expand-eyebrow {
  font-size: 12px;
  margin-bottom: 14px;
}

.badge-wall {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  padding: 5px 12px;
  border-radius: var(--zm-radius-pill);
  color: var(--zm-gold);
  background: var(--zm-gold-soft);
  border: 1px solid rgba(212, 175, 55, 0.3);
}

.rank-table {
  padding: 8px 8px 12px;
  overflow: hidden;
}

.table-head {
  display: flex;
  align-items: center;
  padding: 12px 18px 10px;
  font-size: 11px;
  color: var(--zm-ink-faint);
  border-bottom: 1px solid var(--zm-border);
}

.table-row {
  width: 100%;
  display: flex;
  align-items: center;
  padding: 12px 18px;
  border: none;
  background: transparent;
  border-radius: var(--zm-radius-sm);
  cursor: pointer;
  font-size: 14px;
  color: var(--zm-ink);
  transition: background var(--zm-dur-fast) var(--zm-ease), transform var(--zm-dur-fast) var(--zm-ease);
  text-align: left;
}

.table-row:hover {
  background: var(--zm-surface-strong);
  transform: translateX(2px);
}

.table-row--expanded {
  background: var(--zm-accent-soft);
}

.col--rank {
  width: 56px;
  flex-shrink: 0;
  font-size: 13px;
}

.col--user {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.row-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col--num {
  width: 90px;
  flex-shrink: 0;
  text-align: right;
  font-family: var(--zm-font-mono);
  font-size: 13px;
  color: var(--zm-ink-soft);
}

.row-power {
  color: var(--zm-ink);
  font-weight: 600;
}

.row-expand {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 12px 18px 20px 56px;
}

@media (max-width: 760px) {
  .hall {
    grid-template-columns: 1fr;
    align-items: stretch;
  }
  .hall-center {
    transform: none;
    order: -1;
  }
  .expand-panel,
  .row-expand {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
