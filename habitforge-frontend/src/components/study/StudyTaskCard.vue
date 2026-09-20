<script setup lang="ts">
import { computed } from 'vue'
import type { StudyOverview } from '@/types/study'

const props = defineProps<{ overview: StudyOverview | null }>()
defineEmits<{ (e: 'click'): void }>()

/** 最近考试：daysLeft 非空中考期最近的科目，如「高等数学（一） 34 天后」 */
const nearestExam = computed(() => {
  const subs = (props.overview?.subjects || []).filter((s) => s.daysLeft !== null)
  if (!subs.length) return ''
  const s = subs.reduce((a, b) => ((a.daysLeft ?? 0) <= (b.daysLeft ?? 0) ? a : b))
  return `${s.name} ${s.daysLeft === 0 ? '今天' : `${s.daysLeft} 天后`}`
})
</script>

<template>
  <div class="card study-task-card is-clickable" @click="$emit('click')">
    <div class="flex-between">
      <span class="t"><span class="deco">📚 </span>学习中心</span>
      <span class="text-light go">进入 ›</span>
    </div>
    <div class="stats">
      <div class="st">
        <div class="num">{{ overview?.dueCardsTotal ?? 0 }}</div>
        <div class="lb">待复习(张)</div>
      </div>
      <div class="st">
        <div class="num">{{ overview?.wrongsTotal ?? 0 }}</div>
        <div class="lb">错题(道)</div>
      </div>
      <div class="st exam">
        <div class="num txt">{{ nearestExam || '暂无考试' }}</div>
        <div class="lb">最近考试</div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.study-task-card {
  cursor: pointer;

  .t {
    font-weight: 700;
    font-size: 15px;
  }

  .go {
    font-size: 12px;
  }

  .stats {
    display: flex;
    gap: 10px;
    margin-top: 12px;

    .st {
      flex: 1;
      background: $bg-inset;
      border-radius: var(--hf-radius-panel, 10px);
      padding: 10px 8px;
      text-align: center;
      min-width: 0;

      .num {
        font-size: 18px;
        font-weight: 800;

        &.txt {
          font-size: 12px;
          font-weight: 700;
          color: $primary;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }

      .lb {
        font-size: 11px;
        color: $text-light;
        margin-top: 2px;
      }
    }
  }
}
</style>
