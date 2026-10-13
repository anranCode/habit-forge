<script setup lang="ts">
import { ref, computed, watch, onMounted, onActivated, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import { showConfirmDialog, showToast } from 'vant'
import type { Question, QuestionQuery } from '@/types/question'
import type { Subject, Chapter } from '@/types/study'
import { apiQuestions, apiSubjects, apiChaptersBySubject, apiKnowledgePoints, apiRecognizePoints } from '@/api'
import QuestionCard from '@/components/study/QuestionCard.vue'
import { useIsDesktop } from '@/composables/useDesktop'
import { TYPE_DROPDOWN_OPTIONS, SOURCE_DROPDOWN_OPTIONS } from '@/constants/question'

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
// 章节筛选仅在选中科目时可用；考点筛选全库可用（选项源是后端去重清单）
const chapterId = ref('')
const knowledgePoint = ref('')
const chapters = ref<Chapter[]>([])
const pointNames = ref<string[]>([])

const list = ref<Question[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const finished = ref(false)

// 下拉选项收在 constants/question.ts；顺序与原字面量一致（来源下拉"真题在前"与录题 radio 不同序，各保各的）
const typeOptions = TYPE_DROPDOWN_OPTIONS
const sourceOptions = SOURCE_DROPDOWN_OPTIONS

/** 章节/考点下拉的动态选项（含"全部"头项，与静态下拉同 {text,value} 形状） */
const chapterOptions = computed(() => [{ text: '全部章节', value: '' }].concat(chapters.value.map((c) => ({ text: c.name, value: c.id }))))
const pointOptions = computed(() => [{ text: '全部考点', value: '' }].concat(pointNames.value.map((p) => ({ text: p, value: p }))))

async function loadFilterOptions(subjectId: string) {
  chapters.value = subjectId ? await apiChaptersBySubject(subjectId).catch(() => [] as Chapter[]) : []
  pointNames.value = await apiKnowledgePoints(subjectId || undefined).catch(() => [] as string[])
  // 科目变了，旧考点可能不属于新科目 —— 选中项失效就退回"全部"
  if (knowledgePoint.value && !pointNames.value.includes(knowledgePoint.value)) knowledgePoint.value = ''
}

const nonce = ref(0)

/** 筛选条件指纹：变化时强制重建 van-list 以重触首屏加载（nonce 用于页面重新激活后的手动刷新） */
const filterKey = computed(
  () =>
    `${activeSubject.value}|${qType.value}|${sourceType.value}|${chapterId.value}|${knowledgePoint.value}|${nonce.value}`
)

// 切科目后旧章节必不属于新科目；考点清单按科目重建（全部科目 = 全库清单）
watch(activeSubject, () => {
  chapterId.value = ''
  knowledgePoint.value = ''
  loadFilterOptions(activeSubject.value)
})

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
    if (chapterId.value) params.chapterId = chapterId.value
    if (knowledgePoint.value) params.knowledgePoint = knowledgePoint.value
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
  // 深链 ?subjectId= 时 activeSubject 初始非空，选项源要按它拉
  loadFilterOptions(activeSubject.value)
})

// 录题/编辑/详情返回后刷新列表（新题、题干改动）；改 nonce 以重建 van-list 重触加载
onActivated(() => {
  resetAndReload()
  nonce.value += 1
})

function goDetail(q: Question) {
  router.push(`/study/questions/${q.id}`)
}

// ============ 批量 AI 识别考点（P6）============
// 批量 = 前端串行循环单题端点：后端串行 N 次 LLM 会顶爆 HTTP 超时；
// 逐题调用天然有进度、单题失败隔离、逐题计费（风险备忘与决策 4）。
const selectMode = ref(false)
const selected = ref<string[]>([])
const batchRunning = ref(false)
const batchDone = ref(0)

function isPick(id: string) {
  return selected.value.includes(id)
}

function toggleSelectMode() {
  if (batchRunning.value) return
  selectMode.value = !selectMode.value
  selected.value = []
}

function onCardClick(q: Question) {
  if (!selectMode.value || batchRunning.value) return goDetail(q)
  const i = selected.value.indexOf(q.id)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(q.id)
}

