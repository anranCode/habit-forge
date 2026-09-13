<script setup lang="ts">
import AppTabbar from '@/components/common/AppTabbar.vue'
import { useRoute } from 'vue-router'
import { computed } from 'vue'
import { useIsDesktop } from '@/composables/useDesktop'

const route = useRoute()
const isDesktop = useIsDesktop()

// 带 tab 的主页面（今日/习惯/记录/追踪/我的）——移动端只有这些显示底部导航
const isTabPage = computed(() => route.meta.tab !== undefined)

// 登录/注册：整屏铺满、不挂导航（移动端与桌面端都不挂）
const fullBleed = computed(() => route.meta.fullBleed === true)

/**
 * 导航可见性：
 *   · 移动端  —— 与改造前完全一致：只有带 tab 的页面显示底部导航；
 *   · 桌面端  —— 除登录/注册外都显示左侧导航。详情/编辑页在移动端靠 nav-bar 的返回箭头
 *               离开，桌面端有常驻侧栏更自然，也避免"整页没有任何导航出口"。
 * 只有桌面端分支会因宽度变化而变 —— 移动端 v-if 的结果与改造前逐字相同。
 */
const navVisible = computed(() => (fullBleed.value ? false : isDesktop.value ? true : isTabPage.value))

// 底部 Tabbar 仍只由 meta.tab 决定（桌面端由 CSS 隐藏，不靠这里切换）
const showBottomNav = computed(() => isTabPage.value)
</script>

<template>
  <div class="page" :class="{ 'has-nav': navVisible, 'is-fullbleed': fullBleed }">
    <AppTabbar v-if="navVisible" :show-bottom="showBottomNav" />
    <!--
      .page-main 是内容区的定位锚点：
      移动端它是个无样式的直通 div（不改变任何盒模型，保证移动端逐像素不变），
      桌面端 main.scss 的 @media (min-width: 1024px) 才给它限宽+居中。

      注意：keep-alive 的 include 按**组件名**匹配（Home/HabitList/Record/Track/Profile），
      所以 router-view 的直系子节点必须仍是页面组件本身。不要把 <component :is> 换成一个
      自建的断点外壳组件，否则名字匹配不上、缓存会静默失效（切页丢滚动位置与表单状态）。
      两套外壳靠 CSS 媒体查询切换，不是靠两个组件。
    -->
    <div class="page-main">
      <router-view v-slot="{ Component }">
        <keep-alive :include="['Home', 'HabitList', 'Record', 'Track', 'Profile']">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </div>
  </div>
</template>
