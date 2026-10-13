<script setup lang="ts">
import { computed, ref } from 'vue'
import MarkdownPreview from '@/components/study/MarkdownPreview.vue'
import { useIsDesktop } from '@/composables/useDesktop'
import type { GradeResult, PracticeQuestion } from '@/types/question'
import { TYPE_LABEL, TYPE_COLOR, TYPE_COLOR_DESKTOP } from '@/constants/question'

/**
 * 刷题作答卡（新组件，无移动端历史基线）：
 * 题目侧只拿得到 PracticeQuestion（后端列裁剪，answer 根本不出库），
 * 对错/标准答案/解析都由判分结果 result 驱动，判完才渲染。
 * 布局沿用 QuestionCard 的卡片语言，选项行从静态变可点。
 */
const props = defineProps<{
  question: PracticeQuestion
  /** null = 未提交；非 null = 已判分（回显态） */
  result: GradeResult | null
  submitting: boolean
}>()

const emit = defineEmits<{ (e: 'submit', answer: string): void }>()

const isDesktop = useIsDesktop()
const typeColor = computed(() =>
  isDesktop.value ? TYPE_COLOR_DESKTOP[props.question.questionType] : TYPE_COLOR[props.question.questionType]
)

const pickedSingle = ref('')
const pickedMulti = ref<string[]>([])
const shortText = ref('')

/** 当前草稿：与后端存储格式同构（单选 "A"、多选升序逗号、判断 "T"/"F"、主观自由文本） */
const draft = computed(() => {
  switch (props.question.questionType) {
    case 'SINGLE':
      return pickedSingle.value
    case 'MULTI':
      return [...pickedMulti.value].sort().join(',')
    case 'JUDGE':
      return '' // JUDGE 直接点 正确/错误 提交，无草稿态
    default:
      return shortText.value.trim()
  }
})

const canSubmit = computed(() => !!draft.value)

function toggleOption(key: string) {
  if (props.result) return // 判完锁定，不许改答案重提交刷分（重提交会重复计入 wrong_count，由日限兜底）
  if (props.question.questionType === 'SINGLE') {
    pickedSingle.value = key
  } else if (props.question.questionType === 'MULTI') {
    const i = pickedMulti.value.indexOf(key)
    if (i >= 0) pickedMulti.value.splice(i, 1)
    else pickedMulti.value.push(key)
  }
}

function submit() {
  if (props.result || props.submitting || !canSubmit.value) return
  emit('submit', draft.value)
}

// ============ 判分回显 ============

const correctKeys = computed(() => {
  const r = props.result
  if (!r || (r.questionType !== 'SINGLE' && r.questionType !== 'MULTI')) return []
  return r.standardAnswer
    .split(',')
    .map((s) => s.trim().toUpperCase())
    .filter(Boolean)
})

/** 用户作答命中的 key 集合（SINGLE/MULTI） */
const pickedKeys = computed(() => {
  if (props.question.questionType === 'SINGLE') return pickedSingle.value ? [pickedSingle.value] : []
  if (props.question.questionType === 'MULTI') return pickedMulti.value
  return []
})

function optClass(key: string) {
  if (!props.result) return { picked: pickedKeys.value.includes(key) }
  return {
    picked: pickedKeys.value.includes(key),
    right: correctKeys.value.includes(key),
    wrongPick: pickedKeys.value.includes(key) && !correctKeys.value.includes(key)
  }
}

/** JUDGE 答案文案转换（照 QuestionCard:39 的口径） */
function judgeText(raw: string) {
  const t = raw.trim().toUpperCase()
  return t === 'T' ? '正确 ✓' : t === 'F' ? '错误 ✗' : raw
}

const isShort = computed(() => props.question.questionType === 'SHORT')
</script>

