<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { showSuccessToast, showToast, showConfirmDialog } from 'vant'
import dayjs from 'dayjs'
import type { Subject, StudySession } from '@/types/study'
import {
  apiActiveStudy,
  apiStartStudy,
  apiEndStudy,
  apiManualStudy,
  apiDeleteStudySession,
  apiStudyTimeSummary,
  apiStudySessions
} from '@/api'

const props = defineProps<{ subjects?: Subject[] }>()

/** 每日 30 分钟是学习维度的达标底线（与周报口径一致） */
const DAILY_GOAL_MINUTES = 30

const active = ref<StudySession | null>(null)
const sessions = ref<StudySession[]>([])
const todayMinutes = ref(0)
const busy = ref(false)
const elapsed = ref('00:00')
let ticker: ReturnType<typeof setInterval> | undefined

const showSubjectPicker = ref(false)
const subjectId = ref('')
const subjectColumns = computed(() => [
  { text: '不指定科目', value: '' },
  ...(props.subjects ?? []).map((s) => ({ text: s.name, value: s.id }))
])
const subjectName = computed(
  () => (props.subjects ?? []).find((s) => s.id === subjectId.value)?.name || '不指定科目'
)

const showManual = ref(false)
const manualMinutes = ref('30')
const manualNote = ref('')
const manualDate = ref(dayjs().format('YYYY-MM-DD'))
const showDatePicker = ref(false)
const manualPickerValue = ref(dayjs().format('YYYY-MM-DD').split('-'))
const maxDate = dayjs().format('YYYY-MM-DD')

onMounted(load)
onBeforeUnmount(stopTick)

async function load() {
  try {
    const [act, sum, list] = await Promise.all([
      apiActiveStudy(),
      apiStudyTimeSummary(),
      apiStudySessions()
    ])
    active.value = act
    todayMinutes.value = sum.minutes
    sessions.value = list
    if (act) startTick(act)
  } catch {
    /* 错误已由拦截器提示 */
  }
}

async function refresh() {
  const [sum, list] = await Promise.all([apiStudyTimeSummary(), apiStudySessions()])
  todayMinutes.value = sum.minutes
  sessions.value = list
}

// ============ 计时 ============

function onSubjectConfirm({ selectedOptions }: { selectedOptions: { value: string }[] }) {
  showSubjectPicker.value = false
  subjectId.value = selectedOptions[0]?.value ?? ''
}

async function onStart() {
  if (busy.value) return
  busy.value = true
  try {
    const s = await apiStartStudy({ subjectId: subjectId.value || undefined })
    active.value = s
    startTick(s)
    showSuccessToast('开始计时')
  } catch {
    /* 7010 已有进行中的计时等由拦截器提示 */
  } finally {
    busy.value = false
  }
}

async function onEnd() {
  if (!active.value || busy.value) return
  busy.value = true
  try {
    const done = await apiEndStudy(active.value.id)
    stopTick()
    active.value = null
    showSuccessToast(`本次 ${done.minutes} 分钟已记录`)
    await refresh()
  } catch {
    /* 7012（超 12 小时）由拦截器提示, 用户可放弃后手动补录 */
  } finally {
    busy.value = false
  }
}

async function onGiveUp() {
  if (!active.value) return
  try {
    await showConfirmDialog({ title: '放弃本次计时？', message: '这段时间不会被记录' })
  } catch {
    return
  }
  await apiDeleteStudySession(active.value.id).catch(() => undefined)
  stopTick()
  active.value = null
  await refresh().catch(() => undefined)
}

function startTick(s: StudySession) {
  stopTick()
  const update = () => {
    const sec = Math.max(0, dayjs().diff(dayjs(normalize(s.startedAt)), 'second'))
    const h = Math.floor(sec / 3600)
    const m = Math.floor((sec % 3600) / 60)
    const ss = sec % 60
    elapsed.value = (h > 0 ? `${h}:` : '') + `${String(m).padStart(2, '0')}:${String(ss).padStart(2, '0')}`
  }
  update()
  ticker = setInterval(update, 1000)
}

function stopTick() {
  if (ticker) {
    clearInterval(ticker)
    ticker = undefined
  }
}

/** 兼容 ISO（T 分隔）与 "yyyy-MM-dd HH:mm:ss" 两种序列化 */
function normalize(value: string) {
  return String(value).replace(' ', 'T')
}

function hhmm(value: string) {
  return dayjs(normalize(value)).format('HH:mm')
}

// ============ 手动补录 ============

function openManual() {
  manualMinutes.value = '30'
  manualNote.value = ''
  manualDate.value = dayjs().format('YYYY-MM-DD')
  manualPickerValue.value = manualDate.value.split('-')
  showManual.value = true
}

function onManualDateConfirm({ selectedValues }: { selectedValues: string[] }) {
  showDatePicker.value = false
  manualDate.value = selectedValues.join('-')
}

