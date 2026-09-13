<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import { showConfirmDialog, showSuccessToast, showToast, showImagePreview } from 'vant'
import dayjs from 'dayjs'
import type { JournalDetail, Reflection } from '@/types/journal'
import type { Habit } from '@/types/habit'
import {
  apiJournalDetail,
  apiDeleteJournal,
  apiLinkHabit,
  apiUnlinkHabit,
  apiListHabits,
  apiReflectionsByJournal
} from '@/api'
import { moodEmoji, moodLabel, feelingEmoji, categoryEmoji } from '@/utils/format'
import { imageUrl } from '@/utils/image'
import { usePopupPosition } from '@/composables/useDesktop'

const popupPosition = usePopupPosition()
const router = useRouter()

/** 无历史可退时按路由的 meta.backTo 兜底（桌面端可直接深链进详情页） */
const goBack = useGoBack()
const route = useRoute()
const journalId = route.params.id as string

const journal = ref<JournalDetail | null>(null)
const reflections = ref<Reflection[]>([])
const allHabits = ref<Habit[]>([])
const showHabitPicker = ref(false)

onMounted(load)

async function load() {
  journal.value = await apiJournalDetail(journalId)
  reflections.value = await apiReflectionsByJournal(journalId).catch(() => [] as Reflection[])
}

/** 未关联的习惯（供添加关联） */
function pickHabitToAdd() {
  apiListHabits(true)
    .then((list) => {
      const linked = new Set(journal.value?.habits.map((h) => h.id))
      allHabits.value = list.filter((h) => !linked.has(h.id))
      if (!allHabits.value.length) {
        showToast('没有可关联的习惯')
        return
      }
      showHabitPicker.value = true
    })
    .catch(() => undefined)
}

async function onPickHabit({ selectedOptions }: { selectedOptions: Array<{ text?: string; value?: string }> }) {
  showHabitPicker.value = false
  const opt = selectedOptions?.[0]
  if (!opt?.value || !journal.value) return
  try {
    await apiLinkHabit(journal.value.id, opt.value)
    await load()
    showSuccessToast('已关联')
  } catch {
    /* 拦截器已提示 */
  }
}

