<script setup lang="ts">
import { computed } from 'vue'
import MarkdownPreview from '@/components/study/MarkdownPreview.vue'
import { useIsDesktop } from '@/composables/useDesktop'
import type { Question, QuestionType } from '@/types/question'

const isDesktop = useIsDesktop()

const props = withDefaults(
  defineProps<{
    question: Question
    /** 是否展开答案与解析（由父级控制「显示答案」） */
    revealed?: boolean
    /** revealed 时默认操作区是否显示「我答错了」（父级用 #actions 插槽可整体替换） */
    showMarkWrong?: boolean
  }>(),
  { revealed: false, showMarkWrong: true }
)

const emit = defineEmits<{ (e: 'add-wrong'): void }>()

const TYPE_LABEL: Record<QuestionType, string> = {
  SINGLE: '单选',
  MULTI: '多选',
  JUDGE: '判断',
  SHORT: '主观'
}

const TYPE_COLOR: Record<QuestionType, string> = {
  SINGLE: '#ff7a00',
  MULTI: '#3498db',
  JUDGE: '#27ae60',
  SHORT: '#9b59b6'
}

/** 桌面端：同色相降饱和，与 format.ts 的 categoryColorDesktop 同一套标准 */
const TYPE_COLOR_DESKTOP: Record<QuestionType, string> = {
  SINGLE: '#ff7a00',
  MULTI: '#6b8db5',
  JUDGE: '#5a9e7f',
  SHORT: '#8b6fa3'
}

const typeColor = computed(() =>
  isDesktop.value ? TYPE_COLOR_DESKTOP[props.question.questionType] : TYPE_COLOR[props.question.questionType]
)

/** 来源标签同样是内联 style，断点切换只能在 JS 侧做 */
const sourceColor = computed(() => (isDesktop.value ? '#6b8db5' : '#3498db'))

const SOURCE_LABEL: Record<string, string> = {
  PAST_EXAM: '真题',
  TEXTBOOK: '教材',
  AI: 'AI 生成',
  CUSTOM: '自定义'
}

/** 选择题正确项 key 集合（判断/主观无选项，返回空） */
const answerKeys = computed(() =>
  props.question.questionType === 'SINGLE' || props.question.questionType === 'MULTI'
    ? props.question.answer.split(',').map((s) => s.trim())
    : []
)

const answerText = computed(() => {
  const q = props.question
  if (q.questionType === 'JUDGE') return q.answer === 'T' ? '正确 ✓' : '错误 ✗'
  return q.answer
})
</script>

<template>
  <div class="card question-card">
    <div class="q-head">
      <van-tag round :color="typeColor">{{ TYPE_LABEL[question.questionType] }}</van-tag>
      <!-- 次要灰走间接层：移动端仍是 #8a94a6，桌面端由断点内的 :root 换成中性 #999 -->
      <van-tag v-if="question.difficulty" round plain color="var(--hf-text-light)">难度 {{ question.difficulty }}</van-tag>
      <van-tag v-if="question.sourceType" round plain :color="sourceColor">
        {{ question.sourceDetail || SOURCE_LABEL[question.sourceType] || question.sourceType }}
      </van-tag>
    </div>

    <MarkdownPreview class="q-stem" :content="question.stem" />

    <div v-if="question.options && question.options.length" class="q-options">
      <div v-for="o in question.options" :key="o.key" class="opt">
        <span class="opt-key" :class="{ right: revealed && answerKeys.includes(o.key) }">{{ o.key }}</span>
        <span class="opt-text">{{ o.text }}</span>
      </div>
    </div>

    <template v-if="revealed">
      <div class="q-line">
        <span class="lb">答案</span>
        <span class="val answer">{{ answerText }}</span>
      </div>
      <div v-if="question.analysis" class="q-line analysis">
        <span class="lb">解析</span>
        <MarkdownPreview class="analysis-body" :content="question.analysis" />
      </div>
      <div class="q-actions">
        <slot name="actions">
          <van-button v-if="showMarkWrong" size="small" round plain type="danger" @click.stop="emit('add-wrong')">
            我答错了
          </van-button>
        </slot>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.question-card {
  .q-head {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;
  }

  .q-stem {
    font-size: 15px;
  }

  .q-options {
    margin-top: 10px;
    display: flex;
    flex-direction: column;
    gap: 8px;

    .opt {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      font-size: 14px;

      .opt-key {
        flex-shrink: 0;
        width: 22px;
        height: 22px;
        line-height: 22px;
        text-align: center;
        border-radius: 50%;
        background: $bg-page;
        color: $text-light;
        font-size: 12px;

        &.right {
          background: #e8f8ef;
          color: $success; // 桌面端由 --hf-success 换成降饱和 #5a9e7f
          font-weight: 700;
        }
      }
    }
  }

  .q-line {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px dashed $bg-page;
    font-size: 14px;

    .lb {
      color: $text-light;
      margin-right: 8px;
    }

    .answer {
      color: $success; // 桌面端由 --hf-success 换成降饱和 #5a9e7f
      font-weight: 700;
    }

    &.analysis .analysis-body {
      display: inline;
      color: $text-main;
    }
  }

  .q-actions {
    margin-top: 12px;
    display: flex;
    gap: 10px;
  }
}
</style>
