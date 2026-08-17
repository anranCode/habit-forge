<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { showSuccessToast, showToast, type UploaderFileListItem } from 'vant'
import dayjs from 'dayjs'
import type { Habit } from '@/types/habit'
import type { JournalDetail } from '@/types/journal'
import {
  apiJournalDetail,
  apiCreateJournal,
  apiUpdateJournal,
  apiJournalByDate,
  apiListHabits,
  apiLinkHabit,
  apiUnlinkHabit,
  apiUploadImage,
  apiDeleteImage
} from '@/api'
import { imageUrl } from '@/utils/image'

const router = useRouter()
const route = useRoute()

/** 编辑模式：路由 /record/edit/:id；创建模式：/record/create */
const journalId = route.params.id as string | undefined
const isEdit = computed(() => !!journalId)

const journal = ref<JournalDetail | null>(null)
const date = ref((route.query.date as string) || dayjs().format('YYYY-MM-DD'))
const title = ref('')
const mood = ref<number | undefined>(undefined)
const content = ref('')
const saving = ref(false)

// 关联习惯
const allHabits = ref<Habit[]>([])
const selectedHabits = ref<string[]>([])
const habitsTouched = ref(false)

// 图片
interface UploadItem extends UploaderFileListItem {
  imageId?: string
  objectKey?: string
}
const fileList = ref<UploadItem[]>([])
const showDatePicker = ref(false)
const pickerValue = ref<string[]>(dayjs(date.value).format('YYYY-MM-DD').split('-'))

const moods = [
  { v: 1, emoji: '😊', label: '好' },
  { v: 2, emoji: '😐', label: '一般' },
  { v: 3, emoji: '😫', label: '疲惫' }
]

const maxDate = dayjs().format('YYYY-MM-DD')

onMounted(load)

async function load() {
  allHabits.value = await apiListHabits(true).catch(() => [] as Habit[])
  if (isEdit.value && journalId) {
    journal.value = await apiJournalDetail(journalId)
    date.value = journal.value.journalDate
    title.value = journal.value.title || ''
    mood.value = journal.value.mood ?? undefined
    content.value = journal.value.content || ''
    selectedHabits.value = journal.value.habits.map((h) => h.id)
    fileList.value = journal.value.images.map((img) => ({
      url: imageUrl(img.objectKey),
      status: 'done',
      imageId: img.id,
      objectKey: img.objectKey,
      isImage: true
    }))
  } else {
    // 创建模式：query 带 habitId 时预选
    const preselect = route.query.habitId as string | undefined
    if (preselect) {
      selectedHabits.value = [preselect]
      habitsTouched.value = true
    }
  }
}

function onDateConfirm({ selectedValues }: { selectedValues: string[] }) {
  showDatePicker.value = false
  const picked = selectedValues.join('-')
  if (picked === date.value) return
  if (!isEdit.value) {
    // 目标日期已有日记则直接跳转编辑，避免保存时撞唯一键
    apiJournalByDate(picked)
      .then((list) => {
        if (list.length) {
          showToast('这一天已有记录，为你转到编辑页')
          router.replace(`/record/edit/${list[0].id}`)
        } else {
          date.value = picked
        }
      })
      .catch(() => {
        date.value = picked
      })
  }
}

function toggleHabit(id: string) {
  habitsTouched.value = true
  if (!isEdit.value) {
    // 创建模式：仅本地选择
    selectedHabits.value = selectedHabits.value.includes(id)
      ? selectedHabits.value.filter((x) => x !== id)
      : [...selectedHabits.value, id]
    return
  }
  // 编辑模式：即时增删关联
  if (!journal.value) return
  if (selectedHabits.value.includes(id)) {
    apiUnlinkHabit(journal.value.id, id)
      .then(() => {
        selectedHabits.value = selectedHabits.value.filter((x) => x !== id)
      })
      .catch(() => undefined)
  } else {
    apiLinkHabit(journal.value.id, id)
      .then(() => {
        selectedHabits.value = [...selectedHabits.value, id]
      })
      .catch(() => undefined)
  }
}

// ============ 图片上传 ============

async function onAfterRead(item: UploadItem | UploadItem[]) {
  const items = Array.isArray(item) ? item : [item]
  for (const it of items) {
    if (isEdit.value && journal.value) {
      await uploadNow(it)
    }
    // 创建模式保持 pending，保存后统一上传
  }
}

async function uploadNow(it: UploadItem) {
  if (!journal.value || !it.file) return
  it.status = 'uploading'
  it.message = '上传中'
  try {
    const img = await apiUploadImage(journal.value.id, it.file)
    it.status = 'done'
    it.message = ''
    it.imageId = img.id
    it.objectKey = img.objectKey
    it.url = imageUrl(img.objectKey)
  } catch {
    it.status = 'failed'
    it.message = '失败'
  }
}

function onDelete(item: UploadItem) {
  if (isEdit.value && journal.value && item.imageId) {
    apiDeleteImage(journal.value.id, item.imageId).catch(() => undefined)
  }
}

// ============ 保存 ============

