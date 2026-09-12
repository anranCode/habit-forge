<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { showConfirmDialog, showToast, showSuccessToast, type UploaderFileListItem } from 'vant'
import { apiUploadNoteImage } from '@/api'
import { imageUrl } from '@/utils/image'
import MarkdownPreview from './MarkdownPreview.vue'

const props = defineProps<{
  /** Markdown 原文 */
  modelValue: string
  /** 已有笔记 id：选图即传；为空 = 新建模式暂存本地，保存后 flushUploads 补传 */
  noteId?: string
  /** localStorage 草稿 key（含 subjectId）；空串 = 禁用草稿 */
  draftKey?: string
  /** 顶部导航标题 */
  navTitle?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  save: []
  back: []
}>()

const taRef = ref<HTMLTextAreaElement>()
const uploaderRef = ref<{ $el: HTMLElement } | null>(null)
const activeTab = ref<'edit' | 'preview'>('edit')

// ============ 光标工具栏 ============

/**
 * 替换 [start,end) 区间文本：优先 document.execCommand('insertText')（保原生撤销栈），
 * 不支持时回退 value 拼接 + 手动恢复光标。
 */
function applyEdit(start: number, end: number, text: string, selFrom: number, selTo: number) {
  const el = taRef.value
  if (!el) return
  el.focus()
  let ok = false
  try {
    el.setSelectionRange(start, end)
    ok = document.execCommand('insertText', false, text)
  } catch {
    ok = false
  }
  if (!ok) {
    const v = el.value
    emit('update:modelValue', v.slice(0, start) + text + v.slice(end))
  }
  nextTick(() => taRef.value?.setSelectionRange(selFrom, selTo))
}

function wrapSelection(before: string, after: string, placeholder: string) {
  const el = taRef.value
  if (!el) return
  const s = el.selectionStart
  const e = el.selectionEnd
  const inner = el.value.slice(s, e) || placeholder
  applyEdit(s, e, before + inner + after, s + before.length, s + before.length + inner.length)
}

/** 在当前行行首插入前缀语法（标题/列表/引用） */
function prefixLine(prefix: string) {
  const el = taRef.value
  if (!el) return
  const s = el.selectionStart
  const v = el.value
  let ls = s
  while (ls > 0 && v[ls - 1] !== '\n') ls--
  applyEdit(ls, ls, prefix, s + prefix.length, Math.max(el.selectionEnd, s) + prefix.length)
}

const toolH2 = () => prefixLine('## ')
const toolH3 = () => prefixLine('### ')
const toolBold = () => wrapSelection('**', '**', '加粗')
const toolItalic = () => wrapSelection('*', '*', '斜体')
const toolList = () => prefixLine('- ')
const toolQuote = () => prefixLine('> ')

function toolCode() {
  const el = taRef.value
  if (!el) return
  const s = el.selectionStart
  const e = el.selectionEnd
  const sel = el.value.slice(s, e)
  if (sel.includes('\n')) {
    wrapSelection('```\n', '\n```', '代码块')
  } else {
    wrapSelection('`', '`', 'code')
  }
}

// ============ 图片：即传 / pending 队列（仿 JournalEdit） ============

interface PendingImage {
  id: number
  file: File
  name: string
  status: 'pending' | 'uploading' | 'failed'
}
let seq = 0
const pendingImages = ref<PendingImage[]>([])

function pickImage() {
  const input = uploaderRef.value?.$el?.querySelector<HTMLInputElement>('input[type="file"]')
  input?.click()
}

function imageMarkdown(name: string, objectKey: string): string {
  const alt = name.replace(/\.[^.]+$/, '')
  return `![${alt}](${imageUrl(objectKey)})`
}

/** 在光标处插入一行文本 */
function insertAtCursor(text: string) {
  const el = taRef.value
  const pos = el ? el.selectionStart : (props.modelValue ?? '').length
  const v = props.modelValue ?? ''
  const pad = v && !v.endsWith('\n') && pos === v.length ? '\n\n' : pos === v.length ? '\n' : ''
  applyEdit(pos, pos, pad + text + '\n', pos + pad.length + text.length + 1, pos + pad.length + text.length + 1)
}

function appendToBody(text: string) {
  const v = props.modelValue ?? ''
  const sep = v ? (v.endsWith('\n\n') ? '' : v.endsWith('\n') ? '\n' : '\n\n') : ''
  emit('update:modelValue', v + sep + text)
}

async function uploadOne(noteId: string, p: PendingImage): Promise<boolean> {
  p.status = 'uploading'
  try {
    const img = await apiUploadNoteImage(noteId, p.file)
    appendToBody(imageMarkdown(p.name, img.objectKey))
    return true
  } catch {
    p.status = 'failed'
    return false
  }
}

async function onAfterRead(item: UploaderFileListItem | UploaderFileListItem[]) {
  const items = Array.isArray(item) ? item : [item]
  for (const it of items) {
    const file = it.file as File | undefined
    if (!file) continue
    if (props.noteId) {
      // 编辑模式：即选即传，成功后插入光标处
      try {
        const img = await apiUploadNoteImage(props.noteId, file)
        insertAtCursor(imageMarkdown(file.name, img.objectKey))
      } catch {
        /* 拦截器已提示 */
      }
    } else {
      // 新建模式：暂存本地，保存拿到 id 后 flushUploads 补传
      pendingImages.value.push({ id: ++seq, file, name: file.name, status: 'pending' })
    }
  }
}

