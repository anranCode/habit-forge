<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { StudyOverview } from '@/types/study'
import { apiStudyOverview } from '@/api'
import SubjectCard from '@/components/study/SubjectCard.vue'

const router = useRouter()
const overview = ref<StudyOverview | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    overview.value = await apiStudyOverview()
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    loading.value = false
  }
}

onMounted(load)

function goCreate() {
  router.push('/study/subjects/create')
}
</script>

<template>
  <div>
    <van-nav-bar title="学习中心" left-arrow @click-left="router.back()" />

    <div class="page-body">
      <!-- 顶部汇总条（P0 到期卡/错题/今日复习恒 0，照常渲染） -->
      <div class="card sum-bar">
        <div class="sum">
          <div class="num">{{ overview?.dueCardsTotal ?? 0 }}</div>
          <div class="lb">到期卡</div>
        </div>
        <div class="sum">
          <div class="num">{{ overview?.wrongsTotal ?? 0 }}</div>
          <div class="lb">错题</div>
        </div>
        <div class="sum">
          <div class="num">{{ overview?.reviewedToday ?? 0 }}</div>
          <div class="lb">今日复习</div>
        </div>
      </div>

      <!-- 科目卡片列表 -->
      <template v-if="overview && overview.subjects.length">
        <SubjectCard v-for="s in overview.subjects" :key="s.id" :subject="s" />
      </template>
      <div v-else-if="!loading" class="empty-tip">
        <p>先创建第一个科目</p>
        <p class="sub">把自考的科目与章节拆小，进度看得见</p>
        <van-button size="small" type="primary" color="#ff7a00" round class="create-btn" @click="goCreate">
          新建科目
        </van-button>
      </div>
      <div v-else class="empty-tip">加载中…</div>
    </div>

    <!-- 浮动新建按钮 -->
    <van-floating-bubble axis="xy" magnetic="x" @click="goCreate">
      <div class="bubble">＋</div>
    </van-floating-bubble>
  </div>
</template>

<style scoped lang="scss">
.sum-bar {
  display: flex;

  .sum {
    flex: 1;
    text-align: center;

    .num {
      font-size: 20px;
      font-weight: 800;
    }

    .lb {
      font-size: 12px;
      color: $text-light;
      margin-top: 2px;
    }
  }
}

.empty-tip .sub {
  font-size: 12px;
  margin-top: 6px;
}

.create-btn {
  margin-top: 14px;
}

.bubble {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff9a3d, #ff7a00);
  color: #fff;
  font-size: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(255, 122, 0, 0.4);
}
</style>
