<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
const label = (route.meta?.label as string) ?? '这个模块'
</script>

<template>
  <div class="coming-soon zm-glass">
    <p class="tag zm-prompt">&gt; building_next</p>
    <h2>{{ label }}正在打磨</h2>
    <p class="desc">
      仪表盘和题库中心已经上线,{{ label }}走的是同一套设计语言——玻璃拟态卡片、苹果缓动曲线、终端提示符信号,下一批交付里见。
    </p>
  </div>
</template>

<style scoped>
.coming-soon {
  padding: 64px 48px;
  text-align: center;
  max-width: 560px;
  margin: 10vh auto 0;
}

.tag {
  font-size: 13px;
  display: inline-block;
  margin-bottom: 14px;
}

h2 {
  font-size: 24px;
  margin-bottom: 12px;
}

.desc {
  color: var(--zm-ink-soft);
  line-height: 1.7;
  font-size: 14px;
}
</style>
