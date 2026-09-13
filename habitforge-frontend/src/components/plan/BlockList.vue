<script setup lang="ts">
import { computed } from 'vue'
import type { PlanBlock } from '@/types/plan'
import BlockItem from './BlockItem.vue'

const props = defineProps<{
  blocks: PlanBlock[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'complete', id: string, checkinHabit: boolean): void
  (e: 'skip', id: string): void
  (e: 'reopen', id: string): void
}>()

/** 按开始时间升序（同日不同时区问题不存在，HH:mm 字典序即时间序） */
const sorted = computed(() =>
  [...props.blocks].sort((a, b) => a.startTime.localeCompare(b.startTime) || a.sortOrder - b.sortOrder)
)
</script>

<template>
  <div>
    <BlockItem
      v-for="b in sorted"
      :key="b.id"
      :block="b"
      :readonly="readonly"
      @complete="(id, c) => emit('complete', id, c)"
      @skip="(id) => emit('skip', id)"
      @reopen="(id) => emit('reopen', id)"
    />
    <!-- 历史只读态无生成/手动添加入口，文案需区分（非只读态保持引导原文案） -->
    <div v-if="!blocks.length" class="list-empty text-light">
      {{ readonly ? '当日无安排' : '还没有安排，点下方按钮生成或手动添加' }}
    </div>
  </div>
</template>

<style scoped lang="scss">
.list-empty {
  text-align: center;
  font-size: 13px;
  padding: 24px 0;
}
</style>