/**
 * 新建保存后补传暂存图片。成功数 > 0 时返回 true（正文已追加图片 Markdown，
 * 调用方应以最新 modelValue 再落库一次）。
 */
async function flushUploads(noteId: string): Promise<boolean> {
  let appended = false
  const queue = pendingImages.value.filter((p) => p.status !== 'uploading')
  for (const p of queue) {
    if (await uploadOne(noteId, p)) appended = true
  }
  pendingImages.value = pendingImages.value.filter((p) => p.status === 'failed')
  if (queue.length) {
    const failed = pendingImages.value.length
    if (failed) showToast(`${failed} 张图片上传失败，可稍后在编辑页重传`)
    else showSuccessToast('图片已上传')
  }
  return appended
}

// ============ 草稿（仅 draftKey 非空时启用） ============

let draftTimer: ReturnType<typeof setTimeout> | undefined
let draftDisabled = false

function writeDraft() {
  if (!props.draftKey || draftDisabled) return
  clearTimeout(draftTimer)
  draftTimer = setTimeout(() => {
    const key = props.draftKey
    if (!key) return
    const v = props.modelValue ?? ''
    if (v.trim()) localStorage.setItem(key, v)
    else localStorage.removeItem(key)
  }, 500)
}

function clearDraft() {
  draftDisabled = true
  clearTimeout(draftTimer)
  if (props.draftKey) localStorage.removeItem(props.draftKey)
}

watch(() => props.modelValue, writeDraft)

onMounted(async () => {
  const key = props.draftKey
  if (!key) return
  const saved = localStorage.getItem(key)
  if (saved && saved !== props.modelValue) {
    try {
      await showConfirmDialog({
        title: '恢复草稿',
        message: '检测到上次未保存的内容，是否恢复？'
      })
      emit('update:modelValue', saved)
    } catch {
      localStorage.removeItem(key)
    }
  }
})

onUnmounted(() => clearTimeout(draftTimer))

defineExpose({ flushUploads, clearDraft })
</script>

<template>
  <div class="md-editor">
    <van-nav-bar
      :title="navTitle || '笔记'"
      left-arrow
      fixed
      placeholder
      @click-left="emit('back')"
    >
      <template #right>
        <span class="save-btn" @click="emit('save')">保存</span>
      </template>
    </van-nav-bar>

    <!-- 标题/科目等表单区 -->
    <div class="meta-slot"><slot name="meta" /></div>

    <van-tabs v-model:active="activeTab" class="mode-tabs" line-width="20" swipeable>
      <van-tab title="编辑" name="edit" />
      <van-tab title="预览" name="preview" />
    </van-tabs>

    <div v-show="activeTab === 'edit'" class="toolbar" @mousedown.prevent>
      <button class="tb" @click="toolH2">H2</button>
      <button class="tb" @click="toolH3">H3</button>
      <button class="tb b" @click="toolBold">B</button>
      <button class="tb i" @click="toolItalic">I</button>
      <button class="tb" @click="toolList">• 列表</button>
      <button class="tb" @click="toolQuote">❝ 引用</button>
      <button class="tb mono" @click="toolCode">&lt;/&gt;</button>
      <button class="tb" @click="pickImage">🖼 图片</button>
    </div>

    <!-- 始终挂载（仅 v-show 切换），保证切到预览态时补传/插入仍能操作光标 -->
    <textarea
      ref="taRef"
      v-show="activeTab === 'edit'"
      class="md-input"
      :value="modelValue"
      placeholder="支持 Markdown：# 标题、**加粗**、- 列表、> 引用、``` 代码块…&#10;图片可点击工具栏「图片」插入"
      @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    />

    <div v-show="activeTab === 'preview'" class="preview-pane card">
      <MarkdownPreview :content="modelValue || '*（暂无内容）*'" />
    </div>

    <!-- 隐藏的 van-uploader：图片按钮代理触发其文件选择 -->
    <div style="display: none">
      <van-uploader
        ref="uploaderRef"
        :max-size="5 * 1024 * 1024"
        accept="image/jpeg,image/png,image/webp,image/gif"
        multiple
        :after-read="onAfterRead"
        @oversize="showToast('图片大小不能超过 5MB')"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.md-editor {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: $bg-page;
}

.save-btn {
  color: $primary;
  font-size: 14px;
  font-weight: 600;
}

.meta-slot {
  padding: 12px 16px 0;
}

.mode-tabs {
  :deep(.van-tabs__wrap) {
    background: transparent;
  }
}

.toolbar {
  display: flex;
  gap: 6px;
  padding: 8px 12px;
  overflow-x: auto;
  background: $bg-card;
  border-top: 1px solid #f0f2f5;

  .tb {
    flex: none;
    border: none;
    background: #f6f7fb;
    color: $text-main;
    font-size: 13px;
    padding: 6px 12px;
    border-radius: 8px;
    cursor: pointer;

    &:active {
      background: rgba(255, 122, 0, 0.12);
      color: $primary;
    }

    &.b {
      font-weight: 800;
    }

    &.i {
      font-style: italic;
    }

    &.mono {
      font-family: Consolas, Menlo, monospace;
    }
  }
}

.md-input {
  flex: 1;
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  padding: 14px 16px calc(14px + env(safe-area-inset-bottom));
  font-size: 15px;
  line-height: 1.75;
  font-family: inherit;
  color: $text-main;
  background: $bg-card;
  min-height: 320px;
}

.preview-pane {
  flex: 1;
  margin: 12px 16px calc(12px + env(safe-area-inset-bottom));
  overflow-y: auto;
}
</style>
