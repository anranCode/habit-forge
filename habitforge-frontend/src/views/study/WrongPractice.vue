<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import { showToast, showSuccessToast } from 'vant'
import type { WrongQuestion } from '@/types/question'
import { apiWrongList, apiPracticeWrong } from '@/api'
import QuestionCard from '@/components/study/QuestionCard.vue'
import { useIsDesktop } from '@/composables/useDesktop'

const isDesktop = useIsDesktop()
defineOptions({ name: 'WrongPractice' })

const route = useRoute()
const router = useRouter()

/** 无历史可退时按路由的 meta.backTo 兜底（桌面端可直接深链进详情页） */
const goBack = useGoBack()

/** 错题本深链 ?subjectId= 时只重练该科目 */
const subjectId = (route.query.subjectId as string) || ''

const queue = ref<WrongQuestion[]>([])
const idx = ref(0)
const revealed = ref(false)
const loading = ref(true)
const answering = ref(false)
const finished = computed(() => !loading.value && idx.value >= queue.value.length)
const current = computed(() => (idx.value < queue.value.length ? queue.value[idx.value] : null))

/** 战报：本次练了 N 道、摘除 M 道 */
const practiced = ref(0)
const removed = ref(0)

const progress = computed(() =>
  queue.value.length ? Math.round((idx.value / queue.value.length) * 100) : 0
)

async function load() {
  loading.value = true
  try {
    queue.value = await apiWrongList(
      subjectId ? { subjectId, limit: 20 } : { limit: 20 }
    )
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function answer(correct: boolean) {
  const q = current.value?.question
  if (!q || answering.value) return
  answering.value = true
  try {
    const res = await apiPracticeWrong(q.id, correct)
    practiced.value += 1
    if (!correct) {
      showToast(res.wrongCount > 1 ? `已记录，累计错 ${res.wrongCount} 次` : '别灰心，再来一遍')
    } else if (res.mastered) {
      removed.value += 1
      showSuccessToast('连对 2 次，已摘除 🎉')
    } else {
      showToast(`答对了！再连对 ${Math.max(2 - res.correctStreak, 1)} 次即摘除`)
    }
    idx.value += 1
    revealed.value = false
  } catch {
    /* 拦截器已提示 */
  } finally {
    answering.value = false
  }
}

async function practiceAgain() {
  idx.value = 0
  practiced.value = 0
  removed.value = 0
  revealed.value = false
  await load()
}
</script>

<template>
  <div>
    <van-nav-bar title="错题重练" left-arrow @click-left="goBack">
      <template v-if="!loading && queue.length && !finished" #right>
        <span class="pos">{{ idx + 1 }} / {{ queue.length }}</span>
      </template>
    </van-nav-bar>

    <van-progress
      v-if="!loading && queue.length && !finished"
      :percentage="progress"
      color="#ff7a00"
      :pivot="false"
      class="bar"
    />

    <div class="page-body">
      <!-- 队列完成：战报 -->
      <div v-if="finished && practiced" class="card report">
        <div class="r-title">{{ isDesktop ? '本组练完啦' : '🎉 本组练完啦' }}</div>
        <div class="r-nums">
          <div class="r-sum">
            <div class="num">{{ practiced }}</div>
            <div class="lb">已练（道）</div>
          </div>
          <div class="r-sum">
            <div class="num ok">{{ removed }}</div>
            <div class="lb">已摘除</div>
          </div>
        </div>
        <div class="r-btns">
          <van-button round plain type="primary" color="#ff7a00" @click="practiceAgain">再来一组</van-button>
          <van-button round type="primary" color="#ff7a00" @click="router.replace('/study/wrongs')">
            返回错题本
          </van-button>
        </div>
      </div>

      <template v-else-if="current">
        <QuestionCard :question="current.question" :revealed="revealed">
          <template #actions>
            <van-button
              size="small"
              round
              plain
              type="danger"
              :loading="answering"
              @click.stop="answer(false)"
            >
              答错了
            </van-button>
            <van-button size="small" round type="success" :loading="answering" @click.stop="answer(true)">
              答对了
            </van-button>
          </template>
        </QuestionCard>

        <div v-if="!revealed" class="reveal-wrap">
          <van-button block round plain type="primary" color="#ff7a00" @click="revealed = true">
            先回想一下 · 显示答案与解析
          </van-button>
        </div>
        <div v-else class="hint text-light">自评后进入下一题（答对连对 2 次自动摘除）</div>
      </template>

      <div v-else-if="!loading" class="empty-tip">
        <p>错题本是空的</p>
        <van-button size="small" round type="primary" color="#ff7a00" @click="router.replace('/study/questions')">
          去题库练手
        </van-button>
      </div>
      <div v-else class="empty-tip">题海中捞题…</div>
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

.reveal-wrap {
  margin-top: 4px;
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
