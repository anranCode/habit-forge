<script setup lang="ts">
import { computed, ref } from 'vue'
import { showToast } from 'vant'
import type { Chapter, ChapterStatus } from '@/types/study'
import { usePopupPosition } from '@/composables/useDesktop'

const popupPosition = usePopupPosition()
const props = defineProps<{ chapters: Chapter[] }>()
const emit = defineEmits<{
  (e: 'status', chapter: Chapter, next: ChapterStatus): void
  (e: 'rename', chapter: Chapter, name: string): void
  (e: 'add', parent: Chapter): void
  (e: 'remove', chapter: Chapter): void
}>()

interface Row {
  chapter: Chapter
  depth: number
}

/** 平铺数据按 parentId 组树后前序展开为带 depth 的缩进行 */
const rows = computed<Row[]>(() => {
  const known = new Set(props.chapters.map((c) => c.id))
  const children = new Map<string, Chapter[]>()
  const roots: Chapter[] = []
  for (const c of props.chapters) {
    if (c.parentId && known.has(c.parentId)) {
      if (!children.has(c.parentId)) children.set(c.parentId, [])
      children.get(c.parentId)!.push(c)
    } else {
      // 顶层或父缺失（防脏数据丢节点）
      roots.push(c)
    }
  }
  const bySort = (a: Chapter, b: Chapter) => a.sortOrder - b.sortOrder
  roots.sort(bySort)
  children.forEach((arr) => arr.sort(bySort))

  const out: Row[] = []
  const visited = new Set<string>()
  const walk = (list: Chapter[], depth: number) => {
    for (const c of list) {
      if (visited.has(c.id)) continue // 防环兜底
      visited.add(c.id)
      out.push({ chapter: c, depth })
      walk(children.get(c.id) || [], depth + 1)
    }
  }
  walk(roots, 0)
  return out
})

function nextStatus(s: ChapterStatus): ChapterStatus {
  return s === 'NOT_STARTED' ? 'IN_PROGRESS' : s === 'IN_PROGRESS' ? 'DONE' : 'NOT_STARTED'
}

function cycle(c: Chapter) {
  emit('status', c, nextStatus(c.status))
}

function addChild(c: Chapter, depth: number) {
  if (depth >= 3) showToast('层级较深，建议章节不超过 3 层')
  emit('add', c)
}

// ============ 重命名弹窗 ============

const showEdit = ref(false)
const editName = ref('')
const editTarget = ref<Chapter | null>(null)

function startEdit(c: Chapter) {
  editTarget.value = c
  editName.value = c.name
  showEdit.value = true
}

function saveEdit() {
  const name = editName.value.trim()
  if (!name) {
    showToast('名称不能为空')
    return
  }
  if (editTarget.value) emit('rename', editTarget.value, name)
  showEdit.value = false
}
</script>

<template>
  <div class="chapter-tree">
    <van-swipe-cell v-for="row in rows" :key="row.chapter.id">
      <div class="row" :style="{ paddingLeft: 4 + row.depth * 18 + 'px' }">
        <span class="dot" :class="row.chapter.status" @click.stop="cycle(row.chapter)"></span>
        <span
          class="cname"
          :class="{ done: row.chapter.status === 'DONE' }"
          @click="startEdit(row.chapter)"
        >
          {{ row.chapter.name }}
        </span>
        <van-icon name="plus" class="add-btn" @click.stop="addChild(row.chapter, row.depth)" />
      </div>
      <template #right>
        <van-button square type="danger" text="删除" class="del-btn" @click="$emit('remove', row.chapter)" />
      </template>
    </van-swipe-cell>

    <van-popup class="hf-popup" v-model:show="showEdit" :position="popupPosition" round>
      <div class="edit-pop">
        <div class="pop-title">重命名章节</div>
        <van-field v-model="editName" placeholder="章节名称" maxlength="200" />
        <van-button block round type="primary" color="#ff7a00" @click="saveEdit">保存</van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
.chapter-tree {
  .row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding-top: 11px;
    padding-bottom: 11px;
    padding-right: 4px;
    border-bottom: 1px solid #f4f5f8;

    &:last-child {
      border-bottom: none;
    }
  }

  .dot {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    flex-shrink: 0;
    cursor: pointer;

    &.NOT_STARTED {
      border: 2px solid #c8ced8;
      background: transparent;
    }

    &.IN_PROGRESS {
      border: 2px solid #f39c12;
      background: rgba(243, 156, 18, 0.35);
    }

    &.DONE {
      border: 2px solid #27ae60;
      background: #27ae60;
    }
  }

  .cname {
    flex: 1;
    font-size: 14px;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    &.done {
      color: $text-light;
      text-decoration: line-through;
    }
  }

  .add-btn {
    flex-shrink: 0;
    font-size: 16px;
    color: $primary;
    padding: 4px;
    cursor: pointer;
  }

  .del-btn {
    height: 100%;
  }
}

.edit-pop {
  padding: 16px;

  .pop-title {
    font-size: 15px;
    font-weight: 700;
    text-align: center;
    padding-bottom: 12px;
  }

  .van-field {
    background: #f6f7fb;
    border-radius: 10px;
    margin-bottom: 14px;
    padding: 8px 12px;
  }
}
</style>
