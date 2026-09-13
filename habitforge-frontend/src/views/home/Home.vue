<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { onMountedOrActivated } from '@vant/use'
import type { Habit } from '@/types/habit'
import type { JournalDetail, Reflection } from '@/types/journal'
import { useHabitStore, useCheckinStore, useUserStore } from '@/stores'
import { apiTodayJournal, apiReflectionsByJournal, apiStudyOverview, apiPlanToday } from '@/api'
import type { StudyOverview } from '@/types/study'
import type { DailyPlan } from '@/types/plan'
import HabitCard from '@/components/habit/HabitCard.vue'
import ReflectionEditor from '@/components/record/ReflectionEditor.vue'
import StudyTaskCard from '@/components/study/StudyTaskCard.vue'
import PlanCard from '@/components/plan/PlanCard.vue'
import { greeting, todayStr, weekdayCn } from '@/utils/date'
import { moodEmoji } from '@/utils/format'
import dayjs from 'dayjs'

const router = useRouter()
const habitStore = useHabitStore()
const checkinStore = useCheckinStore()
const userStore = useUserStore()

const checkingId = ref<string>('')
const pendingHabit = ref<Habit | null>(null)
const showCheckinDialog = ref(false)

// 今日记录（journal）与今日心得（reflections）
const todayJournal = ref<JournalDetail | null>(null)
const reflections = ref<Reflection[]>([])
// 学习中心汇总（P0 到期卡/错题恒 0）
const studyOverview = ref<StudyOverview | null>(null)
// 今日 AI 安排摘要（未生成时 null）
const plan = ref<DailyPlan | null>(null)
const showReflectionEditor = ref(false)
const reflectionHabit = ref<Habit | null>(null)
const activeReflection = ref<Reflection | null>(null)

const tips = [
  '两分钟规则：新习惯开始时不应超过两分钟 —— "读一页书"也可以！',
  '绝不错过两次：错过一天没关系，但绝不连续错过两天。',
  '习惯叠加：继[当前习惯]之后，我将[新习惯]。',
  '让习惯简便易行：把健身服提前放在床头。',
  '每天进步 1%，一年后你将进步 37 倍。',
  '目标不是读一本书，而是成为读者。'
]
const tipIndex = ref(dayjs().day() % tips.length)

const list = computed(() => habitStore.todayList)
const doneCount = computed(() => list.value.filter((h) => h.checkedToday).length)
const totalCount = computed(() => list.value.length)
const progress = computed(() => (totalCount.value ? Math.round((doneCount.value / totalCount.value) * 100) : 0))
const totalStreak = computed(() => list.value.reduce((s, h) => s + (h.currentStreak || 0), 0))

// 打卡确认弹窗内容：仅在 pendingHabit 存在时输出，杜绝空内容白板
const checkinDialogTitle = computed(() => (pendingHabit.value ? `打卡「${pendingHabit.value.name}」` : ''))
const checkinDialogMessage = computed(() => {
  const h = pendingHabit.value
  if (!h) return ''
  return h.twoMinuteVersion ? `微习惯版本：${h.twoMinuteVersion}` : '完成了今天的习惯？'
})

async function load() {
  if (!userStore.user) {
    try {
      await userStore.fetchMe()
    } catch {
      /* 401 由拦截器处理 */
    }
  }
  await habitStore.loadToday()
  await loadJournalAndReflections()
  await loadStudyOverview()
  await loadPlan()
}

async function loadPlan() {
  try {
    plan.value = await apiPlanToday()
  } catch {
    /* 错误已由拦截器提示 */
  }
}

async function loadStudyOverview() {
  try {
    studyOverview.value = await apiStudyOverview()
  } catch {
    /* 错误已由拦截器提示 */
  }
}

async function loadJournalAndReflections() {
  try {
    todayJournal.value = await apiTodayJournal()
    reflections.value = todayJournal.value
      ? await apiReflectionsByJournal(todayJournal.value.id)
      : []
  } catch {
    /* 错误已由拦截器提示 */
  }
}

/** 某习惯今日的心得（无则 null） */
function reflectionOf(habitId: string): Reflection | null {
  return reflections.value.find((r) => r.habitId === habitId) || null
}

function openReflection(habit: Habit) {
  reflectionHabit.value = habit
  activeReflection.value = reflectionOf(habit.id)
  showReflectionEditor.value = true
}

async function onReflectionSaved() {
  await loadJournalAndReflections()
}

