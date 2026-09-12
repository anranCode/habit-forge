<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { NoteSummary } from '@/types/note'
import type { Subject } from '@/types/study'
import { apiNotes, apiSubjects } from '@/api'
import dayjs from 'dayjs'

defineOptions({ name: 'NoteList' })

const route = useRoute()
const router = useRouter()

/** 从 ?subjectId= 过滤；'' = 全部科目 */
const subjectId = computed(() => (route.query.subjectId as string) || '')

const list = ref<NoteSummary[]>([])
const subjects = ref<Subject[]>([])
const loading = ref(false)

const subjectName = computed(() => subjects.value.find((s) => s.id === subjectId.value)?.name)

async function load() {
  loading.value = true
  try {
    list.value = await apiNotes(subjectId.value ? { subjectId: subjectId.value } : undefined)
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  subjects.value = await apiSubjects().catch(() => [] as Subject[])
  load()
})
// 编辑/新建返回后刷新
onActivated(load)

function goCreate() {
  router.push(subjectId.value ? `/study/notes/create?subjectId=${subjectId.value}` : '/study/notes/create')
}
</script>

<template>
  <div>
    <van-nav-bar :title="subjectName ? `${subjectName} · 笔记` : '笔记'" left-arrow @click-left="router.back()">
      <template #right>
        <span class="nav-btn" @click="goCreate">新建</span>
      </template>
    </van-nav-bar>

    <div class="page-body">
      <template v-if="list.length">
        <div v-for="n in list" :key="n.id" class="card note-item" @click="router.push(`/study/notes/${n.id}`)">
          <div class="flex-between">
            <span class="title">{{ n.title }}</span>
            <span class="time text-light">{{ dayjs(n.updatedAt).format('MM-DD HH:mm') }}</span>
          </div>
          <div class="excerpt text-light">{{ n.excerpt || '（无正文）' }}</div>
        </div>
      </template>
      <div v-else-if="!loading" class="empty-tip">
        <p>还没有笔记</p>
        <p class="sub">用 Markdown 整理这一科的重点，支持插入图片</p>
        <van-button size="small" type="primary" color="#ff7a00" round class="create-btn" @click="goCreate">
          新建笔记
        </van-button>
      </div>
      <div v-else class="empty-tip">加载中…</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.nav-btn {
  color: $primary;
  font-size: 14px;
  font-weight: 600;
}

.note-item {
  margin-bottom: 12px;

  .title {
    font-size: 15px;
    font-weight: 700;
  }

  .time {
    font-size: 11px;
    flex-shrink: 0;
    margin-left: 10px;
  }

  .excerpt {
    margin-top: 6px;
    font-size: 12px;
    line-height: 1.6;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

.empty-tip .sub {
  font-size: 12px;
  margin-top: 6px;
}

.create-btn {
  margin-top: 14px;
}
</style>
