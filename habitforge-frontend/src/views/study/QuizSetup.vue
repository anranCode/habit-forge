<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import { showToast } from 'vant'
import type { Subject, Chapter } from '@/types/study'
import type { QuestionType, RandomQuery } from '@/types/question'
import { apiSubjects, apiChaptersBySubject, apiRandomQuestions } from '@/api'
import { usePopupPosition } from '@/composables/useDesktop'
import { usePracticeStore } from '@/stores'
import { TYPE_LABEL } from '@/constants/question'

/**
 * 刷题选题页（功能B 入口）：范围(整个题库/科目/章节) + 题型 + 题量 → 随机取题 → 会话页。
 * P3 只开客观题；SHORT 的勾选在 P4 接上 AI 判分后放开。
 */
defineOptions({ name: 'QuizSetup' })

const route = useRoute()
const router = useRouter()
const goBack = useGoBack()
const popupPosition = usePopupPosition()
const store = usePracticeStore()

const subjects = ref<Subject[]>([])
const chapters = ref<Chapter[]>([])

type Scope = 'ALL' | 'SUBJECT' | 'CHAPTER'
const scope = ref<Scope>('ALL')
const subjectId = ref((route.query.subjectId as string) || '')
const chapterId = ref('')
const showChapterPicker = ref(false)

/** 可选客观题型（顺序与 constants 的 TYPE_ORDER 一致） */
const OBJECTIVE_TYPES: QuestionType[] = ['SINGLE', 'MULTI', 'JUDGE']
const types = ref<QuestionType[]>([...OBJECTIVE_TYPES])

const COUNT_PRESETS = [5, 10, 20, 50]
const limit = ref(10)

const scopeOptions: { value: Scope; label: string; hint: string }[] = [
  { value: 'ALL', label: '整个题库', hint: '全库随机' },
  { value: 'SUBJECT', label: '按科目', hint: '科目内随机' },
  { value: 'CHAPTER', label: '按章节', hint: '单元针对性刷' }
]

const needSubject = computed(() => scope.value !== 'ALL')
const chapterName = computed(() => chapters.value.find((c) => c.id === chapterId.value)?.name || '')

async function loadSubjects() {
  try {
    subjects.value = await apiSubjects()
    if (subjectId.value) {
      scope.value = scope.value === 'ALL' ? 'SUBJECT' : scope.value
      loadChapters(subjectId.value)
    }
  } catch {
    /* 拦截器已提示 */
  }
}

async function loadChapters(id: string) {
  chapters.value = id ? await apiChaptersBySubject(id).catch(() => [] as Chapter[]) : []
}

function pickSubject(id: string) {
  subjectId.value = id
  chapterId.value = '' // 章节级联：换科目必清
  loadChapters(id)
}

function pickChapter(id: string) {
  chapterId.value = id
  showChapterPicker.value = false
}

function toggleType(t: QuestionType) {
  const i = types.value.indexOf(t)
  if (i >= 0) types.value.splice(i, 1)
  else types.value.push(t)
}

const starting = ref(false)

async function start() {
  if (starting.value) return
  if (needSubject.value && !subjectId.value) {
    showToast('请先选择科目')
    return
  }
  if (scope.value === 'CHAPTER' && !chapterId.value) {
    showToast('请选择章节')
    return
  }
  if (!types.value.length) {
    showToast('至少勾选一种题型')
    return
  }
  const q: RandomQuery = { limit: limit.value }
  if (scope.value !== 'ALL') q.subjectId = subjectId.value
  if (scope.value === 'CHAPTER') q.chapterId = chapterId.value
  if (types.value.length < OBJECTIVE_TYPES.length) q.questionTypes = [...types.value].join(',')

  starting.value = true
  try {
    const list = await apiRandomQuestions(q)
    const subjectName = subjects.value.find((s) => s.id === subjectId.value)?.name || ''
    const scopeLabel =
      scope.value === 'ALL' ? '整个题库' : scope.value === 'SUBJECT' ? subjectName : `${subjectName} · ${chapterName.value}`
    store.start(list, scopeLabel)
    router.push('/study/practice/session')
  } catch {
    /* 拦截器已提示（含 7015 空范围） */
  } finally {
    starting.value = false
  }
}

onMounted(loadSubjects)
</script>

