<script setup lang="ts">
import { ref, watch } from 'vue'
import type { HabitCreatePayload, Category, FrequencyType } from '@/types/habit'
import { usePopupPosition } from '@/composables/useDesktop'

const popupPosition = usePopupPosition()
const props = defineProps<{
  modelValue: HabitCreatePayload
  submitText: string
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: HabitCreatePayload): void
  (e: 'submit', v: HabitCreatePayload): void
  (e: 'cancel'): void
}>()

const form = ref<HabitCreatePayload>({ ...props.modelValue })
watch(
  () => props.modelValue,
  (v) => (form.value = { ...v })
)

const categories: { value: Category; label: string }[] = [
  { value: 'HEALTH', label: '健康' },
  { value: 'LEARNING', label: '学习' },
  { value: 'WORK', label: '工作' },
  { value: 'LIFE', label: '生活' },
  { value: 'OTHER', label: '其他' }
]

const freqOptions: { value: FrequencyType; label: string }[] = [
  { value: 'DAILY', label: '每天' },
  { value: 'WEEKLY_DAYS', label: '每周指定几天' },
  { value: 'WEEKLY_COUNT', label: '每周 N 次' }
]

const weekDays: { value: string; label: string }[] = [
  { value: '1', label: '一' },
  { value: '2', label: '二' },
  { value: '3', label: '三' },
  { value: '4', label: '四' },
  { value: '5', label: '五' },
  { value: '6', label: '六' },
  { value: '7', label: '日' }
]

const selectedDays = ref<string[]>((props.modelValue.frequencyDays || '').split(',').filter(Boolean))
const showTimePicker = ref(false)
const execTimeValue = ref(props.modelValue.execTime || '07:00')

function sync() {
  emit('update:modelValue', { ...form.value })
}

function onFreqChange() {
  if (form.value.frequencyType !== 'WEEKLY_DAYS') {
    selectedDays.value = []
    form.value.frequencyDays = undefined
  }
  if (form.value.frequencyType !== 'WEEKLY_COUNT') {
    form.value.frequencyTarget = undefined
  }
  sync()
}

function toggleDay(d: string) {
  const i = selectedDays.value.indexOf(d)
  if (i >= 0) selectedDays.value.splice(i, 1)
  else selectedDays.value.push(d)
  selectedDays.value.sort()
  form.value.frequencyDays = selectedDays.value.length ? selectedDays.value.join(',') : undefined
  sync()
}

function onTimeConfirm({ selectedValues }: { selectedValues: string[] }) {
  execTimeValue.value = selectedValues.join(':')
  form.value.execTime = execTimeValue.value
  showTimePicker.value = false
  sync()
}
</script>

<template>
  <!-- is-form：桌面端把表单收窄到 760px 居中；移动端这个类没有任何声明 -->
  <van-form class="is-form" @submit="emit('submit', { ...form })">
    <van-cell-group inset title="基本信息">
      <van-field
        v-model="form.name"
        label="习惯名称"
        placeholder="如：每日阅读"
        required
        :rules="[{ required: true, message: '请输入习惯名称' }]"
        @blur="sync()"
      />
      <van-field
        v-model="form.identityTag"
        label="身份标签"
        placeholder="我想成为…（如：读者）"
        @blur="sync()"
      />
      <div class="field-row">
        <div class="field-label">分类</div>
        <div class="chips">
          <span
            v-for="c in categories"
            :key="c.value"
            class="chip"
            :class="{ active: form.category === c.value }"
            @click="form.category = c.value; sync()"
          >
            {{ c.label }}
          </span>
        </div>
      </div>
    </van-cell-group>

    <van-cell-group inset title="让它显而易见（第一定律）">
      <van-field readonly clickable label="执行时间" :model-value="form.execTime" placeholder="点击选择" @click="showTimePicker = true" />
      <van-field
        v-model="form.execPlace"
        label="执行地点"
        placeholder="如：书房"
        @blur="sync()"
      />
      <van-field
        v-model="form.stackAfter"
        label="习惯叠加"
        placeholder="如：继我晚上洗漱之后"
        @blur="sync()"
      />
    </van-cell-group>

    <van-cell-group inset title="让它简便易行（第三定律）">
      <van-field
        v-model="form.twoMinuteVersion"
        label="两分钟版本"
        placeholder="如：读一页书"
        @blur="sync()"
      />
      <div class="tip-inline">💡 从微习惯开始，让新习惯简便易行！</div>
    </van-cell-group>

    <van-cell-group inset title="打卡频率">
      <div class="field-row">
        <div class="field-label">重复</div>
        <div class="chips">
          <span
            v-for="f in freqOptions"
            :key="f.value"
            class="chip"
            :class="{ active: (form.frequencyType || 'DAILY') === f.value }"
            @click="form.frequencyType = f.value; onFreqChange()"
          >
            {{ f.label }}
          </span>
        </div>
      </div>

      <div v-if="form.frequencyType === 'WEEKLY_DAYS'" class="field-row">
        <div class="field-label">星期</div>
        <div class="chips">
          <span
            v-for="d in weekDays"
            :key="d.value"
            class="chip small"
            :class="{ active: selectedDays.includes(d.value) }"
            @click="toggleDay(d.value)"
          >
            {{ d.label }}
          </span>
        </div>
      </div>

      <van-field
        v-if="form.frequencyType === 'WEEKLY_COUNT'"
        v-model.number="form.frequencyTarget"
        type="digit"
        label="每周次数"
        placeholder="如：3"
        @blur="sync()"
      />
    </van-cell-group>

    <div class="form-actions">
      <van-button round block plain @click="emit('cancel')">取消</van-button>
      <van-button round block type="primary" native-type="submit" color="#ff7a00" :loading="loading">
        {{ submitText }}
      </van-button>
    </div>
  </van-form>

  <van-popup class="hf-popup" v-model:show="showTimePicker" :position="popupPosition" round>
    <van-time-picker
      :model-value="execTimeValue.split(':')"
      title="选择执行时间"
      @confirm="onTimeConfirm"
      @cancel="showTimePicker = false"
    />
  </van-popup>
</template>

<style scoped lang="scss">
.field-row {
  display: flex;
  align-items: flex-start;
  padding: 12px 16px;
  background: #fff;

  .field-label {
    width: 72px;
    flex-shrink: 0;
    color: #646566;
    font-size: 14px;
    padding-top: 4px;
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    flex: 1;
  }
}

.chip {
  padding: 6px 14px;
  border-radius: 999px;
  background: #f2f3f5;
  font-size: 13px;
  color: #5d6d7e;
  transition: all 0.15s ease;
  user-select: none;

  &.small {
    padding: 6px 12px;
  }

  &.active {
    background: #fff1e5;
    color: $primary;
    font-weight: 600;
    border: 1px solid $primary;
    padding: 5px 13px;
  }

  &.small.active {
    padding: 5px 11px;
  }
}

.tip-inline {
  padding: 8px 16px 14px;
  font-size: 12px;
  color: $text-light;
  background: #fff;
}

.form-actions {
  display: flex;
  gap: 12px;
  padding: 24px 16px 40px;

  > :first-child {
    flex: 1;
  }

  > :last-child {
    flex: 2;
  }
}
</style>