<template>
  <div class="card practice-card">
    <div class="q-head">
      <van-tag round :color="typeColor">{{ TYPE_LABEL[question.questionType] }}</van-tag>
      <van-tag v-if="question.difficulty" round plain color="var(--hf-text-light)">难度 {{ question.difficulty }}</van-tag>
    </div>

    <MarkdownPreview class="q-stem" :content="question.stem" />

    <!-- 选项（SINGLE/MULTI）：行可点选 -->
    <div v-if="question.options && question.options.length" class="q-options">
      <div
        v-for="o in question.options"
        :key="o.key"
        class="opt"
        :class="[optClass(o.key), { locked: !!result }]"
        @click="toggleOption(o.key)"
      >
        <span class="opt-key">{{ o.key }}</span>
        <span class="opt-text">{{ o.text }}</span>
        <van-icon v-if="result && o.key.toUpperCase() === result.standardAnswer.trim().toUpperCase() && question.questionType === 'SINGLE'" name="success" class="opt-mark ok" />
      </div>
    </div>

    <!-- 判断：两个大按钮即作答 -->
    <div v-if="question.questionType === 'JUDGE' && !result" class="judge-row">
      <van-button round block plain type="success" :disabled="submitting" @click="emit('submit', 'T')">正确</van-button>
      <van-button round block plain type="danger" :disabled="submitting" @click="emit('submit', 'F')">错误</van-button>
    </div>

    <!-- 主观：文本域（判分走 AI 要点覆盖, 见 QuizSetup 勾选说明） -->
    <template v-if="isShort && !result">
      <van-field
        v-model="shortText"
        type="textarea"
        rows="4"
        autosize
        maxlength="2000"
        placeholder="写下你的作答（AI 按要点判分，覆盖标准答案 50% 即为对）"
        class="short-area"
      />
    </template>

    <!-- 提交 -->
    <div v-if="!result" class="submit-row">
      <van-button
        v-if="!isShort"
        block
        round
        type="primary"
        color="#ff7a00"
        :disabled="!canSubmit"
        :loading="submitting"
        @click="submit"
      >
        提交答案
      </van-button>
      <van-button v-else block round type="primary" color="#ff7a00" :disabled="!canSubmit" :loading="submitting" @click="submit">
        {{ submitting ? 'AI 判卷中…' : '提交给 AI 判卷' }}
      </van-button>
    </div>

    <!-- 判分回显 -->
    <template v-if="result">
      <div class="verdict" :class="{ ok: result.correct, bad: !result.correct }">
        {{ result.correct ? '答对了' : '答错了' }}
        <span v-if="result.aiGrade" class="ratio">要点覆盖 {{ result.aiGrade.covered }}/{{ result.aiGrade.total }}</span>
      </div>

      <!-- SHORT 要点清单（AI 只判 hit，对错是服务端按阈值算的，见计划决策 6） -->
      <div v-if="result.aiGrade" class="ai-points">
        <div v-for="(p, i) in result.aiGrade.points" :key="i" class="ai-point" :class="{ hit: p.hit }">
          <van-icon :name="p.hit ? 'checked' : 'close'" :color="p.hit ? 'var(--hf-success)' : 'var(--hf-danger)'" />
          <span>{{ p.point }}</span>
        </div>
      </div>

      <div class="q-line">
        <span class="lb">标准答案</span>
        <span class="val answer">{{ result.questionType === 'JUDGE' ? judgeText(result.standardAnswer) : result.standardAnswer }}</span>
      </div>
      <div v-if="result.analysis" class="q-line analysis">
        <span class="lb">解析</span>
        <MarkdownPreview class="analysis-body" :content="result.analysis" />
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.practice-card {
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
      padding: 6px 8px;
      margin: 0 -8px;
      border-radius: $radius-sm;
      cursor: pointer;

      &.locked {
        cursor: default;
      }

      &.picked {
        background: rgba(255, 122, 0, 0.08);
      }

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
      }

      &.right .opt-key {
        background: #e8f8ef;
        color: $success;
        font-weight: 700;
      }

      &.wrongPick .opt-key {
        background: #fdecec;
        color: $danger;
        font-weight: 700;
      }

      .opt-mark {
        margin-left: auto;
        align-self: center;

        &.ok {
          color: $success;
        }
      }
    }
  }

  .judge-row {
    display: flex;
    gap: 12px;
    margin-top: 14px;
  }

  .short-area {
    margin-top: 10px;
    border: 1px solid $border-color;
    border-radius: $radius-sm;
  }

  .submit-row {
    margin-top: 14px;
  }

  .verdict {
    margin-top: 14px;
    padding-top: 12px;
    border-top: 1px dashed $bg-page;
    font-size: 15px;
    font-weight: 700;

    &.ok {
      color: $success;
    }

    &.bad {
      color: $danger;
    }

    .ratio {
      margin-left: 8px;
      font-size: 12px;
      font-weight: 400;
      color: $text-light;
    }
  }

  .ai-points {
    margin-top: 8px;
    display: flex;
    flex-direction: column;
    gap: 4px;

    .ai-point {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: $text-sub;

      &.hit {
        color: $text-main;
      }
    }
  }

  .q-line {
    margin-top: 10px;
    font-size: 14px;

    .lb {
      color: $text-light;
      margin-right: 8px;
    }

    .answer {
      color: $success;
      font-weight: 700;
    }

    &.analysis .analysis-body {
      display: inline;
      color: $text-main;
    }
  }
}
</style>