async function onSave() {
  if (saving.value) return
  saving.value = true
  try {
    if (isEdit.value && journal.value) {
      await apiUpdateJournal(journal.value.id, {
        title: title.value.trim() || undefined,
        mood: mood.value,
        content: content.value.trim() || undefined
      })
      showSuccessToast('已保存')
      router.back()
    } else {
      const created = await apiCreateJournal({
        journalDate: date.value,
        title: title.value.trim() || undefined,
        mood: mood.value,
        content: content.value.trim() || undefined,
        // 未手动选择过 -> null 由后端自动关联当日已打卡习惯
        habitIds: habitsTouched.value ? selectedHabits.value : null
      })
      journal.value = created
      // 创建模式下补传暂存的图片
      for (const it of fileList.value) {
        if (!it.imageId && it.file) {
          await uploadNow(it)
        }
      }
      showSuccessToast('记录已保存')
      router.replace(`/record/${created.id}`)
    }
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    saving.value = false
  }
}

// 离开页面前清理本地预览 blob URL
onBeforeRouteLeave(() => {
  fileList.value.forEach((f) => {
    if (f.url && f.url.startsWith('blob:')) URL.revokeObjectURL(f.url)
  })
})
</script>

<template>
  <div>
    <van-nav-bar :title="isEdit ? '编辑记录' : '写记录'" left-arrow @click-left="router.back()">
      <template #right>
        <span class="save-btn" @click="onSave">保存</span>
      </template>
    </van-nav-bar>

    <div class="page-body">
      <!-- 日期 -->
      <div class="card">
        <div class="date-row" @click="!isEdit && (showDatePicker = true)">
          <span class="d">📅 {{ dayjs(date).format('YYYY年M月D日 ddd') }}</span>
          <span v-if="!isEdit" class="text-light change">修改 ›</span>
        </div>

        <!-- 心情 -->
        <div class="field-label">今天的心情</div>
        <div class="mood-row">
          <div
            v-for="m in moods"
            :key="m.v"
            class="mood-item"
            :class="{ active: mood === m.v }"
            @click="mood = mood === m.v ? undefined : m.v"
          >
            <span class="e">{{ m.emoji }}</span>
            <span>{{ m.label }}</span>
          </div>
        </div>

        <!-- 标题 -->
        <van-field v-model="title" placeholder="给这一天起个标题（可选）" maxlength="200" clearable />

        <!-- 正文 -->
        <van-field
          v-model="content"
          type="textarea"
          rows="4"
          autosize
          maxlength="10000"
          placeholder="今天过得怎么样？写下任何想记录的…"
        />
      </div>

      <!-- 关联习惯 -->
      <div class="card">
        <div class="field-label">关联的习惯</div>
        <div v-if="allHabits.length" class="chips">
          <div
            v-for="h in allHabits"
            :key="h.id"
            class="chip"
            :class="{ active: selectedHabits.includes(h.id) }"
            @click="toggleHabit(h.id)"
          >
            {{ h.name }}
          </div>
        </div>
        <div v-else class="text-light hint">还没有进行中的习惯</div>
        <div v-if="!isEdit && !habitsTouched" class="text-light hint">未手动选择时，将自动关联今天已打卡的习惯</div>
      </div>

      <!-- 图片 -->
      <div class="card">
        <div class="field-label">图片</div>
        <van-uploader
          v-model="fileList"
          :max-count="9"
          :max-size="5 * 1024 * 1024"
          accept="image/jpeg,image/png,image/webp,image/gif"
          multiple
          :after-read="onAfterRead"
          @delete="onDelete"
          @oversize="showToast('图片大小不能超过 5MB')"
        />
        <div v-if="!isEdit" class="text-light hint">新建时先选好的图片会在保存时一并上传</div>
      </div>

      <van-button block type="primary" color="#ff7a00" round :loading="saving" @click="onSave">
        {{ isEdit ? '保存修改' : '保存记录' }}
      </van-button>
    </div>

    <!-- 日期选择（仅创建模式） -->
    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-date-picker
        v-model="pickerValue"
        title="选择日期"
        :min-date="new Date('2024-01-01')"
        :max-date="new Date(maxDate)"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
.save-btn {
  color: $primary;
  font-size: 14px;
  font-weight: 600;
}

.date-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f2f5;

  .d {
    font-weight: 700;
    font-size: 15px;
  }

  .change {
    font-size: 12px;
  }
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  margin: 14px 0 8px;
}

.mood-row {
  display: flex;
  gap: 8px;

  .mood-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding: 10px 0;
    border-radius: 10px;
    background: #f6f7fb;
    font-size: 12px;
    color: $text-light;
    cursor: pointer;

    .e {
      font-size: 20px;
    }

    &.active {
      background: rgba(255, 122, 0, 0.1);
      color: $primary;
      font-weight: 700;
    }
  }
}

:deep(.van-cell) {
  padding-left: 0;
  padding-right: 0;

  &::after {
    display: none;
  }
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  .chip {
    padding: 6px 14px;
    border-radius: 999px;
    background: #f6f7fb;
    font-size: 13px;
    color: $text-light;
    cursor: pointer;

    &.active {
      background: rgba(255, 122, 0, 0.12);
      color: $primary;
      font-weight: 600;
    }
  }
}

.hint {
  margin-top: 8px;
  font-size: 12px;
}
</style>
