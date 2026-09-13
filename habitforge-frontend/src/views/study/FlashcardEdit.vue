<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import type { Subject, Chapter } from '@/types/study'
import { apiSubjects, apiChaptersBySubject, apiCreateFlashcard } from '@/api'
import { usePopupPosition } from '@/composables/useDesktop'

const popupPosition = usePopupPosition()
const route = useRoute()
const router = useRouter()

const subjects = ref<Subject[]>([])
const chapters = ref<Chapter[]>([])

const subjectId = ref('')
const chapterId = ref('')
const front = ref('')
const back = ref('')
const saving = ref(false)

const showSubjectPicker = ref(false)
const showChapterPicker = ref(false)

const subjectName = computed(() => subjects.value.find((s) => s.id === subjectId.value)?.name || '')
const chapterName = computed(() => chapters.value.find((c) => c.id === chapterId.value)?.name || '')

onMounted(async () => {
  subjects.value = await apiSubjects().catch(() => [] as Subject[])
  // ?subjectId=&chapterId= 预选（从科目页/章节页跳来）
  const preSubject = (route.query.subjectId as string) || ''
  const preChapter = (route.query.chapterId as string) || ''
  if (preSubject && subjects.value.some((s) => s.id === preSubject)) {
    subjectId.value = preSubject
    await loadChapters(preSubject)
    if (preChapter && chapters.value.some((c) => c.id === preChapter)) {
      chapterId.value = preChapter
    }
  }
})

async function loadChapters(id: string) {
  chapters.value = await apiChaptersBySubject(id).catch(() => [] as Chapter[])
}

function pickSubject(id: string) {
  if (id !== subjectId.value) {
    subjectId.value = id
    chapterId.value = ''
    chapters.value = []
    if (id) loadChapters(id)
  }
  showSubjectPicker.value = false
}

function pickChapter(id: string) {
  chapterId.value = id // '' = 不关联章节
  showChapterPicker.value = false
}

async function onSubmit() {
  if (saving.value) return
  if (!subjectId.value) {
    showToast('请选择科目')
    return
  }
  saving.value = true
  try {
    await apiCreateFlashcard({
      subjectId: subjectId.value,
      chapterId: chapterId.value || undefined,
      front: front.value.trim(),
      back: back.value.trim()
    })
    showSuccessToast('闪卡已创建')
    router.back()
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="新建闪卡" left-arrow @click-left="router.back()" />

    <div class="page-body">
      <van-form @submit="onSubmit">
        <div class="card">
          <van-cell
            title="科目"
            :value="subjectName || '必选'"
            :class="!subjectName ? 'is-placeholder' : ''"
            is-link
            @click="showSubjectPicker = true"
          />
          <van-cell
            title="章节"
            :value="subjectId ? chapterName || '可选' : '先选科目'"
            :class="!chapterName ? 'is-placeholder' : ''"
            :is-link="!!subjectId"
            :clickable="!!subjectId"
            @click="subjectId && (showChapterPicker = true)"
          />
        </div>

        <div class="card">
          <div class="field-label">正面（问题）<span class="req">*</span></div>
          <van-field
            v-model="front"
            name="front"
            type="textarea"
            rows="3"
            autosize
            maxlength="500"
            show-word-limit
            placeholder="如：SM-2 中 rating=1 时 interval 置为多少？"
            :rules="[{ required: true, message: '请填写卡片正面' }]"
          />
          <div class="field-label">背面（答案）<span class="req">*</span></div>
          <van-field
            v-model="back"
            name="back"
            type="textarea"
            rows="3"
            autosize
            maxlength="1000"
            show-word-limit
            placeholder="答案支持 Markdown 原文，复习时按纯文本展示"
            :rules="[{ required: true, message: '请填写卡片背面' }]"
          />
        </div>

        <van-button block type="primary" color="#ff7a00" round native-type="submit" :loading="saving">
          创建闪卡
        </van-button>
      </van-form>
    </div>

    <!-- 科目选择 -->
    <van-popup class="hf-popup" v-model:show="showSubjectPicker" :position="popupPosition" round :style="{ maxHeight: '60%' }">
      <div class="picker">
        <div class="picker__title">选择科目</div>
        <div class="picker__list">
          <div
            v-for="s in subjects"
            :key="s.id"
            class="picker-item"
            :class="{ active: s.id === subjectId }"
            @click="pickSubject(s.id)"
          >
            <span>{{ s.name }}</span>
            <van-icon v-if="s.id === subjectId" name="success" color="#ff7a00" />
          </div>
          <div v-if="!subjects.length" class="empty-tip">还没有科目，先去创建一个吧</div>
        </div>
      </div>
    </van-popup>

    <!-- 章节选择（级联于已选科目） -->
    <van-popup class="hf-popup" v-model:show="showChapterPicker" :position="popupPosition" round :style="{ maxHeight: '60%' }">
      <div class="picker">
        <div class="picker__title">选择章节（可选）</div>
        <div class="picker__list">
          <div class="picker-item" :class="{ active: !chapterId }" @click="pickChapter('')">
            <span>不关联章节</span>
            <van-icon v-if="!chapterId" name="success" color="#ff7a00" />
          </div>
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
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
.field-label {
  font-size: 13px;
  font-weight: 600;
  margin: 4px 0 8px;

  .req {
    color: $danger;
  }
}

.field-label + .field-label,
:deep(.van-cell) + .field-label {
  margin-top: 14px;
}

:deep(.van-cell) {
  padding-left: 0;
  padding-right: 0;

  &:not(:last-child)::after {
    left: 0;
    right: 0;
  }

  &.is-placeholder .van-cell__value {
    color: $text-light;
  }
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
