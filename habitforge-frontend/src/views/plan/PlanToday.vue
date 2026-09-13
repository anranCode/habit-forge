<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  showConfirmDialog,
  showLoadingToast,
  showToast,
  showSuccessToast,
  type PickerOption
} from 'vant'
import dayjs from 'dayjs'
import type { DailyPlan, PlanUsage, BlockType } from '@/types/plan'
import type { Habit } from '@/types/habit'
import {
  apiPlanToday,
  apiPlanByDate,
  apiPlanUsage,
  apiGeneratePlan,
  apiAdoptPlan,
  apiCreateBlock,
  apiCompleteBlock,
  apiSkipBlock,
  apiReopenBlock,
  apiTodayHabits
} from '@/api'
import { todayStr, weekdayCn } from '@/utils/date'
import BlockList from '@/components/plan/BlockList.vue'
import FreeSlotEditor from '@/components/plan/FreeSlotEditor.vue'
import { usePopupPosition } from '@/composables/useDesktop'

const popupPosition = usePopupPosition()
defineOptions({ name: 'PlanToday' })

const router = useRouter()

// ============ 日期与数据（页面级拉数，无新 store；isPast = 历史只读） ============
const date = ref(todayStr())
const isPast = computed(() => date.value !== todayStr())
const plan = ref<DailyPlan | null>(null)
const usage = ref<PlanUsage | null>(null)
const loading = ref(false)

const dateTitle = computed(() => {
  const d = dayjs(date.value)
  const tag = isPast.value ? (date.value === dayjs().subtract(1, 'day').format('YYYY-MM-DD') ? '昨天' : '历史') : '今天'
  return `${tag} · ${d.format('M月D日')} ${weekdayCn(d)}`
})

/** 右箭头切昨日及以前；左箭头回到今天方向 */
function shiftDate(delta: number) {
  const d = dayjs(date.value).add(delta, 'day')
  if (d.isAfter(dayjs(), 'day')) return
  date.value = d.format('YYYY-MM-DD')
  load()
}

async function load() {
  loading.value = true
  try {
    plan.value = isPast.value ? await apiPlanByDate(date.value) : await apiPlanToday()
  } catch {
    /* 历史无计划（6011）等错误已由拦截器提示，视为无数据 */
    plan.value = null
  } finally {
    loading.value = false
  }
  if (isPast.value) {
    usage.value = null
    return
  }
  try {
    usage.value = await apiPlanUsage()
  } catch {
    usage.value = null
  }
}

onMounted(load)

// ============ 空闲时段 ============
const showFreeSlot = ref(false)
const slotCount = computed(() => plan.value?.freeSlots.length || 0)

// ============ 生成 ============
const generating = ref(false)
/** 生成失败提示（拦截器已 toast，这里在按钮下方保留一行可重试的兜底提示） */
const genError = ref('')

/** 失败消息：HTTP 错误取响应体 message，业务错误取 Error.message，兜底通用文案 */
function errMsg(e: unknown): string {
  const resp = (e as { response?: { data?: { message?: string } } })?.response
  return resp?.data?.message || (e as Error)?.message || '生成失败，请稍后重试'
}

async function onGenerate() {
  if (generating.value) return
  if (!slotCount.value) {
    showToast('请先设置空闲时段')
    showFreeSlot.value = true
    return
  }
  if (plan.value) {
    try {
      await showConfirmDialog({
        title: '重新生成今日安排',
        message: '将替换未采纳的建议块，已采纳/已完成/已跳过的块不受影响。'
      })
    } catch {
      return /* 取消 */
    }
  }
  // 同步阻塞调 LLM（接口 timeout 120s），全屏 loading。
  // 不加 forbidClick：Vant 的 toast 是全应用单例，forbidClick 会给 body 挂
  // .van-toast--unclickable 并让整棵页面失去命中测试（实测 elementFromPoint 在按钮上返回
  // BODY），生成期间最长 120s 内整个 App 点不动——而这里本来就有 generating 在防重复提交。
  // 也不要在 finally 里关它：showSuccessToast 复用的是同一条单例，finally 的 close() 会把
  // 刚弹出来的"今日安排已生成"一起关掉。改为在弹成功提示之前显式关掉 loading。
  const toast = showLoadingToast({ message: 'AI 正在规划…', duration: 0 })
  generating.value = true
  genError.value = ''
  try {
    await apiGeneratePlan()
    toast.close()
    showSuccessToast('今日安排已生成')
    await load()
  } catch (e) {
    // toast 是全应用单例：axios 拦截器在请求失败时刚用它弹过错误提示（request.ts 的 showToast），
    // 此刻 close() 关掉的正是那一条——两步落在同一轮微任务里，浏览器不会在中间重绘，
    // 于是用户根本看不到错误提示（实测 s1：close() 后 visible 直接变 false）。
    // 所以先收掉 loading，再用同一条文案把错误提示重新弹一次，避免"点了没反应"。
    toast.close()
    const msg = errMsg(e)
    showToast(msg)
    /* 按钮下方再留一行可点重试的失败提示（toast 会消失，这行不会） */
    genError.value = msg
  } finally {
    generating.value = false
  }
}