async function runBatchRecognize() {
  const ids = [...selected.value] // 快照: 跑动中取消勾选不影响本轮队列
  if (!ids.length) return showToast('先勾选题目')
  if (ids.length > 10) {
    try {
      await showConfirmDialog({
        title: '批量识别考点',
        message: `将对 ${ids.length} 道题逐题调用 AI：消耗每日识别额度，每题约 5–10 秒。确定开始？`
      })
    } catch {
      return /* 取消 */
    }
  }
  batchRunning.value = true
  batchDone.value = 0
  let ok = 0
  let fail = 0
  for (const qid of ids) {
    try {
      await apiRecognizePoints(qid)
      ok += 1
    } catch {
      fail += 1 // 拦截器已逐题提示(后者覆盖前者), 汇总 toast 兜底
    }
    batchDone.value += 1
  }
  batchRunning.value = false
  selectMode.value = false
  selected.value = []
  showToast(fail ? `识别完成：成功 ${ok} 题，失败 ${fail} 题` : `识别完成：已为 ${ok} 题标注考点`)
  // 新考点要立刻进筛选下拉（列表卡片不显示考点，无需重拉）
  loadFilterOptions(activeSubject.value)
}
</script>

<template>
  <div>
    <van-nav-bar title="题库" left-arrow @click-left="goBack">
      <!-- 桌面端没有右下角的浮动气泡（main.scss 把 .van-floating-bubble 隐藏了），
           录题入口挪到标题栏右侧。移动端这个分支不渲染，DOM 与像素都不变。 -->
      <template #right>
        <span v-if="isDesktop && !selectMode" class="nav-btn" @click="router.push('/study/questions/create')">录题</span>
        <span v-if="!batchRunning" class="nav-btn" @click="toggleSelectMode">{{ selectMode ? '取消' : '多选' }}</span>
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
        <van-dropdown-item v-if="activeSubject" v-model="chapterId" :options="chapterOptions" />
        <van-dropdown-item v-model="knowledgePoint" :options="pointOptions" />
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
          <div v-for="q in list" :key="q.id" class="q-cell" :class="{ picked: isPick(q.id) }" @click="onCardClick(q)">
            <QuestionCard :question="q" class="is-clickable" />
            <span v-if="selectMode" class="pick-dot" :class="{ on: isPick(q.id) }">✓</span>
          </div>
        </div>
      </van-list>
      <div v-if="finished && !list.length" class="empty-tip">
        <p>题库还空着</p>
        <!-- 桌面端没有右下角的浮动按钮，指引得跟着换，否则让人去找一个不存在的东西。
             移动端保留原文案，逐像素不变。 -->
        <p class="sub">{{ isDesktop ? '点右上角「录题」录入第一道题' : '点右下角「录题」录入第一道题' }}</p>
      </div>
    </div>

    <van-floating-bubble v-if="!selectMode" type="primary" icon="edit" @click="router.push('/study/questions/create')">
      <template #default>
        <span style="font-size: 12px">录题</span>
      </template>
    </van-floating-bubble>

    <!-- 多选态底部操作条: 固定吸底, 跑动中按钮显示 i/n 进度 -->
    <div v-if="selectMode" class="batch-bar">
      <span class="cnt">已选 {{ selected.length }} 题</span>
      <van-button
        size="small"
        round
        type="primary"
        color="#ff7a00"
        :disabled="!selected.length || batchRunning"
        :loading="batchRunning"
        :loading-text="`识别中 ${batchDone}/${selected.length}…`"
        @click="runBatchRecognize"
      >
        AI 识别考点
      </van-button>
    </div>
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

/* ===== 批量多选态（P6）: 未进入 selectMode 时这些类一个都不渲染 ===== */
.q-cell {
  position: relative;

  &.picked :deep(.question-card) {
    box-shadow: 0 0 0 2px rgba(255, 122, 0, 0.55);
  }

  .pick-dot {
    position: absolute;
    top: 14px;
    right: 14px;
    z-index: 2;
    width: 20px;
    height: 20px;
    line-height: 20px;
    text-align: center;
    border-radius: 50%;
    border: 1px solid $primary;
    background: $bg-card;
    color: transparent;
    font-size: 12px;

    &.on {
      background: $primary;
      color: #fff;
    }
  }
}

.batch-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: $bg-card;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.06);

  .cnt {
    font-size: 13px;
    color: $text-light;
  }
}
</style>
