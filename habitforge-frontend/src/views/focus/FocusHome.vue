<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import { showSuccessToast, showToast } from 'vant'
import dayjs from 'dayjs'
import type { FocusLog, FocusTrend } from '@/types/focus'
import { apiFocusToday, apiFocusTrend, apiRecordUrge, apiSaveFocusLog, apiUpdateFocusLimit } from '@/api'

const router = useRouter()
const goBack = useGoBack()

const today = ref<FocusLog | null>(null)
const trend = ref<FocusTrend | null>(null)
const minutes = ref(0)
const saving = ref(false)
const urgeBusy = ref(false)

const showLimit = ref(false)
const limitInput = ref('60')

const quickMinutes = [0, 30, 60, 90, 120]

const limit = computed(() => today.value?.limitMinutes ?? 60)
const compliant = computed(() => minutes.value <= limit.value)
/** 进度条按上限的 1.5 倍封顶，超标时能看出超了多少 */
const percent = computed(() =>
  Math.min(100, Math.round((minutes.value / Math.max(1, limit.value * 1.5)) * 100))
)
const overPercent = computed(() => Math.round((limit.value / Math.max(1, limit.value * 1.5)) * 100))
const remain = computed(() => limit.value - minutes.value)
const chartMax = computed(() => {
  const peak = Math.max(...(trend.value?.days ?? []).map((d) => d.entertainmentMinutes ?? 0), limit.value * 1.5, 60)
  return Math.round(peak)
})

onMounted(load)

async function load() {
  const to = dayjs().format('YYYY-MM-DD')
  const from = dayjs().subtract(13, 'day').format('YYYY-MM-DD')
  try {
    const [t, tr] = await Promise.all([apiFocusToday(), apiFocusTrend(from, to)])
    today.value = t
    trend.value = tr
    minutes.value = t.entertainmentMinutes ?? 0
  } catch {
    /* 错误已由拦截器提示 */
  }
}

async function refresh() {
  const to = dayjs().format('YYYY-MM-DD')
  const from = dayjs().subtract(13, 'day').format('YYYY-MM-DD')
  const [t, tr] = await Promise.all([apiFocusToday(), apiFocusTrend(from, to)])
  today.value = t
  trend.value = tr
}

