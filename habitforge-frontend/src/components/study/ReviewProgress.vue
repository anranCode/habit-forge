<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** 本次会话已评分张数 */
  reviewed: number
  /** 本次会话总张数（已评 + 队列剩余） */
  total: number
  /** 今日已复习总数（后端统计） */
  reviewedToday: number
}>()

const remaining = computed(() => Math.max(props.total - props.reviewed, 0))
const percentage = computed(() =>
  props.total > 0 ? Math.round((props.reviewed / props.total) * 100) : 0
)
</script>

<template>
  <div class="review-progress card">
    <div class="flex-between stats">
      <span class="item">
        进度 <b>{{ reviewed }}</b>/<span class="t">{{ total }}</span>
      </span>
      <span class="item">
        剩余 <b class="left">{{ remaining }}</b>
      </span>
      <span class="item text-light">
        今日已复习 <b class="today">{{ reviewedToday }}</b>
      </span>
    </div>
    <van-progress
      :percentage="percentage"
      :pivot-text="percentage + '%'"
      color="#ff7a00"
      track-color="#f0f2f5"
      stroke-width="6"
      class="bar"
    />
  </div>
</template>

<style scoped lang="scss">
.review-progress {
  padding: 12px 16px 14px;
  margin-bottom: 12px;

  .stats {
    margin-bottom: 8px;
    font-size: 12px;

    .item b {
      font-size: 15px;
      margin: 0 1px;
    }

    .t {
      color: $text-light;
      font-size: 12px;
    }

    .left {
      color: $primary;
    }

    .today {
      color: $success;
    }
  }

  .bar {
    margin-top: 2px;
  }
}

:deep(.van-progress__pivot) {
  font-size: 10px;
  padding: 1px 5px;
}
</style>
