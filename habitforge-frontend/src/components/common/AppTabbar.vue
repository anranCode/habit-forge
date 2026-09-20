<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { toggleSidebar, useSidebarCollapsed } from '@/composables/useDesktop'

defineProps<{
  /** 是否渲染移动端底部 Tabbar（详情/编辑页为 false，与改造前一致） */
  showBottom?: boolean
}>()

const route = useRoute()
const router = useRouter()

// 与 App.vue 共享同一个单例（见 useDesktop.ts）—— 它给 .page 让位、这里切自己的宽度
const collapsed = useSidebarCollapsed()

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

    <nav class="nav-side" :class="{ 'is-collapsed': collapsed }" aria-label="主导航">
      <div class="brand">
        <span class="logo deco" aria-hidden="true">🔥</span>
        <span class="name">HabitForge</span>
        <span class="mark" aria-hidden="true">H</span>
      </div>
      <ul class="list">
        <li v-for="(t, i) in tabs" :key="t.path">
          <button
            type="button"
            class="item"
            :class="{ active: i === active }"
            :aria-current="i === active ? 'page' : undefined"
            :title="collapsed ? t.label : undefined"
            @click="onChange(i)"
          >
            <van-icon :name="t.icon" />
            <span>{{ t.label }}</span>
          </button>
        </li>
      </ul>
      <button
        type="button"
        class="collapse-toggle"
        :aria-label="collapsed ? '展开侧栏' : '折叠侧栏'"
        :aria-expanded="!collapsed"
        @click="toggleSidebar"
      >
        <van-icon :name="collapsed ? 'arrow' : 'arrow-left'" />
      </button>
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
    border-right: 1px solid $border-color;
    overflow-y: auto;
    overflow-x: hidden; // 折叠动画中途不让文字溢出到内容区
    z-index: 100;
    // 只有桌面端会改宽度，所以这条过场也只在这里有意义（移动端整块 display:none）
    transition: width 200ms ease;

    .brand {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 4px 10px 20px;
      font-weight: 800;
      font-size: 17px;
      color: #1a1a1a;

      .logo {
        font-size: 20px;
      }

      // 折叠态的品牌缩写。只在断点内定义，而父级 .nav-side 在移动端是 display:none，
      // 所以移动端即便渲染了这个 span 也不会显示（与 .deco 同一套"断点外零声明"的思路）。
      .mark {
        display: none;
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
      border-radius: $radius-md;
      background: transparent;
      font: inherit;
      font-size: $font-body;
      color: #999;
      text-align: left;
      cursor: pointer;

      .van-icon {
        font-size: 18px;
        flex-shrink: 0;
      }

      &:hover {
        background: $border-lightest;
        color: #333;
      }

      &.active {
        background: rgba(255, 122, 0, 0.08);
        color: $primary;
        font-weight: 700;
      }
    }

    /* 折叠成图标轨道：只留图标，文字让位给内容区。
       品牌处补一个 "H" 缩写 —— 桌面端 .deco 已经把 🔥 隐掉了，不留点东西
       侧栏顶部会是一块空白。 */
    &.is-collapsed {
      width: $sidenav-collapsed-width;
      padding: 20px 6px;

      .brand {
        justify-content: center;
        padding: 4px 0 20px;

        .name {
          display: none;
        }

        .mark {
          display: inline;
          font-size: $font-heading;
        }
      }

      .item {
        justify-content: center;
        gap: 0;
        padding: 11px 0;

        span {
          display: none;
        }
      }
    }
  }

  .collapse-toggle {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    margin-top: auto; // 侧栏是 flex 列 + 固定上下沿，auto 把它推到最底
    padding: $space-sm 0;
    border: none;
    border-radius: $radius-md;
    background: transparent;
    color: #999;
    font-size: 16px;
    cursor: pointer;

    &:hover {
      background: $border-lightest;
      color: #333;
    }
  }
}
</style>
