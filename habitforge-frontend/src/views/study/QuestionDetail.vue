<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast, showSuccessToast } from 'vant'
import type { Question, WrongQuestion } from '@/types/question'
import {
  apiQuestion,
  apiDeleteQuestion,
  apiAddWrong,
  apiWrongList,
  apiSetWrongMastered
} from '@/api'
import QuestionCard from '@/components/study/QuestionCard.vue'

defineOptions({ name: 'QuestionDetail' })

const route = useRoute()
const router = useRouter()
const id = String(route.params.id)

const question = ref<Question | null>(null)
/** 该题在错题本中的未摘除条目（mastered=0 列表命中即「已在错题本」） */
const wrongEntry = ref<WrongQuestion | null>(null)
const revealed = ref(false)
const busy = ref(false)

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
    router.back()
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
    <van-nav-bar title="题目详情" left-arrow @click-left="router.back()" />

    <div class="page-body">
      <template v-if="question">
        <QuestionCard :question="question" :revealed="revealed" :show-mark-wrong="false" />

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
