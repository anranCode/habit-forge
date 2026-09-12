<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{ content: string }>()

const html = computed(() => renderMarkdown(props.content))
</script>

<template>
  <div class="md-body" v-html="html" />
</template>

<style scoped lang="scss">
// v-html 注入的内容不带 scoped 属性，统一走 :deep()
.md-body {
  font-size: 15px;
  line-height: 1.75;
  color: $text-main;
  word-break: break-word;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4) {
  margin: 20px 0 10px;
  line-height: 1.4;
  font-weight: 700;
}

.md-body :deep(h1) {
  font-size: 20px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f5;
}

.md-body :deep(h2) {
  font-size: 18px;
  // 主题橙强调：小节竖条
  padding-left: 10px;
  border-left: 4px solid $primary;
}

.md-body :deep(h3) {
  font-size: 16px;
}

.md-body :deep(h4) {
  font-size: 15px;
  color: $text-light;
}

.md-body :deep(p) {
  margin: 8px 0;
}

.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 8px 0;
  padding-left: 22px;
}

.md-body :deep(li) {
  margin: 4px 0;
}

.md-body :deep(li::marker) {
  color: $primary;
}

.md-body :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 12px;
  border-left: 4px solid rgba(255, 122, 0, 0.5);
  border-radius: 0 8px 8px 0;
  background: rgba(255, 122, 0, 0.06);
  color: #5b6472;
}

.md-body :deep(code) {
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace;
  font-size: 13px;
  background: #f2f3f7;
  border-radius: 4px;
  padding: 2px 5px;
}

.md-body :deep(pre) {
  margin: 12px 0;
  padding: 12px 14px;
  background: #282c34;
  border-radius: 10px;
  overflow-x: auto;
}

.md-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: #abb2bf;
  font-size: 13px;
  line-height: 1.6;
}

.md-body :deep(img) {
  max-width: 100%;
  border-radius: 10px;
  display: block;
  margin: 12px auto;
}

.md-body :deep(a) {
  color: $primary;
  text-decoration: none;
}

.md-body :deep(hr) {
  border: none;
  border-top: 1px dashed #dfe3ea;
  margin: 16px 0;
}

.md-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
  font-size: 14px;
  display: block;
  overflow-x: auto;
}

.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid #e6e9ef;
  padding: 6px 10px;
}

.md-body :deep(th) {
  background: #f6f7fb;
  font-weight: 600;
}

.md-body :deep(strong) {
  font-weight: 700;
}
</style>
