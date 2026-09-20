<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import dayjs, { Dayjs } from 'dayjs'
import { apiMonthCheckins } from '@/api'

/**
 * 月历热力图：🟩 有打卡 / ⬜ 无
 * 数据来源 /checkins/month
 */
const current = ref<Dayjs>(dayjs())
const checkedDates = ref<Set<string>>(new Set())
const totalInMonth = ref(0)

const monthLabel = computed(() => current.value.format('YYYY年M月'))

interface Cell {
  day: number
  date: string
  checked: boolean
  isToday: boolean
  future: boolean
  empty?: boolean
}

const cells = computed<Cell[]>(() => {
  const first = current.value.startOf('month')
  const daysInMonth = current.value.daysInMonth()
  const lead = (first.day() + 6) % 7 // 周一开头
  const today = dayjs().format('YYYY-MM-DD')
  const arr: Cell[] = []
  for (let i = 0; i < lead; i++) {
    arr.push({ day: 0, date: '', checked: false, isToday: false, future: false, empty: true })
  }
  for (let d = 1; d <= daysInMonth; d++) {
    const date = current.value.date(d).format('YYYY-MM-DD')
    arr.push({
      day: d,
      date,
      checked: checkedDates.value.has(date),
      isToday: date === today,
      future: date > today
    })
  }
  return arr
})

async function load() {
  const res = await apiMonthCheckins(current.value.format('YYYY-MM'))
  checkedDates.value = new Set(res.dates)
  totalInMonth.value = res.total
}

function prevMonth() {
  current.value = current.value.subtract(1, 'month')
}

function nextMonth() {
  if (current.value.isBefore(dayjs(), 'month')) {
    current.value = current.value.add(1, 'month')
  }
}

watch(current, load)
onMounted(load)
</script>

<template>
  <div class="heatmap card">
    <div class="flex-between">
      <van-icon name="arrow-left" class="nav-arrow" @click="prevMonth" />
      <div class="month-label">{{ monthLabel }} · {{ totalInMonth }} 天有打卡</div>
      <van-icon
        name="arrow"
        class="nav-arrow"
        :class="{ disabled: !current.isBefore(dayjs(), 'month') }"
        @click="nextMonth"
      />
    </div>

    <div class="week-head">
      <span v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w">{{ w }}</span>
    </div>

    <div class="grid">
      <div
        v-for="(c, i) in cells"
        :key="i"
        class="cell"
        :class="{ checked: c.checked, today: c.isToday, future: c.future, empty: c.empty }"
      >
        <template v-if="!c.empty">{{ c.day }}</template>
      </div>
    </div>

    <div class="legend">
      <span><i class="dot checked" /> 已打卡</span>
      <span><i class="dot" /> 未打卡</span>
      <span><i class="dot today" /> 今天</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.nav-arrow {
  font-size: 18px;
  padding: 6px;
  color: $text-main;

  &.disabled {
    opacity: 0.25;
  }
}

.month-label {
  font-weight: 700;
  font-size: 15px;
}

.week-head {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-top: 14px;
  font-size: 12px;
  color: $text-light;
  text-align: center;
}

.grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
  margin-top: 8px;
}

.cell {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 13px;
  color: $text-sub; // 桌面端由 --hf-text-sub 换成中性 #666，移动端仍是 #5d6d7e
  background: #f2f3f5;

  &.checked {
    background: #27ae60;
    color: #fff;
    font-weight: 700;
  }

  &.today {
    outline: 2px solid $primary;
    outline-offset: -2px;
  }

  &.future {
    background: transparent;
    color: #c8cdd6;
  }

  &.empty {
    background: transparent;
  }
}

.legend {
  display: flex;
  gap: 16px;
  margin-top: 14px;
  font-size: 12px;
  color: $text-light;

  .dot {
    display: inline-block;
    width: 10px;
    height: 10px;
    border-radius: 3px;
    background: #f2f3f5;
    margin-right: 4px;

    &.checked {
      background: #27ae60;
    }

    &.today {
      outline: 2px solid $primary;
      outline-offset: -2px;
    }
  }
}
</style>
