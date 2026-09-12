<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import dayjs from 'dayjs'
import type { Note } from '@/types/note'
import { apiNote, apiDeleteNote } from '@/api'
import MarkdownPreview from '@/components/study/MarkdownPreview.vue'

const router = useRouter()
const route = useRoute()
const noteId = route.params.id as string

const note = ref<Note | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    note.value = await apiNote(noteId)
  } finally {
    loading.value = false
  }
})

async function onDelete() {
  if (!note.value) return
  try {
    await showConfirmDialog({
      title: '删除笔记',
      message: `确定删除「${note.value.title}」吗？正文中的图片将由后端一并清理，不可恢复。`,
      confirmButtonColor: '#e74c3c'
    })
  } catch {
    return
  }
  await apiDeleteNote(noteId)
  showSuccessToast('已删除')
  router.back()
}
</script>

<template>
  <div>
    <van-nav-bar title="笔记详情" left-arrow fixed placeholder @click-left="router.back()">
      <template #right>
        <span class="nav-btn" @click="router.push(`/study/notes/edit/${noteId}`)">编辑</span>
      </template>
    </van-nav-bar>

    <div v-if="note" class="page-body">
      <div class="card">
        <h1 class="note-title">{{ note.title }}</h1>
        <div class="note-meta text-light">
          更新于 {{ dayjs(note.updatedAt).format('YYYY-MM-DD HH:mm') }}
        </div>
        <van-divider />
        <MarkdownPreview :content="note.content" />
      </div>

      <van-button block plain type="danger" round @click="onDelete">删除笔记</van-button>
    </div>

    <div v-else-if="!loading" class="empty-tip">笔记不存在或已删除</div>
  </div>
</template>

<style scoped lang="scss">
.nav-btn {
  color: $primary;
  font-size: 14px;
  font-weight: 600;
}

.note-title {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
  margin: 0;
}

.note-meta {
  font-size: 12px;
  margin-top: 6px;
}
</style>
