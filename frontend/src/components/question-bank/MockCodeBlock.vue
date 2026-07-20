<script setup lang="ts">
import { computed } from 'vue'
import { highlightLine } from './highlight'

const props = defineProps<{
  language: string
  code: string
}>()

const lines = computed(() =>
  props.code.replace(/^\n+|\n+$/g, '').split('\n').map((line) => highlightLine(line, props.language)),
)
</script>

<template>
  <div class="mock-editor">
    <div class="mock-editor-titlebar">
      <span class="dot dot--red"></span>
      <span class="dot dot--yellow"></span>
      <span class="dot dot--green"></span>
      <span class="mock-editor-filename zm-prompt">answer.{{ language }}</span>
    </div>
    <pre class="mock-editor-body"><code
      ><span v-for="(line, i) in lines" :key="i" class="code-line"
        ><span class="line-no">{{ i + 1 }}</span><span class="line-content" v-html="line"></span></span
    ></code></pre>
  </div>
</template>

<style scoped>
.mock-editor {
  border-radius: var(--zm-radius-md);
  overflow: hidden;
  background: #1b1b1f;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: var(--zm-shadow-lg);
}

.mock-editor-titlebar {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 11px 14px;
  background: rgba(255, 255, 255, 0.04);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.dot--red {
  background: #ff5f57;
}
.dot--yellow {
  background: #febc2e;
}
.dot--green {
  background: #28c840;
}

.mock-editor-filename {
  margin-left: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45) !important;
}

.mock-editor-body {
  margin: 0;
  padding: 16px 0;
  overflow-x: auto;
  font-family: var(--zm-font-mono);
  font-size: 13px;
  line-height: 1.75;
}

.code-line {
  display: flex;
}

.line-no {
  flex-shrink: 0;
  width: 40px;
  text-align: right;
  padding-right: 16px;
  color: rgba(255, 255, 255, 0.25);
  user-select: none;
}

.line-content {
  color: #e3e3e6;
  white-space: pre;
  padding-right: 20px;
}

.line-content :deep(.tok-keyword) {
  color: #ff7ab2;
}
.line-content :deep(.tok-string) {
  color: #a5e075;
}
.line-content :deep(.tok-number) {
  color: #d9a5ff;
}
.line-content :deep(.tok-comment) {
  color: rgba(255, 255, 255, 0.35);
  font-style: italic;
}
</style>