async function save() {
  if (saving.value) return
  saving.value = true
  try {
    const wasCompliant = today.value?.compliant ?? false
    const res = await apiSaveFocusLog({ entertainmentMinutes: minutes.value })
    today.value = res
    await refresh()
    if (res.compliant && !wasCompliant) {
      showSuccessToast('节制达标 +10 积分 🎉')
    } else if (res.compliant) {
      showSuccessToast('已记录，今天在限度之内')
    } else {
      showToast(`已记录，超出上限 ${res.entertainmentMinutes! - res.limitMinutes} 分钟`)
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

function pickQuick(v: number) {
  minutes.value = v
}

async function urge(resisted: boolean) {
  if (urgeBusy.value) return
  urgeBusy.value = true
  try {
    today.value = await apiRecordUrge({ resisted })
    await refresh()
    showSuccessToast(resisted ? '记下了，你忍住了 👍' : '记下了，别苛责自己')
  } catch {
    /* 拦截器已提示 */
  } finally {
    urgeBusy.value = false
  }
}

function openLimit() {
  limitInput.value = String(limit.value)
  showLimit.value = true
}

async function saveLimit() {
  const v = Number(limitInput.value)
  if (!Number.isFinite(v) || v < 0 || v > 1440) {
    showToast('请填写 0-1440 之间的分钟数')
    return
  }
  try {
    today.value = await apiUpdateFocusLimit(v)
    showLimit.value = false
    showSuccessToast('上限已更新')
    await refresh()
  } catch {
    /* 拦截器已提示 */
  }
}

function barHeight(d: { entertainmentMinutes: number | null }) {
  if (d.entertainmentMinutes === null) return '3px'
  return Math.max(4, Math.round((d.entertainmentMinutes / chartMax.value) * 100)) + '%'
}
</script>

<template>
  <div>
    <van-nav-bar title="注意力管理" left-arrow @click-left="goBack" />

    <div class="page-body">
      <!-- 今日娱乐时长 -->
      <div class="card">
        <div class="head">
          <span class="t">📱 今日娱乐时长</span>
          <span class="limit" @click="openLimit">上限 {{ limit }} 分钟 ›</span>
        </div>

        <div class="big" :class="{ over: !compliant }">
          {{ minutes }}
          <span class="unit">分钟</span>
        </div>
        <div class="status" :class="compliant ? 'ok' : 'warn'">
          <template v-if="compliant">
            {{ remain > 0 ? `还在限度内，还能用 ${remain} 分钟` : '刚好卡在上限' }}
          </template>
          <template v-else>超出 {{ -remain }} 分钟，明天把手机放远一点</template>
        </div>

        <div class="track">
          <div class="fill" :class="{ over: !compliant }" :style="{ width: percent + '%' }"></div>
          <div class="limit-line" :style="{ left: overPercent + '%' }"></div>
        </div>

        <div class="stepper-row">
          <van-stepper v-model="minutes" :min="0" :max="1440" :step="10" integer input-width="52px" />
          <van-button type="primary" color="#ff7a00" round size="small" :loading="saving" @click="save">
            记录
          </van-button>
        </div>

        <div class="quick">
          <span
            v-for="q in quickMinutes"
            :key="q"
            class="chip"
            :class="{ active: minutes === q }"
            @click="pickQuick(q)"
          >
            {{ q }}
          </span>
          <span class="text-light tip">分钟</span>
        </div>
      </div>

      <!-- 冲动抵抗 -->
      <div class="card">
        <div class="head">
          <span class="t">✋ 想刷手机的时候</span>
        </div>
        <div class="urge-stat text-light">
          今天想刷 <b>{{ today?.urgeTotal ?? 0 }}</b> 次 · 忍住 <b>{{ today?.urgeResisted ?? 0 }}</b> 次
          <template v-if="today?.resistRatePercent !== null && today?.resistRatePercent !== undefined">
            · 忍住率 {{ today.resistRatePercent }}%
          </template>
        </div>
        <div class="urge-row">
          <van-button type="primary" color="#07c160" round size="small" :loading="urgeBusy" @click="urge(true)">
            忍住了
          </van-button>
          <van-button plain round size="small" :loading="urgeBusy" @click="urge(false)">没忍住</van-button>
        </div>
        <div class="text-light tip">
          每一次「想刷」都点一下 —— 把冲动记下来，它就没那么容易赢。<br />
          忍住率是本周复盘里 AI 会重点看的指标。
        </div>
      </div>

      <!-- 近 14 天趋势 -->
      <div class="card" v-if="trend">
        <div class="head">
          <span class="t">📊 近 14 天</span>
          <span class="text-light sub">
            达标 {{ trend.compliantDays }}/{{ trend.recordedDays }} 天
            <template v-if="trend.avgEntertainmentMinutes !== null"> · 日均 {{ trend.avgEntertainmentMinutes }} 分钟</template>
          </span>
        </div>
        <div class="chart">
          <div v-for="d in trend.days" :key="d.date" class="col">
            <div class="col-inner">
              <div
                class="bar"
                :class="{ ok: d.compliant, over: d.entertainmentMinutes !== null && !d.compliant, empty: d.entertainmentMinutes === null }"
                :style="{ height: barHeight(d) }"
                :title="d.date + (d.entertainmentMinutes === null ? ' 未记录' : ' ' + d.entertainmentMinutes + ' 分钟')"
              ></div>
            </div>
            <div class="lb text-light">{{ d.date.slice(8) }}</div>
          </div>
        </div>
        <div class="legend text-light">
          <span class="dot ok"></span>达标
          <span class="dot over"></span>超标
          <span class="dot empty"></span>未记录
        </div>
      </div>

      <!-- 入口 -->
      <div class="card entry" @click="router.push('/focus/environment')">
        <div class="left">
          <div class="t">🏠 环境设计清单</div>
          <div class="d text-light">让它难以发生：手机放远、卸载 App、开灰度模式</div>
        </div>
        <van-icon name="arrow" color="var(--hf-text-light)" />
      </div>
      <div class="card entry" @click="router.push('/focus/contracts')">
        <div class="left">
          <div class="t">🤝 习惯契约</div>
          <div class="d text-light">写下问责伙伴与违约代价，让承诺有分量</div>
        </div>
        <van-icon name="arrow" color="var(--hf-text-light)" />
      </div>
    </div>

    <!-- 上限设置 -->
    <van-dialog v-model:show="showLimit" title="每日娱乐时长上限" show-cancel-button @confirm="saveLimit">
      <div style="padding: 16px">
        <van-field v-model="limitInput" type="digit" label="上限" placeholder="分钟" maxlength="4">
          <template #button>分钟</template>
        </van-field>
        <div class="text-light tip" style="margin-top: 8px">
          建议从 60 分钟起步，逐步下调。改上限不会追溯调整今天已结算的积分。
        </div>
      </div>
    </van-dialog>
  </div>
</template>

<style scoped lang="scss">
.card {
  .head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;

    .t {
      font-size: 15px;
      font-weight: 700;
    }

    .limit {
      font-size: 12px;
      color: $primary;
      cursor: pointer;
    }

    .sub {
      font-size: 12px;
    }
  }

  .big {
    font-size: 40px;
    font-weight: 800;
    text-align: center;
    margin: 8px 0 2px;
    color: $primary;
    font-variant-numeric: tabular-nums;

    &.over {
      color: #ee0a24;
    }

    .unit {
      font-size: 14px;
      font-weight: 600;
      margin-left: 4px;
    }
  }

  .status {
    text-align: center;
    font-size: 12px;
    margin-bottom: 12px;

    &.ok {
      color: #07c160;
    }

    &.warn {
      color: #ee0a24;
    }
  }

  .track {
    position: relative;
    height: 10px;
    border-radius: 999px;
    background: $bg-inset;
    overflow: hidden;

    .fill {
      height: 100%;
      border-radius: 999px;
      background: linear-gradient(90deg, #4bd07a, #07c160);
      transition: width 0.25s;

      &.over {
        background: linear-gradient(90deg, #ff8f6b, #ee0a24);
      }
    }

    .limit-line {
      position: absolute;
      top: 0;
      width: 2px;
      height: 100%;
      background: rgba(0, 0, 0, 0.35);
    }
  }

  .stepper-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 14px;
  }

  .quick {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin-top: 10px;

    .chip {
      padding: 4px 12px;
      border-radius: 999px;
      background: $bg-inset;
      font-size: 13px;
      color: $text-light;
      cursor: pointer;

      &.active {
        background: rgba(255, 122, 0, 0.12);
        color: $primary;
        font-weight: 700;
      }
    }

    .tip {
      font-size: 12px;
    }
  }

  .urge-stat {
    font-size: 13px;
    margin: 8px 0 10px;

    b {
      color: $text-main;
      font-size: 15px;
    }
  }

  .urge-row {
    display: flex;
    gap: 10px;

    :deep(.van-button) {
      flex: 1;
    }
  }

  .tip {
    font-size: 12px;
    line-height: 1.5;
    margin-top: 10px;
  }
}

.chart {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 120px;
  margin-top: 12px;

  .col {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    height: 100%;

    .col-inner {
      flex: 1;
      width: 100%;
      display: flex;
      align-items: flex-end;
    }

    .bar {
      width: 100%;
      border-radius: 3px 3px 0 0;
      background: #d8dde4;

      &.ok {
        background: linear-gradient(180deg, #4bd07a, #07c160);
      }

      &.over {
        background: linear-gradient(180deg, #ff8f6b, #ee0a24);
      }

      &.empty {
        background: repeating-linear-gradient(45deg, #e8ebef, #e8ebef 3px, #f6f7f9 3px, #f6f7f9 6px);
      }
    }

    .lb {
      font-size: 10px;
      margin-top: 4px;
      transform: scale(0.9);
    }
  }
}

.legend {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  margin-top: 10px;

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 2px;
    display: inline-block;
    margin-left: 8px;

    &.ok {
      background: #07c160;
    }

    &.over {
      background: #ee0a24;
    }

    &.empty {
      background: #d8dde4;
    }
  }
}

.entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;

  .left {
    flex: 1;

    .t {
      font-size: 15px;
      font-weight: 700;
    }

    .d {
      font-size: 12px;
      margin-top: 4px;
    }
  }
}
</style>
