<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import dayjs from 'dayjs'
import type { Chapter, ChapterStatus, Subject } from '@/types/study'
import {
  apiSubjectDetail,
  apiChaptersBySubject,
  apiCreateChapter,
  apiUpdateChapter,
  apiChapterStatus,
  apiDeleteChapter
} from '@/api'
import ChapterTree from '@/components/study/ChapterTree.vue'
import { usePopupPosition } from '@/composables/useDesktop'

const popupPosition = usePopupPosition()
const route = useRoute()
const router = useRouter()
const subjectId = route.params.id as string

const subject = ref<Subject | null>(null)
const chapters = ref<Chapter[]>([])
const loading = ref(false)

const progress = computed(() => {
  const s = subject.value
  return s && s.chapterTotal ? Math.round((s.chapterDone / s.chapterTotal) * 100) : 0
})

/** 后端把已过去的考期统一钳为 0，前端按 examDate 区分「今天考试」与「已过考期」 */
const countdownText = computed(() => {
  const s = subject.value
  if (!s || s.daysLeft === null) return ''
  if (s.daysLeft === 0) return dayjs(s.examDate).isBefore(dayjs(), 'day') ? '已过考期' : '今天考试'
  return `${s.daysLeft} 天后考试`
})

async function load() {
  loading.value = true
  try {
    const [detail, chs] = await Promise.all([apiSubjectDetail(subjectId), apiChaptersBySubject(subjectId)])
    subject.value = detail
    chapters.value = chs
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ============ 新建章节弹窗（顶层 or 子章节共用） ============

const showAdd = ref(false)
const newName = ref('')
const addParentId = ref<string | null>(null)
const addParentName = ref('')

function openAdd(parent?: Chapter) {
  addParentId.value = parent ? parent.id : null
  addParentName.value = parent ? parent.name : ''
  newName.value = ''
  showAdd.value = true
}

async function saveChapter() {
  const n = newName.value.trim()
  if (!n) {
    showToast('请输入章节名称')
    return
  }
  try {
    await apiCreateChapter({ subjectId, parentId: addParentId.value, name: n })
    showAdd.value = false
    showSuccessToast('章节已添加')
    await load()
  } catch {
    /* 错误已由拦截器提示 */
  }
}

// ============ 章节状态/改名/删除 ============

async function onStatus(c: Chapter, next: ChapterStatus) {
  try {
    await apiChapterStatus(c.id, next)
    if (next === 'DONE' && c.status !== 'DONE') showSuccessToast('🎉 章节完成，积分已到账')
    await load()
  } catch {
    /* 错误已由拦截器提示（如 7003 防环） */
  }
}

async function onRename(c: Chapter, name: string) {
  try {
    await apiUpdateChapter(c.id, { name })
    await load()
  } catch {
    /* 错误已由拦截器提示 */
  }
}

async function onRemove(c: Chapter) {
  try {
    await showConfirmDialog({
      title: '删除章节',
      message: `删除「${c.name}」将同时删除其所有子章节，不可恢复，确定删除？`
    })
  } catch {
    return // 取消
  }
  try {
    await apiDeleteChapter(c.id)
    showToast('已删除')
    await load()
  } catch {
    /* 错误已由拦截器提示 */
  }
}
</script>

<template>
  <div>
    <van-nav-bar :title="subject?.name || '科目详情'" left-arrow @click-left="router.back()">
      <template #right>
        <span class="nav-btn" @click="router.push(`/study/subjects/edit/${subjectId}`)">编辑</span>
      </template>
    </van-nav-bar>

    <!-- 桌面端两栏：左边章节目录，右边学习工具入口。
         移动端 .split / .col-* 没有任何声明，DOM 顺序也不变，渲染与改造前一致 -->
    <div class="page-body split is-rail">
      <div class="col-main">
        <!-- 科目头部：进度 + 考试倒计时 -->
        <div v-if="subject" class="card head-card">
          <div class="flex-between">
            <span class="sname">{{ subject.name }}</span>
            <van-tag v-if="countdownText" round :color="countdownText === '已过考期' ? '#8a94a6' : '#ff7a00'">
              {{ countdownText }}
            </van-tag>
          </div>
          <div class="exam-line text-light">
            <span v-if="subject.examDate">
              {{ dayjs(subject.examDate).format('YYYY年M月D日') }} 考试{{ subject.examSession ? ' · ' + subject.examSession : '' }}
            </span>
            <span v-else>未设置考试日期</span>
          </div>
          <div class="flex-between prog-line">
            <span class="text-light">章节进度 {{ subject.chapterDone }}/{{ subject.chapterTotal }}</span>
            <span class="pct">{{ progress }}%</span>
          </div>
          <van-progress :percentage="progress" color="#ff7a00" :show-pivot="false" />
          <van-button
            block
            round
            plain
            type="primary"
            color="#ff7a00"
            size="small"
            class="add-chapter-btn"
            @click="openAdd()"
          >
            ＋ 新章节
          </van-button>
        </div>
        <div v-else-if="!loading" class="empty-tip">科目不存在或已删除</div>

        <!-- 章节树 -->
        <div class="section-title">📖 章节目录</div>
        <div class="card tree-card">
          <ChapterTree
            v-if="chapters.length"
            :chapters="chapters"
            @status="onStatus"
            @rename="onRename"
            @add="openAdd"
            @remove="onRemove"
          />
          <div v-else-if="loading" class="empty-tip">加载中…</div>
          <div v-else class="empty-tip small">
            还没有章节，点击上方「＋ 新章节」<br />建议按教材章/节拆分，不超过 3 层
          </div>
        </div>
        <div class="legend text-light">点击圆点切换状态：未开始 → 进行中 → 已完成</div>
      </div>

      <div class="col-side">
        <!-- 学习工具入口（角标数据来自科目实时汇总 dueCards/wrongCount） -->
        <div class="section-title">🧰 学习工具</div>
        <van-cell-group inset class="tools-group">
          <van-cell
            title="🃏 闪卡复习"
            :label="subject?.dueCards ? `今日到期 ${subject.dueCards} 张，开始复习` : '基于 SM-2 间隔重复，今日无到期'"
            is-link
            @click="router.push(`/study/review?subjectId=${subjectId}`)"
          >
            <template v-if="subject?.dueCards" #value>
              <van-badge :content="subject.dueCards" />
            </template>
          </van-cell>
          <van-cell
            title="📝 笔记"
            label="Markdown 笔记，按科目/章节归档"
            is-link
            @click="router.push(`/study/notes?subjectId=${subjectId}`)"
          />
          <van-cell
            title="❓ 题库"
            label="单选/多选/判断/简答题，按科目筛选"
            is-link
            @click="router.push(`/study/questions?subjectId=${subjectId}`)"
          />
          <van-cell
            title="📕 错题本"
            :label="subject?.wrongCount ? `待重练 ${subject.wrongCount} 道，连对 2 次自动摘除` : '记录答错的题，重练到掌握'"
            is-link
            @click="router.push(`/study/wrongs?subjectId=${subjectId}`)"
          >
            <template v-if="subject?.wrongCount" #value>
              <van-badge :content="subject.wrongCount" />
            </template>
          </van-cell>
        </van-cell-group>
      </div>
    </div>

    <!-- 新建章节弹窗 -->
    <van-popup class="hf-popup" v-model:show="showAdd" :position="popupPosition" round>
      <div class="add-pop">
        <div class="pop-title">
          新章节<span v-if="addParentName" class="parent-hint"> · 添加到「{{ addParentName }}」下</span>
        </div>
        <van-field v-model="newName" placeholder="章节名称（如：极限与连续）" maxlength="200" />
        <van-button block round type="primary" color="#ff7a00" @click="saveChapter">添加</van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
.nav-btn {
  color: $primary;
  font-size: 14px;
  font-weight: 600;
}

.head-card {
  .sname {
    font-size: 17px;
    font-weight: 800;
  }

  .exam-line {
    margin-top: 6px;
    font-size: 12px;
  }

  .prog-line {
    margin: 14px 0 8px;
    font-size: 13px;

    .pct {
      font-weight: 700;
      color: $primary;
    }
  }

  .add-chapter-btn {
    margin-top: 14px;
  }
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  margin: 16px 0 10px;
}

.tree-card {
  padding: 4px 12px;

  .empty-tip.small {
    font-size: 13px;
    line-height: 1.8;
    padding: 28px 16px;
  }
}

.legend {
  font-size: 11px;
  text-align: center;
  margin-top: 4px;
}

.tools-group {
  padding: 0;
  overflow: hidden;
  border-radius: 14px;
}

.add-pop {
  padding: 16px;

  .pop-title {
    font-size: 15px;
    font-weight: 700;
    text-align: center;
    padding-bottom: 12px;

    .parent-hint {
      font-size: 12px;
      font-weight: 400;
      color: $text-light;
    }
  }

  .van-field {
    background: #f6f7fb;
    border-radius: 10px;
    margin-bottom: 14px;
    padding: 8px 12px;
  }
}
</style>
