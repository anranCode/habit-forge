<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { onMountedOrActivated } from '@vant/use'
import { showConfirmDialog, showSuccessToast } from 'vant'
import type { Habit } from '@/types/habit'
import HabitCard from '@/components/habit/HabitCard.vue'
import { useHabitStore } from '@/stores'
import { apiUpdateHabit, apiDeleteHabit } from '@/api'

const router = useRouter()
const habitStore = useHabitStore()

const activeTab = ref<'active' | 'archived'>('active')
const list = computed(() =>
  activeTab.value === 'archived'
    ? habitStore.allList.filter((h) => !h.isActive)
    : habitStore.allList.filter((h) => h.isActive)
)

async function load() {
  await habitStore.loadAll()
}

// keep-alive 下 onMounted 与 onActivated 首次都会触发：分别注册会让 load() 并发跑两遍
onMountedOrActivated(load)

function goDetail(h: Habit) {
  router.push(`/habits/${h.id}`)
}

async function toggleArchive(h: Habit) {
  const next = h.isActive ? 0 : 1
  await apiUpdateHabit(h.id, { isActive: next })
  h.isActive = !h.isActive
  showSuccessToast(h.isActive ? '已恢复进行' : '已归档')
}

async function removeHabit(h: Habit) {
  try {
    await showConfirmDialog({ title: '删除习惯', message: `确定删除「${h.name}」吗？打卡记录仍会保留在历史中。` })
    await apiDeleteHabit(h.id)
    await load()
    showSuccessToast('已删除')
  } catch {
    /* 取消 */
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="习惯管理">
      <template #right>
        <van-icon name="plus" size="20" color="#ff7a00" @click="router.push('/habits/create')" />
      </template>
    </van-nav-bar>

    <div class="page-body">
      <div class="flex-between" style="margin-bottom: 12px">
        <van-tabs v-model:active="activeTab" shrink style="flex: 1" :ellipsis="false">
          <van-tab title="进行中" name="active" />
          <van-tab title="已归档" name="archived" />
        </van-tabs>
      </div>

      <template v-if="list.length">
        <van-swipe-cell v-for="h in list" :key="h.id">
          <HabitCard :habit="h" @click="goDetail" />
          <template #right>
            <div class="swipe-actions">
              <van-button square :type="h.isActive ? 'warning' : 'success'" class="swipe-btn" @click="toggleArchive(h)">
                {{ h.isActive ? '归档' : '恢复' }}
              </van-button>
              <van-button square type="danger" class="swipe-btn" @click="removeHabit(h)">删除</van-button>
            </div>
          </template>
        </van-swipe-cell>
      </template>
      <div v-else class="empty-tip">
        <p>暂无习惯</p>
        <van-button size="small" type="primary" color="#ff7a00" round @click="router.push('/habits/create')">
          创建第一个习惯
        </van-button>
      </div>
    </div>

    <van-floating-bubble icon="plus" @click="router.push('/habits/create')" />
  </div>
</template>

<style scoped lang="scss">
.swipe-actions {
  display: flex;
  height: 100%;
}

.swipe-btn {
  height: 100%;
}

:deep(.van-swipe-cell) {
  margin-bottom: 12px;
  border-radius: 14px;
  overflow: hidden;

  .habit-card {
    margin-bottom: 0;
  }
}
</style>
