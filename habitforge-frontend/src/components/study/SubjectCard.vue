<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import type { Subject } from '@/types/study'

const props = defineProps<{ subject: Subject }>()
const router = useRouter()

const progress = computed(() => {
  const s = props.subject
  return s.chapterTotal ? Math.round((s.chapterDone / s.chapterTotal) * 100) : 0
})

const countdown = computed(() => {
  const s = props.subject
  if (s.daysLeft === null) return ''
  // 后端把已过去的考期统一钳为 0，前端按 examDate 区分「今天考试」与「已过考期」
  if (s.daysLeft === 0) {
    return dayjs(s.examDate).isBefore(dayjs(), 'day') ? '已过考期' : '今天考试'
  }
  return `${s.daysLeft} 天后考试`
})
</script>

<template>
  <div class="card subject-card" @click="router.push(`/study/subjects/${subject.id}`)">
    <div class="flex-between">
      <span class="sname">{{ subject.name }}</span>
      <van-tag v-if="countdown" round :color="countdown === '已过考期' ? '#8a94a6' : '#ff7a00'">{{ countdown }}</van-tag>
      <van-tag v-else-if="!subject.examDate" round plain color="#c8ced8">未设考期</van-tag>
    </div>
    <div class="meta text-light">
      <span v-if="subject.examDate">
        {{ dayjs(subject.examDate).format('YYYY/M/D') }} 考试{{ subject.examSession ? ' · ' + subject.examSession : '' }}
      </span>
      <span v-else>暂无考试安排</span>
    </div>
    <div class="prog-row">
      <van-progress :percentage="progress" color="#ff7a00" :show-pivot="false" class="bar" />
      <span class="pnum text-light">{{ subject.chapterDone }}/{{ subject.chapterTotal }}</span>
    </div>
    <div class="tags">
      <van-tag plain round size="medium" color="#3498db">到期卡 {{ subject.dueCards }}</van-tag>
      <van-tag plain round size="medium" color="#e74c3c">错题 {{ subject.wrongCount }}</van-tag>
    </div>
  </div>
</template>

<style scoped lang="scss">
.subject-card {
  cursor: pointer;

  .sname {
    font-size: 15px;
    font-weight: 700;
  }

  .meta {
    margin-top: 6px;
    font-size: 12px;
  }

  .prog-row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 12px;

    .bar {
      flex: 1;
    }

    .pnum {
      font-size: 12px;
      flex-shrink: 0;
    }
  }

  .tags {
    display: flex;
    gap: 8px;
    margin-top: 10px;
  }
}
</style>