function goTodayJournal() {
  if (todayJournal.value) {
    router.push(`/record/${todayJournal.value.id}`)
  } else {
    router.push('/record/create')
  }
}

// Home 在 keep-alive include 里，首次挂载时 onMounted 与 onActivated 都会触发：
// 分别注册会让 load() 在冷启动时并发跑两遍（5 个接口各请求两次，失败时还会弹两条错误 toast）。
// onMountedOrActivated 正是为此而生：首次只走 onMounted，之后每次重新激活才再跑一次。
onMountedOrActivated(load)

function askCheckin(habit: Habit) {
  // 防抖/防重入：已有弹窗、该习惯已打卡或正在打卡中时忽略，避免连点触发并发弹窗
  if (showCheckinDialog.value || habit.checkedToday || checkingId.value === habit.id) return
  pendingHabit.value = habit
  showCheckinDialog.value = true
}

async function onCheckinConfirm() {
  const habit = pendingHabit.value
  if (!habit) return
  await doCheckin(habit)
}

async function doCheckin(habit: Habit) {
  checkingId.value = habit.id
  try {
    const res = await checkinStore.checkin({
      habitId: habit.id,
      checkDate: todayStr()
    })
    // 本地更新：必须写到 store 里"当前那份"，不能写闭包里的 habit。
    // habitStore.loadToday() 是整份替换 todayList（apiTodayHabits() 每次都返回新对象），
    // 打卡请求往返期间只要有过一次 load（切 tab 回来、下拉刷新），闭包里的 habit 就成了孤儿：
    // 写它不会触发 list 重渲染 → 卡片仍是未打卡 → 用户再点一次 → 后端返回"今日已打卡"错误 toast。
    const target = habitStore.todayList.find((h) => h.id === habit.id) || habit
    target.checkedToday = true
    target.currentStreak = res.streak.currentStreak
    target.missedYesterday = false
    if (userStore.user) {
      userStore.user.points += res.pointsEarned
    }
    let msg = `打卡成功！🔥 连续 ${res.streak.currentStreak} 天 · +${res.pointsEarned} 积分`
    if (res.newAchievements.length) {
      msg += `\n🎉 解锁成就：${res.newAchievements.join('、')}`
    }
    showSuccessToast({ message: msg, duration: 2600 })
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    checkingId.value = ''
  }
}

function goDetail(habit: Habit) {
  router.push(`/habits/${habit.id}`)
}

function goCreate() {
  router.push('/habits/create')
}
</script>