async function submitManual() {
  const minutes = Number(manualMinutes.value)
  if (!Number.isFinite(minutes) || minutes < 1) {
    showToast('请填写正确的时长（分钟）')
    return
  }
  if (minutes > 720) {
    showToast('单次最多 720 分钟')
    return
  }
  busy.value = true
  try {
    await apiManualStudy({
      sessionDate: manualDate.value,
      minutes,
      subjectId: subjectId.value || undefined,
      note: manualNote.value.trim() || undefined
    })
    showManual.value = false
    showSuccessToast('已补录')
    await refresh()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="card timer-card">
    <div class="head">
      <span class="title">⏱ 学习计时</span>
      <span class="today">
        今日 <b>{{ todayMinutes }}</b> 分钟
        <span v-if="todayMinutes >= DAILY_GOAL_MINUTES" class="ok">已达标</span>
      </span>
    </div>

    <!-- 计时中 -->
    <template v-if="active">
      <div class="clock">{{ elapsed }}</div>
      <div class="meta text-light">
        {{ active.subjectName || '不指定科目' }} · 开始于 {{ hhmm(active.startedAt) }}
      </div>
      <div class="row">
        <van-button type="primary" color="#ff7a00" round block :loading="busy" @click="onEnd">
          结束并记录
        </van-button>
        <van-button plain round @click="onGiveUp">放弃</van-button>
      </div>
    </template>

    <!-- 空闲 -->
    <template v-else>
      <div class="row">
        <div class="subject-pick" @click="showSubjectPicker = true">
          <span class="text-ellipsis">{{ subjectName }}</span>
          <van-icon name="arrow-down" size="12" />
        </div>
        <van-button type="primary" color="#ff7a00" round :loading="busy" @click="onStart">
          开始学习
        </van-button>
      </div>
      <div class="hint text-light">
        结束后自动计入今日学习时长；忘开计时可
        <span class="link" @click="openManual">手动补录</span>
      </div>
    </template>

    <!-- 今日记录 -->
    <div v-if="sessions.length" class="list">
      <div v-for="s in sessions" :key="s.id" class="item">
        <span class="t">{{ s.source === 'MANUAL' ? '补录' : hhmm(s.startedAt) }}</span>
        <span class="n text-light text-ellipsis">{{ s.subjectName || '未归类' }}</span>
        <span class="m">{{ s.running ? '计时中' : `${s.minutes} 分钟` }}</span>
      </div>
    </div>
  </div>

  <!-- 科目选择 -->
  <van-popup class="hf-popup" v-model:show="showSubjectPicker" position="bottom" round>
    <van-picker
      :columns="subjectColumns"
      title="选择科目"
      @confirm="onSubjectConfirm"
      @cancel="showSubjectPicker = false"
    />
  </van-popup>

  <!-- 手动补录 -->
  <van-popup class="hf-popup" v-model:show="showManual" position="bottom" round>
    <div class="manual">
      <div class="manual-title">手动补录学习时长</div>
      <van-field v-model="manualMinutes" type="digit" label="时长" placeholder="分钟" maxlength="4" />
      <van-field
        :model-value="manualDate"
        label="日期"
        readonly
        is-link
        @click="showDatePicker = true"
      />
      <van-field v-model="manualNote" label="备注" placeholder="学了什么（可选）" maxlength="200" />
      <div class="manual-actions">
        <van-button plain round @click="showManual = false">取消</van-button>
        <van-button type="primary" color="#ff7a00" round :loading="busy" @click="submitManual">
          保存
        </van-button>
      </div>
    </div>
  </van-popup>

  <van-popup class="hf-popup" v-model:show="showDatePicker" position="bottom" round>
    <van-date-picker
      v-model="manualPickerValue"
      title="选择日期"
      :min-date="new Date('2024-01-01')"
      :max-date="new Date(maxDate)"
      @confirm="onManualDateConfirm"
      @cancel="showDatePicker = false"
    />
  </van-popup>
</template>

<style scoped lang="scss">
.timer-card {
  .head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;

    .title {
      font-size: 15px;
      font-weight: 700;
    }

    .today {
      font-size: 13px;
      color: $text-light;

      b {
        font-size: 18px;
        color: $text-main;
      }
    }

    .ok {
      color: $primary;
      font-weight: 600;
      margin-left: 4px;
    }
  }

  .clock {
    font-size: 38px;
    font-weight: 800;
    font-variant-numeric: tabular-nums;
    text-align: center;
    margin: 10px 0 4px;
    color: $primary;
  }

  .meta {
    text-align: center;
    font-size: 12px;
    margin-bottom: 10px;
  }

  .row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 12px;

    :deep(.van-button--block) {
      flex: 1;
    }
  }

  .subject-pick {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 4px;
    height: 44px;
    padding: 0 12px;
    border-radius: var(--hf-radius-panel, 10px);
    background: $bg-inset;
    font-size: 14px;
    cursor: pointer;
  }

  .hint {
    font-size: 12px;
    margin-top: 8px;
  }

  .link {
    color: $primary;
    font-weight: 600;
    cursor: pointer;
  }

  .list {
    margin-top: 12px;
    padding-top: 10px;
    border-top: 1px solid #f0f2f5;

    .item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      padding: 5px 0;

      .t {
        width: 44px;
        color: $text-light;
      }

      .n {
        flex: 1;
        min-width: 0;
      }

      .m {
        font-weight: 600;
      }
    }
  }
}

.manual {
  padding: 16px;

  .manual-title {
    font-size: 15px;
    font-weight: 700;
    margin-bottom: 8px;
  }

  .manual-actions {
    display: flex;
    gap: 10px;
    margin-top: 14px;

    :deep(.van-button) {
      flex: 1;
    }
  }
}
</style>
