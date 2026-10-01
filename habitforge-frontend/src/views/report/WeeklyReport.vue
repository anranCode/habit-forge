<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useGoBack } from '@/composables/useGoBack'
import { showSuccessToast, showConfirmDialog } from 'vant'
import dayjs from 'dayjs'
import type { WeeklyReport, WeeklyReportUsage } from '@/types/report'
import {
  apiWeeklyReport,
  apiGenerateWeeklyReport,
  apiUpdateWeeklyReport,
  apiWeeklyReportUsage
} from '@/api'

const goBack = useGoBack()

/** 周锚点：本周内任意一天（切周时 ±7 天） */
const anchor = ref(dayjs().format('YYYY-MM-DD'))
const report = ref<WeeklyReport | null>(null)
const usage = ref<WeeklyReportUsage | null>(null)
const loading = ref(false)
const generating = ref(false)
const editing = ref(false)
const saving = ref(false)
const showRaw = ref(false)

const form = ref({
  title: '',
  score: 0,
  goodThings: '',
  badThings: '',
  learnings: '',
  suggestions: ''
})

onMounted(load)

/** ISO 周：周一为一周之始（与后端 mondayOf 同口径） */
function mondayOf(d: dayjs.Dayjs) {
  return d.subtract((d.day() + 6) % 7, 'day').startOf('day')
}

const anchorMonday = computed(() => mondayOf(dayjs(anchor.value)))
const thisMonday = computed(() => mondayOf(dayjs()))
const canGoNext = computed(() => anchorMonday.value.isBefore(thisMonday.value))
const isThisWeek = computed(() => anchorMonday.value.isSame(thisMonday.value))

const weekLabel = computed(() => {
  const start = anchorMonday.value
  const end = start.add(6, 'day')
  return `${start.format('M月D日')} ~ ${end.format('M月D日')}`
})

const suggestionList = computed(() =>
  (report.value?.suggestions ?? '')
    .split('\n')
    .map((s) => s.replace(/^[-•\d.、\s]+/, '').trim())
    .filter(Boolean)
)

const scoreText = computed(() =>
  report.value?.score === null || report.value?.score === undefined ? '—' : String(report.value.score)
)

async function load() {
  loading.value = true
  try {
    const [r, u] = await Promise.all([apiWeeklyReport(anchor.value), apiWeeklyReportUsage(anchor.value)])
    report.value = r
    usage.value = u
    editing.value = false
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    loading.value = false
  }
}

function shiftWeek(days: number) {
  anchor.value = dayjs(anchor.value).add(days, 'day').format('YYYY-MM-DD')
  load()
}

function goThisWeek() {
  anchor.value = dayjs().format('YYYY-MM-DD')
  load()
}

async function onGenerate() {
  if (generating.value) return
  if (report.value) {
    try {
      await showConfirmDialog({
        title: '重新生成？',
        message: '会覆盖当前报告的内容（含你手动修改过的部分）'
      })
    } catch {
      return
    }
  }
  generating.value = true
  try {
    report.value = await apiGenerateWeeklyReport(anchor.value)
    usage.value = await apiWeeklyReportUsage(anchor.value)
    showSuccessToast('报告已生成')
  } catch {
    /* 6001/6003/6005/8002 等由拦截器提示 */
  } finally {
    generating.value = false
  }
}

function startEdit() {
  if (!report.value) return
  form.value = {
    title: report.value.title ?? '',
    score: report.value.score ?? 0,
    goodThings: report.value.goodThings ?? '',
    badThings: report.value.badThings ?? '',
    learnings: report.value.learnings ?? '',
    suggestions: report.value.suggestions ?? ''
  }
  editing.value = true
}