<template>
  <div>
    <div class="hero">
      <div class="flex-between">
        <div>
          <div class="hello">{{ greeting() }}，{{ userStore.user?.username || '朋友' }}！</div>
          <div class="sub">{{ dayjs().format('M月D日') }} {{ weekdayCn(dayjs()) }} · 今天也要加油哦</div>
        </div>
        <div class="level-badge">Lv.{{ userStore.user?.level || 1 }}</div>
      </div>
      <div class="hero-stats">
        <div class="stat">
          <div class="num">{{ totalStreak }}</div>
          <div class="label">今日习惯链总和 🔥</div>
        </div>
        <div class="stat">
          <div class="num">{{ userStore.user?.points || 0 }}</div>
          <div class="label">累计积分 ⭐</div>
        </div>
      </div>
    </div>

    <!-- 桌面端两栏：左边是"今天要做什么"（安排+进度+待打卡），右边是"顺带看一眼"的侧栏。
         移动端 .split 没有任何声明，两个 div 就是普通块级盒子，DOM 结构变了、渲染不变 -->
    <div class="page-body split is-rail">
      <div class="col-main">
        <!-- AI 今日安排摘要卡（顶部主入口：未生成→引导录空闲时段，已生成→进度摘要） -->
        <PlanCard :plan="plan" @click="router.push('/plan')" />

        <!-- 今日进度 -->
        <div class="card">
          <div class="flex-between">
            <span style="font-weight: 700">今日进度</span>
            <span class="text-light">{{ doneCount }}/{{ totalCount }} · {{ progress }}%</span>
          </div>
          <van-progress :percentage="progress" color="#ff7a00" :show-pivot="false" style="margin-top: 12px" />
          <div v-if="totalCount > 0 && doneCount === totalCount" class="all-done">
            🎉 今天全部完成！你正在为想成为的人投票。
          </div>
        </div>

        <!-- 今日待打卡 -->
        <div class="section-title">📋 今日待打卡（{{ totalCount }}）</div>
        <template v-if="list.length">
          <div v-for="h in list" :key="h.id">
            <HabitCard
              :habit="h"
              checkable
              :loading="checkingId === h.id"
              @check="askCheckin"
              @click="goDetail"
            />
            <!-- 心得入口：完成 -> 记录心得；未完成 -> 记录原因；已记录 -> 查看/编辑 -->
            <div class="reflection-entry" @click="openReflection(h)">
              <template v-if="reflectionOf(h.id)">✍️ 已记录心得 · 查看/编辑</template>
              <template v-else-if="h.checkedToday">✍️ 记录心得 · 为什么今天能做到？</template>
              <template v-else>🤔 为什么没完成？记一笔</template>
            </div>
          </div>
        </template>
        <div v-else class="empty-tip">
          <p>今天没有安排的习惯</p>
          <van-button size="small" type="primary" color="#ff7a00" round @click="goCreate">去创建一个</van-button>
        </div>
      </div>

      <div class="col-side">
        <!-- 学习中心任务卡 -->
        <StudyTaskCard :overview="studyOverview" @click="router.push('/study')" />

        <!-- 今日记录 -->
        <div class="card journal-card is-clickable" @click="goTodayJournal">
          <div class="flex-between">
            <span style="font-weight: 700">📔 今日记录</span>
            <span class="text-light">{{ todayJournal ? '查看 ›' : '去记录 ›' }}</span>
          </div>
          <div v-if="todayJournal" class="journal-line">
            <span v-if="todayJournal.mood" class="mood">{{ moodEmoji(todayJournal.mood) }}</span>
            <span v-if="todayJournal.title">{{ todayJournal.title }}</span>
            <span v-else-if="todayJournal.content" class="text-light">{{
              todayJournal.content.slice(0, 30)
            }}</span>
            <span v-else class="text-light">已写下今天，继续补充心得吧</span>
          </div>
          <div v-else class="journal-line text-light">打卡之后，写下一天的记录与心得吧</div>
        </div>

        <!-- 今日建议 -->
        <div class="card tip-card">
          <div class="tip-title">💡 今日建议</div>
          <div class="tip-text">{{ tips[tipIndex] }}</div>
          <div class="tip-next text-light" @click="tipIndex = (tipIndex + 1) % tips.length">换一条 ›</div>
        </div>
      </div>
    </div>

    <!-- 打卡确认弹窗（组件式，避免函数式单例偶发渲染空内容白板） -->
    <van-dialog
      v-model:show="showCheckinDialog"
      :title="checkinDialogTitle"
      :message="checkinDialogMessage"
      show-cancel-button
      confirm-button-text="确认打卡"
      confirm-button-color="#ff7a00"
      @confirm="onCheckinConfirm"
    />

    <!-- 心得编辑弹窗 -->
    <ReflectionEditor
      v-model:show="showReflectionEditor"
      :habit="reflectionHabit"
      :reflection="activeReflection"
      @saved="onReflectionSaved"
    />
  </div>
</template>

<style scoped lang="scss">
.hello {
  font-size: 22px;
  font-weight: 700;
}

.sub {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.7;
}

.level-badge {
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 700;
}

.hero-stats {
  display: flex;
  gap: 12px;
  margin-top: 20px;

  .stat {
    flex: 1;
    background: rgba(255, 255, 255, 0.08);
    border-radius: 12px;
    padding: 12px;
    text-align: center;

    .num {
      font-size: 22px;
      font-weight: 800;
    }

    .label {
      font-size: 12px;
      opacity: 0.7;
      margin-top: 4px;
    }
  }
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  margin: 16px 0 10px;
}

.all-done {
  margin-top: 12px;
  font-size: 13px;
  color: $success;
}

.reflection-entry {
  margin: -6px 0 12px;
  padding: 8px 14px;
  font-size: 12px;
  color: $primary;
  background: rgba(255, 122, 0, 0.06);
  border-radius: 10px;
  text-align: center;
  cursor: pointer;

  &:active {
    background: rgba(255, 122, 0, 0.12);
  }
}

.journal-card {
  .journal-line {
    margin-top: 10px;
    font-size: 13px;
    display: flex;
    align-items: center;
    gap: 6px;

    .mood {
      font-size: 16px;
    }
  }
}

.tip-card {
  .tip-title {
    font-weight: 700;
    font-size: 14px;
  }

  .tip-text {
    margin-top: 8px;
    font-size: 13px;
    line-height: 1.7;
    color: #5d6d7e;
  }

  .tip-next {
    margin-top: 10px;
    font-size: 12px;
    text-align: right;
    cursor: pointer;
  }
}
</style>