<template>
  <div>
    <van-nav-bar title="刷题" left-arrow @click-left="goBack" />

    <div class="page-body is-form">
      <van-cell-group inset class="grp" title="选题范围">
        <div class="scope-row">
          <div
            v-for="s in scopeOptions"
            :key="s.value"
            class="scope-cell"
            :class="{ on: scope === s.value }"
            @click="scope = s.value"
          >
            <div class="s-label">{{ s.label }}</div>
            <div class="s-hint">{{ s.hint }}</div>
          </div>
        </div>

        <template v-if="needSubject">
          <div class="chips">
            <span v-for="s in subjects" :key="s.id" class="chip" :class="{ on: subjectId === s.id }" @click="pickSubject(s.id)">
              {{ s.name }}
            </span>
            <span v-if="!subjects.length" class="text-light chip-empty">还没有科目</span>
          </div>
          <van-field
            v-if="scope === 'CHAPTER'"
            :model-value="chapterName"
            label="章节"
            :placeholder="subjectId ? '请选择章节' : '先选科目'"
            readonly
            is-link
            @click="subjectId && (showChapterPicker = true)"
          />
        </template>
      </van-cell-group>

      <van-cell-group inset class="grp" title="题型">
        <div class="type-row">
          <div v-for="t in OBJECTIVE_TYPES" :key="t" class="type-cell" :class="{ on: types.includes(t) }" @click="toggleType(t)">
            {{ TYPE_LABEL[t] }}题
          </div>
        </div>
        <div class="text-light type-note">主观题按要点 AI 判分，判分链路接入后开放勾选</div>
      </van-cell-group>

      <van-cell-group inset class="grp" title="题量">
        <div class="type-row">
          <div v-for="c in COUNT_PRESETS" :key="c" class="type-cell" :class="{ on: limit === c }" @click="limit = c">
            {{ c }} 题
          </div>
        </div>
        <div class="text-light type-note">范围内题量不足时有多少发多少</div>
      </van-cell-group>

      <div class="submit-row">
        <van-button block round type="primary" color="#ff7a00" :loading="starting" @click="start">开始刷题</van-button>
      </div>
    </div>

    <!-- 章节选择弹层（与录题/闪卡同款列表式 picker） -->
    <van-popup
      class="hf-popup"
      v-model:show="showChapterPicker"
      :position="popupPosition"
      round
      :style="{ maxHeight: '60%' }"
    >
      <div class="picker">
        <div class="picker__title">选择章节</div>
        <div class="picker__list">
          <div
            v-for="c in chapters"
            :key="c.id"
            class="picker-item"
            :class="{ active: c.id === chapterId }"
            @click="pickChapter(c.id)"
          >
            <span>{{ c.name }}</span>
            <van-icon v-if="c.id === chapterId" name="success" color="#ff7a00" />
          </div>
          <div v-if="!chapters.length" class="empty-tip">该科目还没有章节，去科目页创建</div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
.grp {
  margin-bottom: 12px;
}

.scope-row {
  display: flex;
  gap: 8px;
  padding: 12px 16px;

  .scope-cell {
    flex: 1;
    text-align: center;
    padding: 10px 4px;
    border: 1px solid $border-color;
    border-radius: $radius-md;
    cursor: pointer;

    .s-label {
      font-size: 14px;
      font-weight: 600;
    }

    .s-hint {
      margin-top: 2px;
      font-size: 11px;
      color: $text-light;
    }

    &.on {
      border-color: $primary;
      background: rgba(255, 122, 0, 0.06);

      .s-label {
        color: $primary;
      }
    }
  }
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 4px 16px 14px;

  .chip {
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

  .chip-empty {
    font-size: 12px;
  }
}

.type-row {
  display: flex;
  gap: 8px;
  padding: 12px 16px 4px;

  .type-cell {
    flex: 1;
    text-align: center;
    padding: 8px 0;
    border: 1px solid $border-color;
    border-radius: $radius-sm;
    font-size: 14px;
    cursor: pointer;

    &.on {
      border-color: $primary;
      color: $primary;
      font-weight: 700;
      background: rgba(255, 122, 0, 0.06);
    }
  }
}

.type-note {
  padding: 6px 16px 12px;
  font-size: 12px;
}

.submit-row {
  margin-top: 20px;
}

.picker {
  padding: 18px 16px calc(18px + env(safe-area-inset-bottom));

  &__title {
    font-size: 16px;
    font-weight: 700;
    margin-bottom: 10px;
  }

  &__list {
    overflow-y: auto;
  }
}

.picker-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 4px;
  font-size: 14px;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;

  &.active {
    color: $primary;
    font-weight: 600;
  }
}
</style>