async function onSave() {
  if (!report.value || saving.value) return
  saving.value = true
  try {
    report.value = await apiUpdateWeeklyReport(report.value.id, {
      title: form.value.title.trim() || undefined,
      score: form.value.score,
      goodThings: form.value.goodThings,
      badThings: form.value.badThings,
      learnings: form.value.learnings,
      suggestions: form.value.suggestions
    })
    editing.value = false
    showSuccessToast('已保存')
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

/** 快照 JSON 美化展示（"客观"要能被核对） */
const rawJson = computed(() => {
  if (!report.value?.statsSnapshot) return ''
  try {
    return JSON.stringify(JSON.parse(report.value.statsSnapshot), null, 2)
  } catch {
    return report.value.statsSnapshot
  }
})
</script>

<template>
  <div>
    <van-nav-bar title="本周复盘" left-arrow @click-left="goBack">
      <template #right>
        <span v-if="report && !editing" class="nav-btn" @click="startEdit">编辑</span>
      </template>
    </van-nav-bar>

    <div class="page-body">
      <!-- 周切换 -->
      <div class="card week-bar">
        <van-icon name="arrow-left" @click="shiftWeek(-7)" />
        <div class="label" @click="goThisWeek">
          <div class="t">{{ weekLabel }}</div>
          <div class="s text-light">{{ isThisWeek ? '本周' : '历史周' }}</div>
        </div>
        <van-icon name="arrow" :class="{ disabled: !canGoNext }" @click="canGoNext && shiftWeek(7)" />
      </div>

      <!-- 生成入口 -->
      <div class="card gen">
        <van-button
          type="primary"
          color="#ff7a00"
          round
          block
          :loading="generating"
          :loading-text="'AI 正在分析数据…'"
          @click="onGenerate"
        >
          {{ report ? '重新生成报告' : '生成周报' }}
        </van-button>
        <div class="hint text-light">
          <template v-if="usage">
            AI 依据本周学习时长、习惯打卡、复习与日记数据给出客观评价；
            <template v-if="usage.enabled">本周还可生成 {{ usage.remaining }} 次。</template>
            <template v-else>当前 AI 服务未启用（未配置密钥或已关闭）。</template>
          </template>
          <template v-else>生成过程可能耗时数十秒，请勿离开页面。</template>
        </div>
      </div>

      <div v-if="loading" class="card empty-tip">加载中…</div>

      <template v-else-if="report">
        <!-- 评分与标题 -->
        <div class="card score-card">
          <div class="ring">
            <div class="num">{{ scoreText }}</div>
            <div class="unit">分</div>
          </div>
          <div class="brief">
            <div class="title">{{ report.title || '本周复盘' }}</div>
            <div class="meta text-light">
              {{ report.periodStart }} ~ {{ report.periodEnd }}
              <template v-if="report.model"> · {{ report.model }}</template>
            </div>
          </div>
        </div>

        <!-- 编辑态 -->
        <div v-if="editing" class="card">
          <van-field v-model="form.title" label="标题" maxlength="200" />
          <van-field v-model.number="form.score" type="digit" label="评分" maxlength="3" />
          <van-field v-model="form.goodThings" type="textarea" rows="4" autosize label="做得好" maxlength="5000" />
          <van-field v-model="form.badThings" type="textarea" rows="4" autosize label="待改进" maxlength="5000" />
          <van-field v-model="form.learnings" type="textarea" rows="4" autosize label="学到" maxlength="5000" />
          <van-field v-model="form.suggestions" type="textarea" rows="5" autosize label="建议" maxlength="5000" />
          <div class="edit-actions">
            <van-button plain round block @click="editing = false">取消</van-button>
            <van-button type="primary" color="#ff7a00" round block :loading="saving" @click="onSave">
              保存
            </van-button>
          </div>
        </div>

        <!-- 阅读态 -->
        <template v-else>
          <div v-if="report.goodThings" class="card sec">
            <div class="h">✅ 做得好的</div>
            <div class="body">{{ report.goodThings }}</div>
          </div>
          <div v-if="report.badThings" class="card sec">
            <div class="h">⚠️ 待改进的</div>
            <div class="body">{{ report.badThings }}</div>
          </div>
          <div v-if="report.learnings" class="card sec">
            <div class="h">💡 学到什么</div>
            <div class="body">{{ report.learnings }}</div>
          </div>
          <div v-if="suggestionList.length" class="card sec">
            <div class="h">🎯 下周建议</div>
            <ol class="sug">
              <li v-for="(s, i) in suggestionList" :key="i">{{ s }}</li>
            </ol>
          </div>

          <!-- 客观数据快照 -->
          <div v-if="rawJson" class="card sec">
            <div class="h clickable" @click="showRaw = !showRaw">
              📊 本期客观数据
              <van-icon :name="showRaw ? 'arrow-up' : 'arrow-down'" size="12" />
            </div>
            <pre v-if="showRaw" class="raw">{{ rawJson }}</pre>
          </div>
        </template>
      </template>

      <div v-else class="card empty-tip">
        <p>这一周还没有报告</p>
        <p class="sub">点上面的按钮，AI 会把本周的数据读一遍再给结论</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.nav-btn {
  color: $primary;
  font-size: 14px;
  font-weight: 600;
}

.week-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;

  .label {
    flex: 1;
    text-align: center;
    cursor: pointer;

    .t {
      font-size: 15px;
      font-weight: 700;
    }

    .s {
      font-size: 12px;
      margin-top: 2px;
    }
  }

  .disabled {
    opacity: 0.3;
  }
}

.gen {
  .hint {
    font-size: 12px;
    margin-top: 8px;
    line-height: 1.5;
  }
}

.score-card {
  display: flex;
  align-items: center;
  gap: 14px;

  .ring {
    width: 74px;
    height: 74px;
    border-radius: 50%;
    background: rgba(255, 122, 0, 0.1);
    color: $primary;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    .num {
      font-size: 26px;
      font-weight: 800;
      line-height: 1.1;
    }

    .unit {
      font-size: 11px;
    }
  }

  .brief {
    flex: 1;
    min-width: 0;

    .title {
      font-size: 16px;
      font-weight: 700;
      line-height: 1.4;
    }

    .meta {
      font-size: 12px;
      margin-top: 4px;
    }
  }
}

.sec {
  .h {
    font-size: 14px;
    font-weight: 700;
    margin-bottom: 8px;

    &.clickable {
      display: flex;
      align-items: center;
      justify-content: space-between;
      cursor: pointer;
    }
  }

  .body {
    font-size: 14px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .sug {
    margin: 0;
    padding-left: 20px;
    font-size: 14px;
    line-height: 1.7;

    li {
      margin-bottom: 8px;
    }
  }

  .raw {
    margin: 0;
    max-height: 320px;
    overflow: auto;
    font-size: 11px;
    line-height: 1.5;
    background: $bg-inset;
    border-radius: var(--hf-radius-panel, 10px);
    padding: 10px;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

.edit-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.empty-tip .sub {
  font-size: 12px;
  margin-top: 6px;
}
</style>