async function unlink(habitId: string) {
  try {
    await apiUnlinkHabit(journalId, habitId)
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

function previewImages(index: number) {
  if (!journal.value) return
  showImagePreview(journal.value.images.map((i) => imageUrl(i.objectKey)), index)
}

async function removeJournal() {
  try {
    await showConfirmDialog({ title: '删除记录', message: '删除后不可恢复，关联的心得也会一并删除。确定吗？' })
    await apiDeleteJournal(journalId)
    showSuccessToast('已删除')
    router.replace('/record')
  } catch {
    /* 取消 */
  }
}

function reflectionTexts(r: Reflection): string[] {
  const out: string[] = []
  if (r.reason) out.push(`原因：${r.reason}`)
  if (r.obstacle) out.push(`困难：${r.obstacle}`)
  if (r.learning) out.push(`学到：${r.learning}`)
  if (r.adjustment) out.push(`调整：${r.adjustment}`)
  return out
}
</script>

<template>
  <div v-if="journal">
    <van-nav-bar :title="dayjs(journal.journalDate).format('M月D日') + ' 的记录'" left-arrow @click-left="goBack">
      <template #right>
        <span class="nav-actions">
          <van-icon name="edit" size="18" @click="router.push(`/record/edit/${journal.id}`)" />
          <van-icon name="delete-o" size="18" style="margin-left: 14px" @click="removeJournal" />
        </span>
      </template>
    </van-nav-bar>

    <!-- 桌面端两栏：左边正文与图片，右边关联习惯与当日心得。
         移动端 .split / .col-* 没有任何声明，DOM 顺序也不变，渲染与改造前一致 -->
    <div class="page-body split is-rail">
      <div class="col-main">
          <!-- 标题 / 心情 / 正文 -->
        <div class="card">
          <div class="head-line">
            <span class="date">{{ dayjs(journal.journalDate).format('YYYY年M月D日 ddd') }}</span>
            <span v-if="journal.mood" class="mood">{{ moodEmoji(journal.mood) }} {{ moodLabel(journal.mood) }}</span>
          </div>
          <div v-if="journal.title" class="title">{{ journal.title }}</div>
          <div v-if="journal.content" class="content">{{ journal.content }}</div>
          <div v-if="!journal.title && !journal.content && !journal.images.length" class="text-light empty">
            这一天还没有正文，点右上角编辑补充
          </div>

          <!-- 图片网格 -->
          <div v-if="journal.images.length" class="img-grid">
            <van-image
              v-for="(img, idx) in journal.images"
              :key="img.id"
              :src="imageUrl(img.objectKey)"
              width="100%"
              height="100"
              fit="cover"
              radius="8"
              @click="previewImages(idx)"
            />
          </div>
        </div>

      </div>

      <div class="col-side">
          <!-- 关联习惯 -->
        <div class="card">
          <div class="flex-between">
            <span class="sec-title">关联的习惯</span>
            <span class="add-link" @click="pickHabitToAdd">＋ 添加</span>
      </div>
        </div>
        <div v-if="journal.habits.length" class="chips">
          <div v-for="h in journal.habits" :key="h.id" class="chip" @click="router.push(`/habits/${h.id}`)">
            <span>{{ categoryEmoji[(h.category as keyof typeof categoryEmoji)] || '✨' }} {{ h.name }}</span>
            <van-icon name="cross" class="x" @click.stop="unlink(h.id)" />
          </div>
        </div>
        <div v-else class="text-light empty">还没有关联习惯</div>
      </div>

      <!-- 当日心得 -->
      <div class="card">
        <div class="sec-title">当日心得</div>
        <template v-if="reflections.length">
          <div v-for="r in reflections" :key="r.id" class="reflection" @click="router.push(`/habits/${r.habitId}`)">
            <div class="flex-between">
              <span class="r-name">{{ r.habitName || '习惯' }}</span>
              <van-tag v-if="r.result === 1" type="success" round>✅ 完成</van-tag>
              <van-tag v-else type="danger" round>❌ 未完成</van-tag>
            </div>
            <div class="r-meta">
              <span v-if="r.feeling">{{ feelingEmoji(r.feeling) }} 感受</span>
              <span v-if="r.difficulty">⭐ {{ r.difficulty }} 难度</span>
            </div>
            <div v-for="(t, i) in reflectionTexts(r)" :key="i" class="r-text">{{ t }}</div>
          </div>
        </template>
        <div v-else class="text-light empty">还没有心得，回今日页给习惯记一笔吧</div>
      </div>
    </div>

    <!-- 添加关联习惯 -->
    <van-popup class="hf-popup" v-model:show="showHabitPicker" :position="popupPosition" round>
      <van-picker
        :columns="allHabits.map((h) => ({ text: h.name, value: h.id }))"
        title="选择要关联的习惯"
        @confirm="onPickHabit"
        @cancel="showHabitPicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
.nav-actions {
  display: inline-flex;
  align-items: center;
}

.head-line {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .date {
    font-weight: 700;
    font-size: 15px;
  }

  .mood {
    font-size: 13px;
    color: $text-light;
  }
}

.title {
  margin-top: 12px;
  font-size: 17px;
  font-weight: 700;
}

.content {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.img-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 12px;
}

.sec-title {
  font-size: 14px;
  font-weight: 700;
}

.add-link {
  font-size: 12px;
  color: $primary;
  cursor: pointer;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;

  .chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 12px;
    border-radius: 999px;
    background: #f6f7fb;
    font-size: 13px;
    cursor: pointer;

    .x {
      color: $text-light;
      font-size: 12px;
    }
  }
}

.reflection {
  padding: 12px;
  background: #f9fafc;
  border-radius: 10px;
  margin-top: 10px;

  .r-name {
    font-size: 14px;
    font-weight: 600;
  }

  .r-meta {
    display: flex;
    gap: 12px;
    margin-top: 6px;
    font-size: 12px;
    color: $text-light;
  }

  .r-text {
    margin-top: 6px;
    font-size: 13px;
    color: #5d6d7e;
    line-height: 1.6;
  }
}

.empty {
  margin-top: 10px;
  font-size: 13px;
}
</style>
