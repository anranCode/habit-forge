<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import { showToast } from 'vant'
import type { Subject } from '@/types/study'
import type { Flashcard, ReviewRating } from '@/types/flashcard'
import { apiSubjects, apiReviewQueue, apiReviewFlashcard, apiFlashcardStats } from '@/api'
import FlashcardFlip from '@/components/study/FlashcardFlip.vue'
import ReviewProgress from '@/components/study/ReviewProgress.vue'

const route = useRoute()
const router = useRouter()

/** 无历史可退时按路由的 meta.backTo 兜底（桌面端可直接深链进详情页） */
const goBack = useGoBack()

/** '' = 全部科目；或从 ?subjectId= 预选 */
const activeSubject = ref((route.query.subjectId as string) || '')
const subjects = ref<Subject[]>([])
const queue = ref<Flashcard[]>([])
const loading = ref(false)
const loadFailed = ref(false)
/** 本次会话累计评分次数（含同卡重评） */
const reviewedCount = ref(0)
/** 今日已复习总数：进页取 stats，评完后用 review 响应实时刷新 */
const reviewedToday = ref(0)
/** 首次加载完成前不显示完成态 */
const loaded = ref(false)
let ratingLock = false

const current = computed(() => queue.value[0] ?? null)
const sessionTotal = computed(() => reviewedCount.value + queue.value.length)
const finished = computed(() => loaded.value && !loading.value && queue.value.length === 0)

const subjectName = computed(() => {
  if (!activeSubject.value || !current.value) return undefined
  return subjects.value.find((s) => s.id === current.value!.subjectId)?.name
})

onMounted(async () => {
  subjects.value = await apiSubjects().catch(() => [] as Subject[])
  stats()
  loadQueue()
})

async function stats() {
  const s = await apiFlashcardStats().catch(() => null)
  if (s) reviewedToday.value = s.reviewedToday
}

async function loadQueue() {
  loading.value = true
  loadFailed.value = false
  reviewedCount.value = 0
  try {
    const res = await apiReviewQueue(
      activeSubject.value ? { subjectId: activeSubject.value } : undefined
    )
    queue.value = res.cards
  } catch {
    loadFailed.value = true
    queue.value = []
  } finally {
    loading.value = false
    loaded.value = true
  }
}

watch(activeSubject, loadQueue)

async function onRate(rating: ReviewRating) {
  const card = current.value
  if (!card || ratingLock || loading.value) return
  ratingLock = true
  try {
    const res = await apiReviewFlashcard(card.id, rating)
    reviewedToday.value = res.reviewedToday
    if (res.rewardPoints > 0) {
      showToast({ message: `复习达标 +${res.rewardPoints} 积分`, icon: '🎉' })
    }
    reviewedCount.value += 1
    // rating=1「忘记」当日重现：本地放回队尾，无需重拉队列
    if (rating === 1) {
      queue.value = [...queue.value.slice(1), card]
    } else {
      queue.value = queue.value.slice(1)
    }
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    ratingLock = false
  }
}

function reload() {
  stats()
  loadQueue()
}
</script>

<template>
  <div>
    <van-nav-bar title="闪卡复习" left-arrow @click-left="goBack">
      <template #right>
        <span class="to-create" @click="router.push('/study/flashcards/create')">新建</span>
      </template>
    </van-nav-bar>

    <van-tabs
      v-model:active="activeSubject"
      shrink-to-fit
      line-width="20px"
      :ellipsis="false"
      class="subj-tabs"
    >
      <van-tab title="全部" name="" />
      <van-tab v-for="s in subjects" :key="s.id" :title="s.name" :name="s.id" />
    </van-tabs>

    <div class="page-body">
      <template v-if="current">
        <ReviewProgress :reviewed="reviewedCount" :total="sessionTotal" :reviewed-today="reviewedToday" />
        <FlashcardFlip :card="current" :subject-name="subjectName" @rate="onRate" />
      </template>

      <van-loading v-else-if="loading" class="center-load" vertical>加载队列…</van-loading>

      <div v-else-if="loadFailed" class="empty-tip">
        <div>队列加载失败</div>
        <van-button size="small" type="primary" color="#ff7a00" round class="retry" @click="reload">重试</van-button>
      </div>

      <div v-else-if="finished" class="empty-tip done">
        <div class="emoji">🎉</div>
        <div class="t1">今日到期卡已清空</div>
        <div class="t2">本次复习 {{ reviewedCount }} 张 · 今日累计 {{ reviewedToday }} 张</div>
        <div v-if="reviewedCount === 0" class="t2 text-light">当前范围内没有到期的卡片</div>
        <van-button size="small" plain type="primary" color="#ff7a00" round class="retry" @click="reload">
          刷新队列
        </van-button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.to-create {
  color: $primary;
  font-size: 14px;
  font-weight: 600;
}

.subj-tabs {
  background: $bg-card;
  margin-bottom: 12px;
  border-radius: 0 0 var(--hf-radius-panel, 12px) var(--hf-radius-panel, 12px);
  overflow: hidden;
}

.center-load {
  padding: 64px 0;
}

.done {
  padding: 48px 16px;

  .emoji {
    font-size: 44px;
    margin-bottom: 8px;
  }

  .t1 {
    font-size: 16px;
    font-weight: 700;
    color: $text-main;
    margin-bottom: 6px;
  }

  .t2 {
    font-size: 13px;
    color: $text-main;
  }
}

.retry {
  margin-top: 14px;
}
</style>
