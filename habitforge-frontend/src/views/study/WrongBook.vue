<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import { showConfirmDialog, showSuccessToast } from 'vant'
import type { WrongQuestion } from '@/types/question'
import { apiWrongList, apiSetWrongMastered, apiDeleteQuestion } from '@/api'
import WrongQuestionItem from '@/components/study/WrongQuestionItem.vue'
import { useIsDesktop } from '@/composables/useDesktop'

defineOptions({ name: 'WrongBook' })

const route = useRoute()
const router = useRouter()
const isDesktop = useIsDesktop()

/** 无历史可退时按路由的 meta.backTo 兜底（桌面端可直接深链进详情页） */
const goBack = useGoBack()

/** 支持科目详情页深链 ?subjectId= 过滤；'' = 全部科目 */
const subjectId = computed(() => (route.query.subjectId as string) || '')

const list = ref<WrongQuestion[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await apiWrongList(subjectId.value ? { subjectId: subjectId.value } : undefined)
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

onMounted(load)
// 重练/详情返回后刷新（练习会改 wrongCount/streak 甚至摘除条目）
onActivated(load)

function goDetail(w: WrongQuestion) {
  router.push(`/study/questions/${w.questionId}`)
}

/** 软移出：mastered=1，不删行 */
async function dismiss(w: WrongQuestion) {
  try {
    await apiSetWrongMastered(w.questionId, true)
    list.value = list.value.filter((x) => x.questionId !== w.questionId)
    showSuccessToast('已移出错题本')
  } catch {
    /* 拦截器已提示 */
  }
}

/** 硬删除整道题（错题行随后端级联失效） */
async function remove(w: WrongQuestion) {
  try {
    await showConfirmDialog({ title: '删除题目', message: '将连同题目本身一起删除，确定？' })
  } catch {
    return /* 取消 */
  }
  try {
    await apiDeleteQuestion(w.questionId)
    list.value = list.value.filter((x) => x.questionId !== w.questionId)
    showSuccessToast('已删除')
  } catch {
    /* 拦截器已提示 */
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="错题本" left-arrow @click-left="goBack">
      <template #right>
        <span v-if="list.length" class="stat">{{ list.length }} 道</span>
      </template>
    </van-nav-bar>

    <div class="page-body">
      <!-- 桌面端错题铺成卡片流，重练按钮横跨整行；移动端 .split 没有任何声明，竖排不变 -->
      <div class="split is-flow">
        <template v-if="list.length">
          <van-button
            block
            round
            type="primary"
            color="#ff7a00"
            class="practice-btn span-all"
            @click="router.push(subjectId ? `/study/wrongs/practice?subjectId=${subjectId}` : '/study/wrongs/practice')"
          >
            开始重练（每次最多 20 道）
          </van-button>

          <van-swipe-cell v-for="w in list" :key="w.questionId">
            <WrongQuestionItem :wrong="w" class="is-clickable" @click="goDetail(w)" />
            <!-- 桌面端（无触摸屏）把左滑才露出的两个操作摆到卡片下沿；
                 移动端这段不渲染，DOM 与像素都不变 -->
            <div v-if="isDesktop" class="card-actions">
              <button type="button" class="inline-action" @click="dismiss(w)">移出错题本</button>
              <button type="button" class="inline-action is-danger" @click="remove(w)">删除</button>
            </div>
            <template #right>
              <div class="swipe-actions">
                <van-button square type="warning" class="swipe-btn" @click="dismiss(w)">移出</van-button>
                <van-button square type="danger" class="swipe-btn" @click="remove(w)">删除</van-button>
              </div>
            </template>
          </van-swipe-cell>
        </template>
        <div v-else-if="!loading" class="empty-tip span-all">
          <p>还没有错题</p>
          <p class="sub">去题库做题，答错的题会自动进到这里</p>
          <van-button size="small" round type="primary" color="#ff7a00" @click="router.push('/study/questions')">
            逛题库
          </van-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.stat {
  color: $text-light;
  font-size: 13px;
}

.practice-btn {
  margin-bottom: 14px;
}

.swipe-actions {
  display: flex;
  height: 100%;
}

.swipe-btn {
  height: 100%;
}

:deep(.van-swipe-cell) {
  margin-bottom: 12px;
  border-radius: $radius-card;
  overflow: hidden;

  .wrong-item {
    margin-bottom: 0;
  }
}

.empty-tip .sub {
  margin: 6px 0 16px;
  font-size: 12px;
}
</style>
