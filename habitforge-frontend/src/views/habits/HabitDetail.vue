<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import dayjs from 'dayjs'
import { apiHabitDetail, apiCheckinsByHabit, apiCancelCheckin, apiReflectionsByHabit } from '@/api'
import type { Habit, CheckinRecord } from '@/types/habit'
import type { Reflection } from '@/types/journal'
import { categoryEmoji, frequencyLabel, feelingEmoji } from '@/utils/format'

const router = useRouter()
const route = useRoute()
const habitId = route.params.id as string

const habit = ref<Habit | null>(null)
const records = ref<CheckinRecord[]>([])
const reflections = ref<Reflection[]>([])

onMounted(load)

async function load() {
  habit.value = await apiHabitDetail(habitId)
  records.value = await apiCheckinsByHabit(habitId)
  reflections.value = await apiReflectionsByHabit(habitId).catch(() => [] as Reflection[])
}

const totalDays = computed(() => records.value.length)
const firstDate = computed(() =>
  records.value.length ? records.value[records.value.length - 1].checkDate : null
)

async function cancelRecord(r: CheckinRecord) {
  try {
    await showConfirmDialog({
      title: '撤销打卡',
      message: `撤销 ${r.checkDate} 的打卡？连续记录将重新计算。`
    })
    await apiCancelCheckin(r.id)
    showSuccessToast('已撤销')
    await load()
  } catch {
    /* 取消 */
  }
}
</script>

<template>
  <div v-if="habit">
    <van-nav-bar :title="habit.name" left-arrow @click-left="router.back()">
      <template #right>
        <van-icon name="edit" size="18" @click="router.push(`/habits/edit/${habit.id}`)" />
      </template>
    </van-nav-bar>

    <div class="page-body">
      <!-- 链数据 -->
      <div class="streak-panel">
        <div class="streak-item">
          <div class="num">🔥 {{ habit.currentStreak || 0 }}</div>
          <div class="label">当前连续</div>
        </div>
        <div class="divider" />
        <div class="streak-item">
          <div class="num">🏆 {{ habit.longestStreak || 0 }}</div>
          <div class="label">最长连续</div>
        </div>
        <div class="divider" />
        <div class="streak-item">
          <div class="num">📅 {{ totalDays }}</div>
          <div class="label">累计打卡</div>
        </div>
      </div>

      <!-- 习惯信息 -->
      <div class="card">
        <div class="info-title">{{ categoryEmoji[habit.category] }} {{ habit.name }}</div>
        <div class="info-grid">
          <div v-if="habit.identityTag" class="info-item"><span class="k">身份标签</span>{{ habit.identityTag }}</div>
          <div class="info-item"><span class="k">频率</span>{{ frequencyLabel(habit.frequencyType, habit.frequencyDays, habit.frequencyTarget) }}</div>
          <div v-if="habit.execTime" class="info-item"><span class="k">执行时间</span>{{ habit.execTime }}</div>
          <div v-if="habit.execPlace" class="info-item"><span class="k">执行地点</span>{{ habit.execPlace }}</div>
          <div v-if="habit.stackAfter" class="info-item"><span class="k">习惯叠加</span>{{ habit.stackAfter }}</div>
          <div v-if="habit.twoMinuteVersion" class="info-item"><span class="k">两分钟版本</span>{{ habit.twoMinuteVersion }}</div>
          <div v-if="firstDate" class="info-item"><span class="k">首次打卡</span>{{ firstDate }}</div>
        </div>
      </div>

      <!-- 我的心得 -->
      <div class="section-title">✍️ 我的心得</div>
      <div v-if="reflections.length">
        <div v-for="r in reflections" :key="r.id" class="reflection-card" @click="router.push(`/record/${r.journalId}`)">
          <div class="flex-between">
            <div class="r-date">
              {{ r.journalDate ? dayjs(r.journalDate).format('M月D日 ddd') : '-' }}
              <span v-if="feelingEmoji(r.feeling)" class="feeling">{{ feelingEmoji(r.feeling) }}</span>
              <span v-if="r.difficulty" class="text-light diff">⭐{{ r.difficulty }}</span>
            </div>
            <van-tag v-if="r.result === 1" type="success" round>✅ 完成</van-tag>
            <van-tag v-else type="danger" round>❌ 未完成</van-tag>
          </div>
          <div v-if="r.reason" class="r-line"><span class="k">原因</span>{{ r.reason }}</div>
          <div v-if="r.obstacle" class="r-line"><span class="k">困难</span>{{ r.obstacle }}</div>
          <div v-if="r.learning" class="r-line"><span class="k">学到</span>{{ r.learning }}</div>
          <div v-if="r.adjustment" class="r-line"><span class="k">调整</span>{{ r.adjustment }}</div>
          <div class="r-jump text-light">查看当日记录（含图片） ›</div>
        </div>
      </div>
      <div v-else class="empty-tip-sm text-light">还没有心得，回今日页记一笔吧</div>

      <!-- 打卡历史 -->
      <div class="section-title">打卡记录</div>
      <div v-if="records.length">
        <van-swipe-cell v-for="r in records" :key="r.id">
          <div class="record-row">
            <div class="date">{{ r.checkDate }} <span class="text-light">{{ dayjs(r.checkDate).format('ddd') }}</span></div>
            <div v-if="r.note" class="note text-light">{{ r.note }}</div>
          </div>
          <template #right>
            <van-button square type="danger" text="撤销" style="height: 100%" @click="cancelRecord(r)" />
          </template>
        </van-swipe-cell>
      </div>
      <div v-else class="empty-tip">还没有打卡记录，从今天开始吧！</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.streak-panel {
  display: flex;
  background: linear-gradient(135deg, #1a1a2e, #16213e);
  border-radius: $radius-card;
  padding: 20px 10px;
  margin-bottom: 12px;
  color: #fff;

  .streak-item {
    flex: 1;
    text-align: center;

    .num {
      font-size: 20px;
      font-weight: 800;
    }

    .label {
      font-size: 12px;
      opacity: 0.65;
      margin-top: 6px;
    }
  }

  .divider {
    width: 1px;
    background: rgba(255, 255, 255, 0.15);
  }
}

.info-title {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 12px;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  font-size: 13px;

  .info-item {
    display: flex;
    flex-direction: column;
    gap: 2px;

    .k {
      font-size: 12px;
      color: $text-light;
    }
  }
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  margin: 16px 0 10px;
}

.reflection-card {
  background: $bg-card;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
  cursor: pointer;

  .r-date {
    font-size: 14px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;

    .feeling {
      font-size: 16px;
    }

    .diff {
      font-size: 12px;
      font-weight: 400;
    }
  }

  .r-line {
    margin-top: 8px;
    font-size: 13px;
    color: #5d6d7e;
    line-height: 1.6;

    .k {
      display: inline-block;
      min-width: 34px;
      color: $text-light;
      font-size: 12px;
      margin-right: 6px;
    }
  }

  .r-jump {
    margin-top: 10px;
    font-size: 12px;
    text-align: right;
  }
}

.empty-tip-sm {
  font-size: 13px;
  text-align: center;
  padding: 16px 0;
}

.record-row {
  background: $bg-card;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 8px;

  .date {
    font-size: 14px;
    font-weight: 600;
  }

  .note {
    margin-top: 4px;
    font-size: 12px;
  }
}

:deep(.van-swipe-cell) {
  border-radius: 10px;
  overflow: hidden;
}
</style>
