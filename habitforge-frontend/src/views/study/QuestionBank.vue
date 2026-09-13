<script setup lang="ts">
import { ref, computed, watch, onMounted, onActivated, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import type { Question, QuestionQuery } from '@/types/question'
import type { Subject } from '@/types/study'
import { apiQuestions, apiSubjects } from '@/api'
import QuestionCard from '@/components/study/QuestionCard.vue'
import { useIsDesktop } from '@/composables/useDesktop'

defineOptions({ name: 'QuestionBank' })

const route = useRoute()
const router = useRouter()

/** 无历史可退时按路由的 meta.backTo 兜底（桌面端可直接深链进详情页） */
const goBack = useGoBack()
const isDesktop = useIsDesktop()

const SIZE = 20

const subjects = ref<Subject[]>([])
// 支持科目详情页深链 ?subjectId= 预选科目；'' = 全部科目
const activeSubject = ref((route.query.subjectId as string) || '')
const qType = ref('') // '' = 全部题型
const sourceType = ref('') // '' = 全部来源

const list = ref<Question[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const finished = ref(false)

const typeOptions = [
  { text: '全部题型', value: '' },
  { text: '单选题', value: 'SINGLE' },
  { text: '多选题', value: 'MULTI' },
  { text: '判断题', value: 'JUDGE' },
  { text: '主观题', value: 'SHORT' }
]

const sourceOptions = [
  { text: '全部来源', value: '' },
  { text: '真题', value: 'PAST_EXAM' },
  { text: '教材', value: 'TEXTBOOK' },
  { text: '自定义', value: 'CUSTOM' },
  { text: 'AI 生成', value: 'AI' }
]

const nonce = ref(0)

/** 筛选条件指纹：变化时强制重建 van-list 以重触首屏加载（nonce 用于页面重新激活后的手动刷新） */
const filterKey = computed(() => `${activeSubject.value}|${qType.value}|${sourceType.value}|${nonce.value}`)

async function loadSubjects() {
  try {
    subjects.value = await apiSubjects()
  } catch {
    /* 错误已由拦截器提示 */
  }
}

async function loadMore() {
  loading.value = true
  try {
    const params: QuestionQuery = { page: page.value, size: SIZE }
    if (activeSubject.value) params.subjectId = activeSubject.value
    if (qType.value) params.questionType = qType.value
    if (sourceType.value) params.sourceType = sourceType.value
    const res = await apiQuestions(params)
    list.value = list.value.concat(res.records)
    total.value = Number(res.total)
    finished.value = !res.records.length || list.value.length >= total.value
    page.value += 1
  } catch {
    finished.value = true
  } finally {
    loading.value = false
  }
}

function resetAndReload() {
  list.value = []
  total.value = 0
  page.value = 1
  finished.value = false
  loading.value = false
}

watch(filterKey, async () => {
  resetAndReload()
  await nextTick()
})

onMounted(() => {
  loadSubjects()
})

// 录题/编辑/详情返回后刷新列表（新题、题干改动）；改 nonce 以重建 van-list 重触加载
onActivated(() => {
  resetAndReload()
  nonce.value += 1
})

function goDetail(q: Question) {
  router.push(`/study/questions/${q.id}`)
}
</script>

<template>
  <div>
    <van-nav-bar title="题库" left-arrow @click-left="goBack">
      <!-- 桌面端没有右下角的浮动气泡（main.scss 把 .van-floating-bubble 隐藏了），
           录题入口挪到标题栏右侧。移动端这个分支不渲染，DOM 与像素都不变。 -->
      <template #right>
        <span v-if="isDesktop" class="nav-btn" @click="router.push('/study/questions/create')">录题</span>
      </template>
    </van-nav-bar>

    <div class="filter-bar">
      <div class="chips">
        <span class="chip" :class="{ on: activeSubject === '' }" @click="activeSubject = ''">全部</span>
        <span
          v-for="s in subjects"
          :key="s.id"
          class="chip"
          :class="{ on: activeSubject === s.id }"
          @click="activeSubject = s.id"
          >{{ s.name }}</span
        >
      </div>
      <van-dropdown-menu active-color="#ff7a00">
        <van-dropdown-item v-model="qType" :options="typeOptions" />
        <van-dropdown-item v-model="sourceType" :options="sourceOptions" />
      </van-dropdown-menu>
    </div>

    <div class="page-body">
      <van-list
        v-model:loading="loading"
        :key="filterKey"
        :finished="finished"
        finished-text="没有更多了"
        @load="loadMore"
      >
        <!-- 桌面端把题目铺成卡片流；移动端 .split 没有任何声明，仍是一条一条竖排 -->
        <div class="split is-flow">
          <QuestionCard v-for="q in list" :key="q.id" :question="q" class="is-clickable" @click="goDetail(q)" />
        </div>
      </van-list>
      <div v-if="finished && !list.length" class="empty-tip">
        <p>题库还空着</p>
        <!-- 桌面端没有右下角的浮动按钮，指引得跟着换，否则让人去找一个不存在的东西。
             移动端保留原文案，逐像素不变。 -->
        <p class="sub">{{ isDesktop ? '点右上角「录题」录入第一道题' : '点右下角「录题」录入第一道题' }}</p>
      </div>
    </div>

    <van-floating-bubble type="primary" icon="edit" @click="router.push('/study/questions/create')">
      <template #default>
        <span style="font-size: 12px">录题</span>
      </template>
    </van-floating-bubble>
  </div>
</template>

<style scoped lang="scss">
.filter-bar {
  background: $bg-card;

  .chips {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    padding: 10px 16px;

    &::-webkit-scrollbar {
      display: none;
    }

    .chip {
      flex-shrink: 0;
      padding: 5px 14px;
      border-radius: 100px;
      background: $bg-page;
      color: $text-light;
      font-size: 13px;

      &.on {
        background: rgba(255, 122, 0, 0.12);
        color: $primary;
        font-weight: 700;
      }
    }
  }

  :deep(.van-dropdown-menu) {
    .van-cell {
      padding: 8px 16px;
    }
  }
}

.empty-tip .sub {
  margin-top: 6px;
  font-size: 12px;
}
</style>
