<script setup lang="ts">
import { ref } from 'vue'
import { onMountedOrActivated } from '@vant/use'
import { apiHabitStats, apiTopStreaks } from '@/api'
import type { HabitStats, StreakTopItem } from '@/types/habit'
import CalendarHeatmap from '@/components/calendar/CalendarHeatmap.vue'
import StatsCard from '@/components/stats/StatsCard.vue'
import { categoryEmoji } from '@/utils/format'

const stats = ref<HabitStats | null>(null)
const top = ref<StreakTopItem[]>([])

async function load() {
  const [s, t] = await Promise.all([apiHabitStats(), apiTopStreaks(10)])
  stats.value = s
  top.value = t.filter((x) => x.currentStreak > 0 || x.longestStreak > 0)
}

// keep-alive 下 onMounted 与 onActivated 首次都会触发：分别注册会让 load() 并发跑两遍
onMountedOrActivated(load)

const medals = ['🥇', '🥈', '🥉']
</script>

<template>
  <div>
    <van-nav-bar title="📊 追踪看板" />

    <div class="page-body">
      <!-- 月度统计 -->
      <div class="flex stat-row" style="gap: 10px; margin-bottom: 12px">
        <StatsCard :value="stats?.monthCheckins ?? '-'" label="本月打卡" emoji="✅" />
        <StatsCard :value="stats ? stats.monthCompletionRate + '%' : '-'" label="本月完成率" emoji="📈" />
        <StatsCard :value="stats?.longestStreakOverall ?? '-'" label="最长纪录" emoji="🏆" />
      </div>

      <!-- 桌面端：热力图占主区，排行与总览收到右侧栏。移动端 .split 无声明，依然是竖排 -->
      <div class="split is-rail">
        <div class="col-main">
          <!-- 日历热力图 -->
          <CalendarHeatmap />
        </div>

        <div class="col-side">
          <!-- 习惯排行 -->
          <div class="section-title">🏆 习惯排行（按当前连续）</div>
          <div v-if="top.length" class="card">
            <div v-for="(item, i) in top" :key="item.habitId" class="rank-row">
              <div class="medal">{{ medals[i] || `${i + 1}.` }}</div>
              <div class="rank-name">
                {{ categoryEmoji[item.category] || '✨' }} {{ item.name }}
                <span class="text-light freq">
                  {{ item.frequencyType === 'WEEKLY_COUNT' ? '周链' : '天链' }}
                </span>
              </div>
              <div class="rank-streak">🔥 {{ item.currentStreak }}</div>
            </div>
          </div>
          <div v-else class="empty-tip">暂无排行数据，先去打卡吧！</div>

          <!-- 总览 -->
          <div v-if="stats" class="card overview">
            <div class="flex-between">
              <span>累计打卡</span>
              <b>{{ stats.totalCheckins }} 次</b>
            </div>
            <div class="flex-between">
              <span>进行中习惯</span>
              <b>{{ stats.activeHabits }} / {{ stats.totalHabits }}</b>
            </div>
            <div class="flex-between">
              <span>当前最高连续</span>
              <b>🔥 {{ stats.currentStreakMax }}</b>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 三张统计卡在 1180px 的内容列里会被拉得过宽，收窄到和下面的主区同一个阅读宽度 */
@media (min-width: #{$bp-desktop}) {
  .stat-row {
    max-width: 720px;
  }
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  margin: 16px 0 10px;
}

.rank-row {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f2f3f5;

  &:last-child {
    border-bottom: none;
  }

  .medal {
    width: 32px;
    font-size: 18px;
  }

  .rank-name {
    flex: 1;
    font-size: 14px;
    font-weight: 600;

    .freq {
      font-size: 11px;
      margin-left: 4px;
      font-weight: 400;
    }
  }

  .rank-streak {
    font-weight: 700;
    color: #ff5722;
  }
}

.overview {
  font-size: 14px;
  color: #5d6d7e;

  > div {
    padding: 8px 0;

    b {
      color: $text-main;
    }
  }
}
</style>
