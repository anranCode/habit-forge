<script setup lang="ts">
import { computed } from 'vue'
import type { DailyPlan, PlanBlock } from '@/types/plan'

const props = defineProps<{ plan: DailyPlan | null }>()
defineEmits<{ (e: 'click'): void }>()

/** 参与进度的块 = 非 SKIPPED */
const activeBlocks = computed(() => (props.plan?.blocks || []).filter((b) => b.status !== 'SKIPPED'))
const doneCount = computed(() => activeBlocks.value.filter((b) => b.status === 'DONE').length)
const progress = computed(() =>
  activeBlocks.value.length ? Math.round((doneCount.value / activeBlocks.value.length) * 100) : 0
)

/** 下一个未完成块（按开始时间） */
const nextBlock = computed<PlanBlock | null>(() => {
  const pending = activeBlocks.value
    .filter((b) => b.status !== 'DONE')
    .sort((a, b) => a.startTime.localeCompare(b.startTime))
  return pending[0] || null
})
</script>

<template>
  <div class="card plan-card" @click="$emit('click')">
    <div class="flex-between">
      <span class="t">🤖 AI 今日安排</span>
      <span class="text-light go">{{ plan ? '查看 ›' : '去设置 ›' }}</span>
    </div>

    <!-- 未生成：引导录空闲时段 -->
    <div v-if="!plan" class="guide text-light">AI 生成今日安排 · 先设置空闲时段</div>

    <!-- 已生成：进度 + 下一个未完成块 -->
    <template v-else>
      <div class="flex-between meta">
        <span>{{ nextBlock ? `下一个 ${nextBlock.startTime} · ${nextBlock.title}` : '全部完成 🎉' }}</span>
        <span class="text-light">{{ doneCount }}/{{ activeBlocks.length }}</span>
      </div>
      <van-progress :percentage="progress" color="#ff7a00" :show-pivot="false" style="margin-top: 8px" />
    </template>
  </div>
</template>

<style scoped lang="scss">
.plan-card {
  cursor: pointer;

  .t {
    font-weight: 700;
    font-size: 15px;
  }

  .go {
    font-size: 12px;
  }

  .guide {
    margin-top: 10px;
    font-size: 13px;
  }

  .meta {
    margin-top: 10px;
    font-size: 13px;
    color: $text-main;

    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
</style>
