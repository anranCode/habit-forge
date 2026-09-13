<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { StudyOverview } from '@/types/study'
import { apiStudyOverview } from '@/api'
import SubjectCard from '@/components/study/SubjectCard.vue'
import { useIsDesktop } from '@/composables/useDesktop'

const router = useRouter()
const isDesktop = useIsDesktop()
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
    <van-nav-bar title="学习中心" left-arrow @click-left="router.back()">
      <!-- 桌面端没有右下角的浮动气泡（main.scss 把 .van-floating-bubble 隐藏了），
           新建入口挪到标题栏右侧；空状态的正文里本来就有按钮，这里补的是非空状态。
           移动端这个分支不渲染，DOM 与像素都不变。 -->
      <template #right>
        <span v-if="isDesktop" class="nav-btn" @click="goCreate">新建科目</span>
      </template>
    </van-nav-bar>

    <div class="page-body">
      <!-- 桌面端把汇总条与复习入口并成一行；移动端 .split 没有任何声明，仍是上下两块 -->
      <div class="split is-half">
        <!-- 顶部汇总条（apiStudyOverview 实时数据：到期卡/错题/今日复习） -->
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

        <!-- 今日待复习快捷入口 -->
        <div class="card review-entry" @click="router.push('/study/review')">
          <div class="left">
            <div class="t">🃏 今日待复习</div>
            <div class="d text-light">
              {{
                (overview?.dueCardsTotal ?? 0) > 0
                  ? `${overview?.dueCardsTotal} 张卡到期，复习满 5 张有积分奖励`
                  : '今日无到期卡，去创建新闪卡吧'
              }}
            </div>
          </div>
          <van-badge v-if="(overview?.dueCardsTotal ?? 0) > 0" :content="overview!.dueCardsTotal" />
          <van-icon name="arrow" color="#8a94a6" />
        </div>
      </div>

      <!-- 科目卡片列表。桌面端自动铺成多列；移动端 .split 没有任何声明，竖排不变 -->
      <div class="split is-flow">
        <template v-if="overview && overview.subjects.length">
          <SubjectCard v-for="s in overview.subjects" :key="s.id" :subject="s" />
        </template>
        <div v-else-if="!loading" class="empty-tip span-all">
          <p>先创建第一个科目</p>
          <p class="sub">把自考的科目与章节拆小，进度看得见</p>
          <van-button size="small" type="primary" color="#ff7a00" round class="create-btn" @click="goCreate">
            新建科目
          </van-button>
        </div>
        <div v-else class="empty-tip span-all">加载中…</div>
      </div>
    </div>

    <!-- 浮动新建按钮 -->
    <van-floating-bubble axis="xy" magnetic="x" @click="goCreate">
      <div class="bubble">＋</div>
    </van-floating-bubble>
  </div>
</template>

<style scoped lang="scss">
/* 并排时两块的顶边要对齐：移动端那条 margin-top 是给竖直堆叠留的间距，
   桌面端由 .split 的 column-gap 接管横向、.card 的 margin-bottom 接管纵向。 */
@media (min-width: #{$bp-desktop}) {
  .review-entry {
    margin-top: 0;
  }
}

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

.review-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;

  .left {
    flex: 1;

    .t {
      font-size: 15px;
      font-weight: 700;
    }

    .d {
      font-size: 12px;
      margin-top: 4px;
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
