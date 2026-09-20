<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'

defineProps<{
  /** 是否渲染移动端底部 Tabbar（详情/编辑页为 false，与改造前一致） */
  showBottom?: boolean
}>()

const route = useRoute()
const router = useRouter()

// 唯一的一份导航定义：移动端底部 Tabbar 与桌面端侧边栏都由它渲染，
// 增删入口只改这里。两套外壳靠 CSS 媒体查询切换（不是 UA 嗅探，也不是两个组件）。
const tabs = [
  { path: '/home', label: '今日', icon: 'home-o' },
  { path: '/habits', label: '习惯', icon: 'todo-list-o' },
  { path: '/record', label: '记录', icon: 'description' },
  { path: '/track', label: '追踪', icon: 'chart-trending-o' },
  { path: '/profile', label: '我的', icon: 'contact-o' }
]

const active = computed(() => {
  const i = tabs.findIndex((t) => t.path === route.path || route.path.startsWith(t.path + '/'))
  return i === -1 ? 0 : i
})

function onChange(index: number) {
  router.push(tabs[index].path)
}
</script>

<template>
  <!--
    一个组件、两套样式标准：
      · < 1024px  用 Vant 的底部 Tabbar（样式与 DOM 与改造前完全一致）
      · ≥ 1024px  用左侧导航栏，底部 Tabbar 隐藏
    两者读的是同一份 tabs，靠 main.scss/本节 scoped 里的 @media 切换。
    桌面端侧栏项用真正的 <button>，鼠标可 hover、键盘可 Tab/Enter ——
    底部 Tabbar 在桌面被隐藏，所以不会出现两套导航同时可点的情况。
  -->
  <div class="app-nav">
    <van-tabbar
      v-if="showBottom"
      class="nav-bottom"
      :model-value="active"
      @change="onChange"
      active-color="#ff7a00"
      inactive-color="#8a94a6"
    >
      <van-tabbar-item v-for="t in tabs" :key="t.path" :icon="t.icon">{{ t.label }}</van-tabbar-item>
    </van-tabbar>

    <nav class="nav-side" aria-label="主导航">
      <div class="brand">
        <span class="logo deco" aria-hidden="true">🔥</span>
        <span class="name">HabitForge</span>
      </div>
      <ul class="list">
        <li v-for="(t, i) in tabs" :key="t.path">
          <button
            type="button"
            class="item"
            :class="{ active: i === active }"
            :aria-current="i === active ? 'page' : undefined"
            @click="onChange(i)"
          >
            <van-icon :name="t.icon" />
            <span>{{ t.label }}</span>
          </button>
        </li>
      </ul>
    </nav>
  </div>
</template>

<style scoped lang="scss">
/* 移动端：侧边栏不参与布局（display:none 而非 v-if —— 避免用 JS 断点决定 DOM，
   从而保证移动端渲染结果与改造前逐像素一致、也不引入跨断点的重挂载） */
.nav-side {
  display: none;
}

@media (min-width: #{$bp-desktop}) {
  // 桌面端反过来：隐藏底部 Tabbar，改用侧边栏
  .nav-bottom {
    display: none;
  }

  .nav-side {
    display: flex;
    flex-direction: column;
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    width: $sidenav-width;
    padding: 20px 12px;
    box-sizing: border-box;
    background: #fff;
    border-right: 1px solid #eef1f6;
    overflow-y: auto;
    z-index: 100;

    .brand {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 4px 10px 20px;
      font-weight: 800;
      font-size: 17px;
      color: $text-main;

      .logo {
        font-size: 20px;
      }
    }

    .list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .item {
      display: flex;
      align-items: center;
      gap: 10px;
      width: 100%;
      padding: 11px 12px;
      border: none;
      border-radius: 10px;
      background: transparent;
      font: inherit;
      font-size: 14px;
      color: $text-light;
      text-align: left;
      cursor: pointer;

      .van-icon {
        font-size: 18px;
      }

      &:hover {
        background: #f6f7fb;
        color: $text-main;
      }

      &.active {
        background: rgba(255, 122, 0, 0.1);
        color: $primary;
        font-weight: 700;
      }
    }
  }
}
</style>
