<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import { showSuccessToast } from 'vant'
import type { GradeResult } from '@/types/question'
import { apiGradeQuestion } from '@/api'
import { usePracticeStore } from '@/stores'
import PracticeQuestionCard from '@/components/study/PracticeQuestionCard.vue'
import { useIsDesktop } from '@/composables/useDesktop'

/**
 * 刷题会话页：队列 + 进度 + 战报三段式（结构照 WrongPractice）。
 * 判分全走服务端 POST /questions/grade —— 这里不再有任何"自评对错"入口。
 */
defineOptions({ name: 'QuizSession' })

const router = useRouter()
const goBack = useGoBack()
const isDesktop = useIsDesktop()
const store = usePracticeStore()

const queue = computed(() => store.queue)
const idx = ref(0)
const result = ref<GradeResult | null>(null)
const submitting = ref(false)
const finished = computed(() => idx.value >= queue.value.length)
const current = computed(() => (idx.value < queue.value.length ? queue.value[idx.value] : null))

/** 战报：练 N / 对 M / 错题本 K（K=判错的题数，判错必入本/累加，由服务端保证） */
const correctCount = ref(0)
const wrongCount = ref(0)

const progress = computed(() => (queue.value.length ? Math.round((idx.value / queue.value.length) * 100) : 0))

// 队列空 = 刷新丢了内存态（store 不持久化），退回选题页
if (!store.active) {
  router.replace('/study/practice')
}

async function onSubmit(answer: string) {
  const q = current.value
  if (!q || submitting.value) return
  submitting.value = true
  try {
    const res = await apiGradeQuestion({ questionId: q.id, answer })
    result.value = res
    if (res.correct) {
      correctCount.value += 1
      // 错题重练在本的题答对时服务端自动累计连对；连对 2 次摘除是唯一值得打断用户的通知
      if (res.wrongBook?.mastered) showSuccessToast('连对 2 次，已自动摘出错题本')
    } else {
      wrongCount.value += 1
    }
  } catch {
    /* 拦截器已提示；作答保留在卡上可重试提交 */
  } finally {
    submitting.value = false
  }
}

function next() {
  idx.value += 1
  result.value = null
}

function quit() {
  store.clear()
  router.replace('/study')
}
</script>

<template>
  <div>
    <van-nav-bar :title="store.scopeLabel ? `刷题 · ${store.scopeLabel}` : '刷题'" left-arrow @click-left="goBack">
      <template v-if="queue.length && !finished" #right>
        <span class="pos">{{ idx + 1 }} / {{ queue.length }}</span>
      </template>
    </van-nav-bar>

    <van-progress v-if="queue.length && !finished" :percentage="progress" color="#ff7a00" :pivot="false" class="bar" />

    <div class="page-body">
      <!-- 完成战报 -->
      <div v-if="finished && queue.length" class="card report">
        <div class="r-title">{{ isDesktop ? '本组刷完啦' : '🎉 本组刷完啦' }}</div>
        <div class="r-nums">
          <div class="r-sum">
            <div class="num">{{ queue.length }}</div>
            <div class="lb">已刷（题）</div>
          </div>
          <div class="r-sum">
            <div class="num ok">{{ correctCount }}</div>
            <div class="lb">答对</div>
          </div>
          <div class="r-sum">
            <div class="num bad">{{ wrongCount }}</div>
            <div class="lb">已进错题本</div>
          </div>
        </div>
        <div class="r-btns">
          <van-button round plain type="primary" color="#ff7a00" @click="router.replace('/study/practice')">重新选题</van-button>
          <van-button round type="primary" color="#ff7a00" @click="quit">完成</van-button>
        </div>
      </div>

      <template v-else-if="current">
        <PracticeQuestionCard
          :key="current.id"
          :question="current"
          :result="result"
          :submitting="submitting"
          @submit="onSubmit"
        />
        <div v-if="result" class="next-wrap">
          <van-button block round type="primary" color="#ff7a00" @click="next">
            {{ idx + 1 >= queue.length ? '完成本组' : '下一题' }}
          </van-button>
        </div>
        <div v-else-if="current.questionType !== 'JUDGE'" class="hint text-light">
          答案提交后立即判分；答错的题自动进错题本
        </div>
      </template>

      <div v-else class="empty-tip">队列已清空，去重新选题</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pos {
  color: $text-light;
  font-size: 13px;
}

.bar {
  border-radius: 0;
}

.next-wrap {
  margin-top: 12px;
}

.hint {
  margin-top: 10px;
  text-align: center;
  font-size: 12px;
}

.report {
  text-align: center;
  padding: 28px 16px;

  .r-title {
    font-size: 17px;
    font-weight: 700;
  }

  .r-nums {
    display: flex;
    justify-content: center;
    gap: 40px;
    margin: 20px 0 24px;

    .num {
      font-size: 28px;
      font-weight: 800;
      color: $primary;

      &.ok {
        color: $success;
      }

      &.bad {
        color: $danger;
      }
    }

    .lb {
      margin-top: 2px;
      font-size: 12px;
      color: $text-light;
    }
  }

  .r-btns {
    display: flex;
    justify-content: center;
    gap: 12px;
  }
}
</style>
