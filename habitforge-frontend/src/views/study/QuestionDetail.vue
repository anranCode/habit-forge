<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import { showConfirmDialog, showToast, showSuccessToast } from 'vant'
import type { Question, WrongQuestion } from '@/types/question'
import {
  apiQuestion,
  apiDeleteQuestion,
  apiAddWrong,
  apiWrongList,
  apiSetWrongMastered,
  apiRecognizePoints
} from '@/api'
import QuestionCard from '@/components/study/QuestionCard.vue'

defineOptions({ name: 'QuestionDetail' })

const route = useRoute()
const router = useRouter()

/** 无历史可退时按路由的 meta.backTo 兜底（桌面端可直接深链进详情页） */
const goBack = useGoBack()
const id = String(route.params.id)

const question = ref<Question | null>(null)
/** 该题在错题本中的未摘除条目（mastered=0 列表命中即「已在错题本」） */
const wrongEntry = ref<WrongQuestion | null>(null)
const revealed = ref(false)
const busy = ref(false)
const recognizing = ref(false)

/** AI 识别考点: 端点成功回带最新详情, 直接替换本地对象省一次刷新; 失败(含 6001)由拦截器提示 */
async function recognizePoints() {
  if (recognizing.value) return
  recognizing.value = true
  try {
    question.value = await apiRecognizePoints(id)
    showSuccessToast('考点已更新')
  } catch {
    /* 拦截器已提示 */
  } finally {
    recognizing.value = false
  }
}

async function load() {
  try {
    question.value = await apiQuestion(id)
    const ws = await apiWrongList()
    wrongEntry.value = ws.find((w) => w.questionId === id) ?? null
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(load)

async function markWrong() {
  if (busy.value) return
  busy.value = true
  try {
    wrongEntry.value = await apiAddWrong(id)
    showToast(wrongEntry.value.wrongCount > 1 ? `已记录，累计错 ${wrongEntry.value.wrongCount} 次` : '已加入错题本')
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = false
  }
}

async function removeFromBook() {
  if (busy.value) return
  busy.value = true
  try {
    await apiSetWrongMastered(id, true)
    wrongEntry.value = null
    showSuccessToast('已移出错题本')
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = false
  }
}

async function remove() {
  try {
    await showConfirmDialog({ title: '删除题目', message: '删除后其错题本记录一并失效，确定删除？' })
  } catch {
    return /* 取消 */
  }
  try {
    await apiDeleteQuestion(id)
    showSuccessToast('已删除')
    goBack()
  } catch {
    /* 拦截器已提示 */
  }
}

const statusText = computed(() => {
  const w = wrongEntry.value
  if (!w) return ''
  return `已在错题本 · 累计错 ${w.wrongCount} 次` + (w.correctStreak > 0 ? ` · 连对 ${w.correctStreak}` : '')
})
</script>

<template>
  <div>
    <van-nav-bar title="题目详情" left-arrow @click-left="goBack" />

    <div class="page-body">
      <template v-if="question">
        <QuestionCard :question="question" :revealed="revealed" :show-mark-wrong="false" />

        <!-- 考点标签: 手动录入与 AI 识别共用同一列, 未标注时只留识别入口 -->
        <div v-if="question.knowledgePoints?.length" class="points-row">
          <van-tag v-for="p in question.knowledgePoints" :key="p" type="primary" size="medium">{{ p }}</van-tag>
        </div>
        <van-button
          block
          round
          plain
          type="primary"
          class="ai-points-btn"
          :loading="recognizing"
          loading-text="AI 正在识别…"
          @click="recognizePoints"
        >
          AI 识别考点
        </van-button>

        <van-button v-if="!revealed" block round plain type="primary" color="#ff7a00" @click="revealed = true">
          显示答案与解析
        </van-button>

        <div v-if="revealed && statusText" class="wrong-status text-light">{{ statusText }}</div>

        <div class="act-row">
          <van-button v-if="!wrongEntry" size="small" round plain type="danger" :loading="busy" @click="markWrong">
            我答错了
          </van-button>
          <van-button v-else size="small" round plain type="success" :loading="busy" @click="removeFromBook">
            移出错题本
          </van-button>
          <van-button size="small" round plain @click="router.push(`/study/questions/edit/${id}`)">编辑</van-button>
          <van-button size="small" round plain type="danger" @click="remove">删除</van-button>
        </div>
      </template>
      <div v-else class="empty-tip">加载中…</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.points-row {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}

.ai-points-btn {
  margin-top: 12px;
}

.wrong-status {
  margin-top: 12px;
  font-size: 12px;
  text-align: center;
}

.act-row {
  margin-top: 16px;
  display: flex;
  justify-content: center;
  gap: 10px;
}
</style>
