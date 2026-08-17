<script setup lang="ts">
import { ref, watch } from 'vue'
import { showSuccessToast } from 'vant'
import type { Habit } from '@/types/habit'
import type { Reflection } from '@/types/journal'
import { apiTodayJournal, apiCreateJournal, apiCreateReflection, apiUpdateReflection } from '@/api'

const props = defineProps<{
  show: boolean
  habit: Habit | null
  /** 已有心得则进入编辑模式 */
  reflection: Reflection | null
}>()

const emit = defineEmits<{
  (e: 'update:show', v: boolean): void
  (e: 'saved'): void
}>()

const result = ref(1)
const feeling = ref<number | undefined>(undefined)
const difficulty = ref<number>(0)
const reason = ref('')
const obstacle = ref('')
const learning = ref('')
const adjustment = ref('')
const saving = ref(false)

const feelings = [
  { v: 1, emoji: '😊', label: '不错' },
  { v: 2, emoji: '😐', label: '一般' },
  { v: 3, emoji: '😫', label: '糟糕' }
]

// 打开时初始化表单
watch(
  () => props.show,
  (v) => {
    if (!v) return
    const r = props.reflection
    if (r) {
      result.value = r.result
      feeling.value = r.feeling
      difficulty.value = r.difficulty || 0
      reason.value = r.reason || ''
      obstacle.value = r.obstacle || ''
      learning.value = r.learning || ''
      adjustment.value = r.adjustment || ''
    } else {
      result.value = props.habit?.checkedToday ? 1 : 0
      feeling.value = undefined
      difficulty.value = 0
      reason.value = ''
      obstacle.value = ''
      learning.value = ''
      adjustment.value = ''
    }
  }
)

/** 确保今日日记存在并返回其 ID */
async function ensureTodayJournal(): Promise<string> {
  const existing = await apiTodayJournal()
  if (existing) return existing.id
  const created = await apiCreateJournal({}) // habitIds 缺省 = 自动关联当日已打卡习惯
  return created.id
}

async function onSave() {
  if (!props.habit || saving.value) return
  saving.value = true
  try {
    const payload = {
      result: result.value,
      feeling: feeling.value,
      difficulty: difficulty.value || undefined,
      reason: reason.value.trim() || undefined,
      obstacle: obstacle.value.trim() || undefined,
      learning: learning.value.trim() || undefined,
      adjustment: adjustment.value.trim() || undefined
    }
    if (props.reflection) {
      await apiUpdateReflection(props.reflection.id, payload)
    } else {
      const journalId = await ensureTodayJournal()
      await apiCreateReflection({ journalId, habitId: props.habit.id, ...payload })
    }
    showSuccessToast('心得已记录 ✍️')
    emit('update:show', false)
    emit('saved')
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <van-popup :show="props.show" position="bottom" round :style="{ maxHeight: '90%' }" @update:show="emit('update:show', $event)">
    <div class="editor">
      <div class="editor__title">{{ reflection ? '编辑心得' : '记录心得' }} · {{ habit?.name }}</div>

      <!-- 完成情况（唯一必填） -->
      <div class="field">
        <div class="label">完成情况 <span class="req">*</span></div>
        <div class="seg">
          <div class="seg-item" :class="{ active: result === 1, done: result === 1 }" @click="result = 1">✅ 完成</div>
          <div class="seg-item" :class="{ active: result === 0, miss: result === 0 }" @click="result = 0">❌ 未完成</div>
        </div>
      </div>

      <!-- 今日感觉 -->
      <div class="field">
        <div class="label">今天感觉</div>
        <div class="seg">
          <div
            v-for="f in feelings"
            :key="f.v"
            class="seg-item emoji-item"
            :class="{ active: feeling === f.v }"
            @click="feeling = feeling === f.v ? undefined : f.v"
          >
            <span class="e">{{ f.emoji }}</span>{{ f.label }}
          </div>
        </div>
      </div>

      <!-- 难度 -->
      <div class="field">
        <div class="label">今天这件事有多难</div>
        <van-rate v-model="difficulty" :size="22" color="#ff7a00" void-icon="star" icon="star" />
      </div>

      <!-- 原因 -->
      <div class="field">
        <div class="label">{{ result === 1 ? '为什么今天能做到？' : '为什么今天没做到？' }}</div>
        <van-field v-model="reason" type="textarea" rows="2" autosize maxlength="500" show-word-limit placeholder="想想背后的原因…" />
      </div>

      <!-- 困难 -->
      <div class="field">
        <div class="label">遇到了什么困难？</div>
        <van-field v-model="obstacle" type="textarea" rows="2" autosize maxlength="500" show-word-limit placeholder="环境/状态/干扰…" />
      </div>

      <!-- 收获 -->
      <div class="field">
        <div class="label">今天学到了什么？</div>
        <van-field v-model="learning" type="textarea" rows="2" autosize maxlength="500" show-word-limit placeholder="一点点发现也是收获" />
      </div>

      <!-- 调整 -->
      <div class="field">
        <div class="label">明天准备怎么调整？</div>
        <van-field v-model="adjustment" type="textarea" rows="2" autosize maxlength="500" show-word-limit placeholder="让明天更容易的小改变" />
      </div>

      <van-button block type="primary" color="#ff7a00" round :loading="saving" @click="onSave">
        {{ reflection ? '保存修改' : '记入今日记录' }}
      </van-button>
    </div>
  </van-popup>
</template>

<style scoped lang="scss">
.editor {
  padding: 20px 16px calc(20px + env(safe-area-inset-bottom));
  max-height: 90vh;
  overflow-y: auto;

  &__title {
    font-size: 16px;
    font-weight: 700;
    margin-bottom: 16px;
  }
}

.field {
  margin-bottom: 14px;

  .label {
    font-size: 13px;
    font-weight: 600;
    color: $text-main;
    margin-bottom: 8px;

    .req {
      color: $danger;
    }
  }

  :deep(.van-cell) {
    border-radius: 10px;
    background: #f6f7fb;
    padding: 10px 12px;
  }
}

.seg {
  display: flex;
  gap: 8px;

  .seg-item {
    flex: 1;
    text-align: center;
    padding: 10px 0;
    border-radius: 10px;
    background: #f6f7fb;
    font-size: 13px;
    color: $text-light;
    transition: all 0.15s ease;
    cursor: pointer;

    &.active.done {
      background: rgba(39, 174, 96, 0.12);
      color: $success;
      font-weight: 700;
    }

    &.active.miss {
      background: rgba(231, 76, 60, 0.1);
      color: $danger;
      font-weight: 700;
    }

    &.emoji-item.active {
      background: rgba(255, 122, 0, 0.1);
      color: $primary;
      font-weight: 700;
    }

    .e {
      margin-right: 4px;
    }
  }
}
</style>