// ============ 进度 / 采纳 / 块状态机 ============
const activeBlocks = computed(() => (plan.value?.blocks || []).filter((b) => b.status !== 'SKIPPED'))
const doneCount = computed(() => activeBlocks.value.filter((b) => b.status === 'DONE').length)
const progress = computed(() =>
  activeBlocks.value.length ? Math.round((doneCount.value / activeBlocks.value.length) * 100) : 0
)
const hasProposed = computed(() => (plan.value?.blocks || []).some((b) => b.status === 'PROPOSED'))

async function onAdoptAll() {
  try {
    const n = await apiAdoptPlan()
    showSuccessToast(`已采纳 ${n} 个建议块`)
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

async function onComplete(id: string, checkinHabit: boolean) {
  try {
    await apiCompleteBlock(id, checkinHabit)
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

async function onSkip(id: string) {
  try {
    await apiSkipBlock(id)
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

async function onReopen(id: string) {
  try {
    await apiReopenBlock(id)
    await load()
  } catch {
    /* 拦截器已提示（SKIPPED 恢复的合法性由后端状态机判定） */
  }
}

// ============ 手动添加块（仅今天可操作） ============
const TYPE_CHIPS: { v: BlockType; label: string }[] = [
  { v: 'HABIT', label: '⏰ 习惯' },
  { v: 'STUDY', label: '📖 学习' },
  { v: 'REST', label: '☕ 休息' },
  { v: 'OTHER', label: '📌 其他' }
]

const showAdd = ref(false)
const adding = ref(false)
const addForm = reactive({
  blockType: 'HABIT' as BlockType,
  title: '',
  startTime: '09:00',
  endTime: '10:00',
  habitId: ''
})

const habits = ref<Habit[]>([])
const habitsLoaded = ref(false)

async function openAdd() {
  // 先取数据再开弹层：习惯列表是异步来的，先开弹层会让"关联习惯"下拉在数据到达前
  // 渲染成空的 van-picker —— 用户看到的就是一个没有选项的空白弹窗。
  if (!habitsLoaded.value) {
    try {
      habits.value = await apiTodayHabits()
      habitsLoaded.value = true
    } catch {
      /* 拦截器已提示，下拉留空 */
    }
  }
  showAdd.value = true
}

// 习惯下拉（单列 Picker）
const showHabitPicker = ref(false)
const habitPickerVal = ref<(string | number)[]>([])
const habitColumns = computed<PickerOption[]>(() => habits.value.map((h) => ({ text: h.name, value: h.id })))
const habitName = computed(() => habits.value.find((h) => h.id === addForm.habitId)?.name || '')

/** 无候选时不打开：van-picker 空 columns 会渲染成一个没有选项的空白弹窗 */
function openHabitPicker() {
  if (!habitColumns.value.length) {
    showToast('今天还没有习惯，先在首页创建')
    return
  }
  showHabitPicker.value = true
}

function confirmHabit() {
  const v = habitPickerVal.value[0]
  if (v !== undefined) addForm.habitId = String(v)
  showHabitPicker.value = false
}

// 起止时间编辑
const showTimePicker = ref(false)
const timePickerVal = ref<string[]>(['09', '00'])
const timeField = ref<'startTime' | 'endTime'>('startTime')

function openTimePicker(field: 'startTime' | 'endTime') {
  timeField.value = field
  const [h, m] = addForm[field].split(':')
  timePickerVal.value = [h, m]
  showTimePicker.value = true
}

function confirmTime() {
  addForm[timeField.value] = `${timePickerVal.value[0]}:${timePickerVal.value[1]}`
  showTimePicker.value = false
}

function pickType(t: BlockType) {
  addForm.blockType = t
  if (t !== 'HABIT') addForm.habitId = ''
}

async function onAdd() {
  if (adding.value) return
  const title = addForm.title.trim()
  if (!title) {
    showToast('请填写标题')
    return
  }
  if (addForm.startTime >= addForm.endTime) {
    showToast('开始时间需早于结束时间')
    return
  }
  adding.value = true
  try {
    await apiCreateBlock({
      date: todayStr(),
      blockType: addForm.blockType,
      title: title.slice(0, 100),
      startTime: addForm.startTime,
      endTime: addForm.endTime,
      habitId: addForm.blockType === 'HABIT' && addForm.habitId ? addForm.habitId : undefined
    })
    showSuccessToast('已添加（视为已采纳）')
    showAdd.value = false
    addForm.title = ''
    addForm.habitId = ''
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    adding.value = false
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="今日安排" left-arrow @click-left="router.back()" />

    <div class="page-body">
      <!-- 日期切换：右箭头往历史走，历史只读 -->
      <div class="date-bar card">
        <van-icon name="arrow-left" :class="{ dim: !isPast }" @click="shiftDate(1)" />
        <span class="date-title">{{ dateTitle }}</span>
        <van-icon name="arrow" @click="shiftDate(-1)" />
      </div>

      <template v-if="loading">
        <div class="loading-box"><van-loading color="#ff7a00" /></div>
      </template>

      <template v-else>
        <!-- 今日剩余生成次数 -->
        <div v-if="!isPast && usage" class="usage-line text-light">
          今日剩余 {{ usage.remaining }}/{{ usage.used + usage.remaining }} 次 · 已耗 {{ usage.todayTokens }} tokens
        </div>

        <!-- 空闲时段摘要行 -->
        <div class="slot-bar card" :class="{ readonly: isPast }" @click="!isPast && (showFreeSlot = true)">
          <span>🕒 空闲时段 <b>{{ slotCount }}</b> 个</span>
          <span v-if="!isPast" class="text-light edit">设置 ›</span>
        </div>

        <!-- 生成/重新生成（历史只读隐藏） -->
        <van-button
          v-if="!isPast"
          block
          type="primary"
          color="#ff7a00"
          round
          class="gen-btn"
          :loading="generating"
          loading-text="AI 正在规划…"
          @click="onGenerate"
        >
          {{ plan ? '🤖 重新生成' : '🤖 AI 生成今日安排' }}
        </van-button>
        <div v-if="!isPast && !plan" class="gen-hint text-light">先录空闲时段，AI 综合日记/习惯/学习进度安排今天</div>
        <!-- 生成失败：按钮下方一行带「重试」的提示（手动加块入口仍在下方兜底） -->
        <div v-if="!isPast && genError" class="gen-error">
          <span class="msg">{{ genError }}</span>
          <span class="retry" @click="onGenerate">重试</span>
        </div>

        <!-- 进度条 -->
        <div v-if="plan && activeBlocks.length" class="card progress-card">
          <div class="flex-between">
            <span style="font-weight: 700">今日安排</span>
            <span class="text-light">{{ doneCount }}/{{ activeBlocks.length }} · {{ progress }}%</span>
          </div>
          <van-progress :percentage="progress" color="#ff7a00" :show-pivot="false" style="margin-top: 10px" />
          <div v-if="hasProposed && !isPast" class="adopt-line">
            <van-button size="small" round color="#ff7a00" class="adopt-btn" @click="onAdoptAll">
              ✅ 一键采纳全部建议（{{ plan.blocks.filter((b) => b.status === 'PROPOSED').length }}）
            </van-button>
          </div>
        </div>

        <!-- 块列表 -->
        <BlockList :blocks="plan?.blocks || []" :readonly="isPast" @complete="onComplete" @skip="onSkip" @reopen="onReopen" />

        <!-- 手动添加（历史只读隐藏） -->
        <div v-if="!isPast" class="add-entry" @click="openAdd">＋ 手动添加</div>
      </template>
    </div>

    <!-- 空闲时段编辑弹层 -->
    <FreeSlotEditor v-model:show="showFreeSlot" :slots="plan?.freeSlots || []" @saved="load" />

    <!-- 手动添加块弹层 -->
    <van-popup class="hf-popup" v-model:show="showAdd" :position="popupPosition" round :style="{ maxHeight: '85%' }">
      <div class="add-sheet">
        <div class="sheet-title">手动添加安排</div>
        <div class="chips">
          <div
            v-for="c in TYPE_CHIPS"
            :key="c.v"
            class="chip"
            :class="{ active: addForm.blockType === c.v }"
            @click="pickType(c.v)"
          >{{ c.label }}</div>
        </div>
        <van-field v-model="addForm.title" label="标题" maxlength="100" placeholder="做什么（建议 30 字内）" show-word-limit />
        <div class="time-row">
          <span class="time-val" @click="openTimePicker('startTime')">{{ addForm.startTime }}</span>
          <span class="text-light">–</span>
          <span class="time-val" @click="openTimePicker('endTime')">{{ addForm.endTime }}</span>
        </div>
        <van-field
          v-if="addForm.blockType === 'HABIT'"
          :model-value="habitName"
          label="关联习惯"
          readonly
          :is-link="habitColumns.length > 0"
          :placeholder="habitColumns.length ? '选一个今日习惯（可选）' : '今日没有可关联的习惯'"
          @click="openHabitPicker"
        />
        <van-button block type="primary" color="#ff7a00" round :loading="adding" @click="onAdd">添加</van-button>
      </div>
    </van-popup>

    <!-- 习惯选择弹层 -->
    <van-popup class="hf-popup" v-model:show="showHabitPicker" :position="popupPosition" round>
      <div class="picker-head">
        <span class="cancel" @click="showHabitPicker = false">取消</span>
        <span class="ok" @click="confirmHabit">确定</span>
      </div>
      <van-picker :columns="habitColumns" v-model="habitPickerVal" />
    </van-popup>

    <!-- 起止时间选择弹层 -->
    <van-popup class="hf-popup" v-model:show="showTimePicker" :position="popupPosition" round>
      <div class="picker-head">
        <span class="cancel" @click="showTimePicker = false">取消</span>
        <span class="ok" @click="confirmTime">确定</span>
      </div>
      <van-time-picker v-model="timePickerVal" title="选择时间" />
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
.date-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 10px;

  .date-title {
    font-size: 14px;
    font-weight: 700;
    color: $text-main;
  }

  .van-icon {
    font-size: 18px;
    color: $primary;
    cursor: pointer;
    padding: 4px;

    &.dim {
      color: #d5dbe3;
    }
  }
}

.loading-box {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.usage-line {
  font-size: 12px;
  margin-bottom: 8px;
  text-align: right;
}

.slot-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  font-size: 14px;
  margin-bottom: 10px;
  cursor: pointer;

  b {
    color: $primary;
  }

  .edit {
    font-size: 12px;
  }

  &.readonly {
    cursor: default;
  }
}

.gen-btn {
  height: 48px;
  font-size: 16px;
  font-weight: 700;
}

.gen-hint {
  font-size: 12px;
  text-align: center;
  margin: 8px 0 4px;
}

.gen-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
  margin: 8px 0 4px;
  color: $danger;

  .msg {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .retry {
    flex-shrink: 0;
    font-weight: 700;
    color: $primary;
    cursor: pointer;
    text-decoration: underline;
  }
}

.progress-card {
  margin-top: 14px;
}

.adopt-line {
  margin-top: 12px;
  text-align: right;

  .adopt-btn {
    color: #fff;
  }
}

.add-entry {
  margin: 14px 0 24px;
  padding: 12px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: $primary;
  border: 1px dashed rgba(255, 122, 0, 0.5);
  border-radius: 12px;
  cursor: pointer;

  &:active {
    background: rgba(255, 122, 0, 0.06);
  }
}

.add-sheet {
  padding: 20px 16px calc(20px + env(safe-area-inset-bottom));
  max-height: 85vh;
  overflow-y: auto;

  .sheet-title {
    font-size: 16px;
    font-weight: 700;
    margin-bottom: 14px;
  }

  .chips {
    display: flex;
    gap: 8px;
    margin-bottom: 14px;

    .chip {
      flex: 1;
      text-align: center;
      padding: 9px 0;
      border-radius: 10px;
      background: #f6f7fb;
      font-size: 13px;
      color: $text-light;
      cursor: pointer;

      &.active {
        background: rgba(255, 122, 0, 0.1);
        color: $primary;
        font-weight: 700;
      }
    }
  }

  :deep(.van-cell) {
    border-radius: 10px;
    background: #f6f7fb;
    margin-bottom: 12px;
  }

  .time-row {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-bottom: 14px;

    .time-val {
      font-size: 18px;
      font-weight: 700;
      color: $primary;
      padding: 6px 16px;
      border-radius: 10px;
      background: rgba(255, 122, 0, 0.08);
      cursor: pointer;
    }
  }

  .van-button {
    margin-top: 6px;
  }
}

.picker-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 0;
  font-size: 14px;

  .cancel {
    color: $text-light;
    cursor: pointer;
  }

  .ok {
    color: $primary;
    font-weight: 700;
    cursor: pointer;
  }
}
</style>
