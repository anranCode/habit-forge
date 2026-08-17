<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { useUserStore } from '@/stores'
import { apiUpdateProfile, apiHabitStats } from '@/api'
import type { HabitStats } from '@/types/habit'

const router = useRouter()
const userStore = useUserStore()

const stats = ref<HabitStats | null>(null)
const editingGoal = ref(false)
const goalInput = ref('')

const levelProgress = computed(() => {
  const points = userStore.user?.points || 0
  return points % 100
})

async function load() {
  if (!userStore.user) {
    await userStore.fetchMe()
  }
  stats.value = await apiHabitStats()
}

onMounted(load)
onActivated(load)

function startEditGoal() {
  goalInput.value = userStore.user?.identityGoal || ''
  editingGoal.value = true
}

async function saveGoal(action: string): Promise<boolean> {
  if (action === 'confirm') {
    await apiUpdateProfile({ identityGoal: goalInput.value })
    if (userStore.user) {
      userStore.user.identityGoal = goalInput.value
    }
    showSuccessToast('已更新身份设定')
  }
  return true
}

async function logout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出登录吗？' })
    await userStore.logout()
    router.replace('/login')
  } catch {
    /* 取消 */
  }
}
</script>

<template>
  <div>
    <div class="hero profile-hero">
      <div class="avatar">{{ (userStore.user?.username || 'U').slice(0, 1).toUpperCase() }}</div>
      <div class="name">{{ userStore.user?.username }}</div>
      <div class="email text-light">{{ userStore.user?.email }}</div>
      <div class="points-row">
        <span class="badge">⭐ {{ userStore.user?.points || 0 }} 积分</span>
        <span class="badge">Lv.{{ userStore.user?.level || 1 }}</span>
      </div>
      <van-progress
        :percentage="levelProgress"
        color="#ff7a00"
        track-color="rgba(255,255,255,0.15)"
        :show-pivot="false"
        style="margin-top: 12px"
      />
      <div class="level-tip">距离下一级还需 {{ 100 - levelProgress }} 积分</div>
    </div>

    <div class="page-body">
      <!-- 身份设定 -->
      <div class="card">
        <div class="flex-between">
          <span style="font-weight: 700">🎯 身份设定</span>
          <span class="edit-btn" @click="startEditGoal">编辑</span>
        </div>
        <div class="identity text-light" style="margin-top: 10px">
          {{ userStore.user?.identityGoal || '还没有设定 —— 决定你想成为谁，然后用小赢证明自己。' }}
        </div>
      </div>

      <!-- 数据总览 -->
      <div v-if="stats" class="card">
        <div class="flex-between"><span>累计打卡</span><b>{{ stats.totalCheckins }} 次</b></div>
        <div class="flex-between"><span>习惯总数</span><b>{{ stats.totalHabits }} 个</b></div>
        <div class="flex-between"><span>最长连续纪录</span><b>🏆 {{ stats.longestStreakOverall }} 天</b></div>
      </div>

      <van-cell-group inset style="margin-top: 12px">
        <van-cell title="关于 HabitForge" value="v1.0 · 基于《掌控习惯》四大定律" />
      </van-cell-group>

      <div style="padding: 24px 16px">
        <van-button block round plain type="danger" @click="logout">退出登录</van-button>
      </div>
    </div>

    <!-- 身份设定编辑弹窗 -->
    <van-dialog
      v-model:show="editingGoal"
      title="编辑身份设定"
      show-cancel-button
      :before-close="saveGoal"
    >
      <div style="padding: 16px">
        <van-field
          v-model="goalInput"
          type="textarea"
          rows="3"
          maxlength="500"
          show-word-limit
          placeholder="我想成为一个…"
        />
      </div>
    </van-dialog>
  </div>
</template>

<style scoped lang="scss">
.profile-hero {
  text-align: center;
  padding: 36px 24px 28px;

  .avatar {
    width: 64px;
    height: 64px;
    margin: 0 auto;
    border-radius: 50%;
    background: linear-gradient(135deg, #ff9a3d, #ff7a00);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 28px;
    font-weight: 800;
    color: #fff;
  }

  .name {
    margin-top: 12px;
    font-size: 20px;
    font-weight: 700;
  }

  .email {
    margin-top: 4px;
    font-size: 13px;
    opacity: 0.6;
  }

  .points-row {
    margin-top: 14px;
    display: flex;
    gap: 10px;
    justify-content: center;

    .badge {
      background: rgba(255, 255, 255, 0.12);
      border: 1px solid rgba(255, 255, 255, 0.2);
      padding: 4px 12px;
      border-radius: 999px;
      font-size: 12px;
    }
  }

  .level-tip {
    margin-top: 8px;
    font-size: 12px;
    opacity: 0.6;
  }
}

.edit-btn {
  color: $primary;
  font-size: 13px;
}

.card > .flex-between {
  padding: 6px 0;
  font-size: 14px;
  color: #5d6d7e;

  b {
    color: $text-main;
  }
}
</style>
