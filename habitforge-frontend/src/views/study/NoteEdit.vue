<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import type { PickerConfirmEventParams } from 'vant'
import type { Subject, Chapter } from '@/types/study'
import {
  apiNote,
  apiCreateNote,
  apiUpdateNote,
  apiSubjects,
  apiChaptersBySubject
} from '@/api'
import MarkdownEditor from '@/components/study/MarkdownEditor.vue'
import { usePopupPosition } from '@/composables/useDesktop'

const popupPosition = usePopupPosition()
const router = useRouter()
const route = useRoute()

/** 编辑模式 /study/notes/edit/:id；新建 /study/notes/create（可带 ?subjectId=&chapterId=） */
const noteId = route.params.id as string | undefined
const isEdit = computed(() => !!noteId)

const title = ref('')
const content = ref('')
const subjectId = ref((route.query.subjectId as string) || '')
const chapterId = ref((route.query.chapterId as string) || '')
const saving = ref(false)

const subjects = ref<Subject[]>([])
const chapters = ref<Chapter[]>([])

const editorRef = ref<InstanceType<typeof MarkdownEditor> | null>(null)

/** 草稿 key 含 subjectId；编辑模式内容已落库，不启用草稿 */
const draftKey = computed(() =>
  isEdit.value ? '' : `study-note-draft:new:${subjectId.value || 'none'}`
)

const subjectColumns = computed(() =>
  subjects.value.map((s) => ({ text: s.name, value: s.id }))
)
const chapterColumns = computed(() => [
  { text: '不关联章节', value: '' },
  ...chapters.value.map((c) => ({ text: chapterLabel(c), value: c.id }))
])

/** 平铺章节按 parentId 组树算缩进 */
function chapterLabel(c: Chapter): string {
  let depth = 0
  let cur: Chapter | undefined = c
  const seen = new Set<string>()
  while (cur && cur.parentId && !seen.has(cur.id)) {
    seen.add(cur.id)
    depth++
    cur = chapters.value.find((x) => x.id === cur!.parentId)
  }
  return `${'　'.repeat(depth)}${c.name}`
}

const showSubjectPicker = ref(false)
const showChapterPicker = ref(false)

const subjectName = computed(
  () => subjects.value.find((s) => s.id === subjectId.value)?.name || ''
)
const chapterName = computed(() => {
  const c = chapters.value.find((x) => x.id === chapterId.value)
  return c ? c.name : ''
})

onMounted(async () => {
  subjects.value = await apiSubjects().catch(() => [] as Subject[])
  if (subjectId.value) loadChapters(subjectId.value)
  if (isEdit.value && noteId) {
    const note = await apiNote(noteId)
    title.value = note.title
    content.value = note.content || ''
    subjectId.value = note.subjectId
    chapterId.value = note.chapterId || ''
    loadChapters(note.subjectId)
  }
})

async function loadChapters(sid: string) {
  chapters.value = sid ? await apiChaptersBySubject(sid).catch(() => [] as Chapter[]) : []
}

function onSubjectConfirm({ selectedValues }: PickerConfirmEventParams) {
  showSubjectPicker.value = false
  const next = String(selectedValues[0] ?? '')
  if (next && next !== subjectId.value) {
    subjectId.value = next
    chapterId.value = ''
    loadChapters(next)
  }
}

function onChapterConfirm({ selectedValues }: PickerConfirmEventParams) {
  showChapterPicker.value = false
  chapterId.value = String(selectedValues[0] ?? '')
}

async function onSave() {
  if (saving.value) return
  if (!title.value.trim()) {
    showToast('请填写标题')
    return
  }
  if (!subjectId.value) {
    showToast('必选科目')
    return
  }
  saving.value = true
  try {
    if (isEdit.value && noteId) {
      await apiUpdateNote(noteId, {
        title: title.value.trim(),
        content: content.value,
        chapterId: chapterId.value || null
      })
      showToast('已保存')
      router.replace(`/study/notes/${noteId}`)
    } else {
      const created = await apiCreateNote({
        subjectId: subjectId.value,
        chapterId: chapterId.value || undefined,
        title: title.value.trim(),
        content: content.value
      })
      // 新建模式暂存图片补传；若追加了图片 Markdown 需再落库一次正文
      const appended = await editorRef.value?.flushUploads(created.id)
      if (appended) {
        await apiUpdateNote(created.id, { content: content.value }).catch(() => undefined)
      }
      editorRef.value?.clearDraft()
      showToast('已保存')
      router.replace(`/study/notes/${created.id}`)
    }
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    saving.value = false
  }
}

function onBack() {
  router.back()
}
</script>

<template>
  <MarkdownEditor
    ref="editorRef"
    v-model="content"
    :note-id="noteId"
    :draft-key="draftKey"
    :nav-title="isEdit ? '编辑笔记' : '新建笔记'"
    @save="onSave"
    @back="onBack"
  >
    <template #meta>
      <div class="card meta-card">
        <van-field
          v-model="title"
          placeholder="笔记标题"
          maxlength="200"
          clearable
          class="title-field"
        />
        <van-cell
          title="科目"
          is-link
          required
          :value="subjectName || '必选'"
          :class="{ 'value-empty': !subjectName }"
          @click="showSubjectPicker = true"
        />
        <van-cell
          title="章节"
          is-link
          :value="chapterName || '可选'"
          :class="{ 'value-empty': !chapterName, 'cell-disabled': !subjectId }"
          @click="subjectId && (showChapterPicker = true)"
        />
      </div>
    </template>
  </MarkdownEditor>

  <van-popup class="hf-popup" :show="showSubjectPicker" :position="popupPosition" round @update:show="showSubjectPicker = $event">
    <van-picker
      title="选择科目"
      :columns="subjectColumns"
      @confirm="onSubjectConfirm"
      @cancel="showSubjectPicker = false"
    />
  </van-popup>

  <van-popup class="hf-popup" :show="showChapterPicker" :position="popupPosition" round @update:show="showChapterPicker = $event">
    <van-picker
      title="选择章节"
      :columns="chapterColumns"
      :model-value="chapterId ? [chapterId] : ['']"
      @confirm="onChapterConfirm"
      @cancel="showChapterPicker = false"
    />
  </van-popup>
</template>

<style scoped lang="scss">
.meta-card {
  padding: 4px 16px;
  margin-bottom: 12px;
}

.title-field {
  :deep(.van-field__control) {
    font-size: 17px;
    font-weight: 700;
  }
}

.value-empty {
  :deep(.van-cell__value) {
    color: $text-light;
  }
}

.cell-disabled {
  pointer-events: none;
  opacity: 0.6;
}
</style>
