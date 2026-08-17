<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import type { JournalDetail, JournalSummary } from '@/types/journal'
import { apiTodayJournal, apiJournalsByRange } from '@/api'
import { moodEmoji } from '@/utils/format'
import dayjs from 'dayjs'

defineOptions({ name: 'Record' })

const router = useRouter()

const todayJournal = ref<JournalDetail | null>(null)
const monthCursor = ref(dayjs().startOf('month'))
const monthList = ref<JournalSummary[]>([])
const loading = ref(false)

const monthLabel = computed(() => monthCursor.value.format('YYYY年M月'))

async function loadToday() {
  try {
    todayJournal.value = await apiTodayJournal()
  } catch {
    /* 错误已由拦截器提示 */
  }
}

async function loadMonth() {
  loading.value = true
  try {
    const from = monthCursor.value.format('YYYY-MM-DD')
    const to = monthCursor.value.endOf('month').format('YYYY-MM-DD')
    monthList.value = await apiJournalsByRange(from, to)
  } catch {
    monthList.value = []
  } finally {
    loading.value = false
  }
}

async function load() {
  await Promise.all([loadToday(), loadMonth()])
}

function prevMonth() {
  monthCursor.value = monthCursor.value.subtract(1, 'month')
  loadMonth()
}

function nextMonth() {
  // 不允许翻到未来月份
  if (monthCursor.value.isSame(dayjs().startOf('month'), 'month')) return
  monthCursor.value = monthCursor.value.add(1, 'month')
  loadMonth()
}

function goCreate() {
  router.push('/record/create')
}

function goDetail(j: JournalDetail | JournalSummary) {
  router.push(`/record/${j.id}`)
}

onMounted(load)
onActivated(load)
</script>

<template>
  <div>
    <van-nav-bar title="每日记录" />

    <div class="page-body">
      <!-- 今日记录入口 -->
      <div class="card today-card" @click="todayJournal ? goDetail(todayJournal) : goCreate()">
        <div class="flex-between">
          <span class="t">📔 今天的记录</span>
          <van-icon name="arrow" color="#8a94a6" />
        </div>
        <div v-if="todayJournal" class="preview">
          <span v-if="todayJournal.mood" class="mood">{{ moodEmoji(todayJournal.mood) }}</span>
          <span v-if="todayJournal.title">{{ todayJournal.title }}</span>
          <span v-else-if="todayJournal.content" class="text-light">{{ todayJournal.content.slice(0, 40) }}</span>
          <span v-else class="text-light">已创建，点击补充内容</span>
        </div>
        <div v-else class="preview text-light">还没有写今天的记录，点我写下第一篇 ✍️</div>
      </div>

      <!-- 历史月份 -->
      <div class="month-bar">
        <van-icon name="arrow-left" size="18" @click="prevMonth" />
        <span class="month-label">{{ monthLabel }}</span>
        <van-icon
          name="arrow"
          size="18"
          :color="monthCursor.isSame(dayjs().startOf('month'), 'month') ? '#d7dce4' : '#2c3e50'"
          @click="nextMonth"
        />
      </div>

      <div v-if="loading" class="empty-tip">加载中…</div>
      <template v-else-if="monthList.length">
        <div v-for="j in monthList" :key="j.id" class="card journal-item" @click="goDetail(j)">
          <div class="flex-between">
            <div class="date-line">
              <span v-if="j.mood" class="mood">{{ moodEmoji(j.mood) }}</span>
              <span class="date">{{ dayjs(j.journalDate).format('M月D日 ddd') }}</span>
              <van-tag v-if="j.habitCount" plain round size="medium" color="#ff7a00">{{ j.habitCount }} 个习惯</van-tag>
              <van-tag v-if="j.imageCount" plain round size="medium" color="#3498db">🖼 {{ j.imageCount }}</van-tag>
            </div>
            <van-icon name="arrow" color="#8a94a6" />
          </div>
          <div v-if="j.title" class="title">{{ j.title }}</div>
        </div>
      </template>
      <div v-else class="empty-tip">这个月还没有记录</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.today-card {
  .t {
    font-weight: 700;
    font-size: 15px;
  }

  .preview {
    margin-top: 10px;
    font-size: 13px;
    display: flex;
    align-items: center;
    gap: 6px;

    .mood {
      font-size: 18px;
    }
  }
}

.month-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  margin: 14px 0 10px;

  .month-label {
    font-size: 14px;
    font-weight: 700;
  }
}

.journal-item {
  .date-line {
    display: flex;
    align-items: center;
    gap: 8px;

    .mood {
      font-size: 16px;
    }

    .date {
      font-size: 14px;
      font-weight: 600;
    }
  }

  .title {
    margin-top: 8px;
    font-size: 13px;
    color: #5d6d7e;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
