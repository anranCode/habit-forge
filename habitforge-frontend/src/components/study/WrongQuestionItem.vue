<script setup lang="ts">
import dayjs from 'dayjs'
import type { QuestionType, WrongQuestion } from '@/types/question'

defineProps<{ wrong: WrongQuestion }>()

const TYPE_LABEL: Record<QuestionType, string> = {
  SINGLE: '单选',
  MULTI: '多选',
  JUDGE: '判断',
  SHORT: '主观'
}

/** 题干摘要：粗剥 Markdown 记号后截断 */
function excerpt(stem: string): string {
  return stem
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '[图]')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#>*`_~-]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 40)
}
</script>

<template>
  <div class="card wrong-item">
    <div class="flex-between">
      <div class="tags">
        <van-tag round :color="wrong.wrongCount >= 3 ? 'var(--hf-danger)' : 'var(--hf-warning)'">错 {{ wrong.wrongCount }} 次</van-tag>
        <van-tag round color="#ff7a00">{{ TYPE_LABEL[wrong.question.questionType] }}</van-tag>
        <van-tag v-if="wrong.correctStreak > 0" round plain color="var(--hf-success)">连对 {{ wrong.correctStreak }}</van-tag>
      </div>
      <van-icon name="arrow" color="var(--hf-text-light)" />
    </div>
    <div class="stem">{{ excerpt(wrong.question.stem) }}</div>
    <div class="time text-light">最近错误 {{ dayjs(wrong.lastWrongAt).format('YYYY/M/D HH:mm') }}</div>
  </div>
</template>

<style scoped lang="scss">
.wrong-item {
  .tags {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .stem {
    margin-top: 8px;
    font-size: 14px;
    color: $text-main;
    line-height: 1.5;
  }

  .time {
    margin-top: 6px;
    font-size: 12px;
  }
}
</style>
