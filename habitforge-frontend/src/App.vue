<script setup lang="ts">
import AppTabbar from '@/components/common/AppTabbar.vue'
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { useEventListener } from '@vueuse/core'
import { useIsDesktop } from '@/composables/useDesktop'

const route = useRoute()
const router = useRouter()
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

/**
 * Esc 关掉最上层的浮层（仅桌面端；移动端没有键盘，不注册也不会有任何行为差异）。
 *
 * 为什么需要：Vant 的 Dialog 自己处理 Esc —— dialog/Dialog.mjs 里 withKeys 绑了 'esc'；
 * 而 Popup 只把事件透传出来（popup/Popup.mjs：`const onKeydown = (event) => emit('keydown', event)`）。
 * 于是抽屉、选择器、下拉菜单这些浮层在桌面端按 Esc 毫无反应，而 Esc 是桌面用户的肌肉记忆。
 *
 * 关法是「点它自己的遮罩」，而不是去改各页面的 v-model：
 *   ① 遮罩点击本来就是用户关浮层的正规路径，走的是 Vant 的公开行为，不碰内部状态；
 *   ② close-on-click-overlay=false 的浮层（必须二选一的）因此 Esc 也关不掉 —— 继承 Vant 的策略，不绕过它。
 *
 * 覆盖不到的：dropdown-menu 的下拉面板没有遮罩（overlay=false），它靠"点别处"收起。
 * 这里不替它模拟那次点击 —— 往 body 上派发 click 会惊动所有监听 document 的代码，
 * 为一个下拉面板不值得。桌面用户点一下旁边就关掉了，成本可接受。
 *
 * 「最上层」按 z-index 判断，而不是 DOM 顺序：Vant 把浮层 teleport 到 body，
 * 节点顺序是挂载顺序、与视觉层级无关，而每次 open 都会取一个递增的全局 z-index（Popup.mjs:55）。
 */
function closeTopPopup() {
  const visible = Array.from(document.querySelectorAll<HTMLElement>('.van-popup')).filter(
    // 对话框留给 Vant 自己处理：抢过来会连它的 Esc 一起触发，等于关两次
    (el) => !el.classList.contains('van-dialog') && getComputedStyle(el).display !== 'none'
  )

  let top: HTMLElement | undefined
  let maxZ = -Infinity
  for (const el of visible) {
    const z = Number(getComputedStyle(el).zIndex)
    if (!Number.isFinite(z) || z < maxZ) continue
    maxZ = z
    top = el
  }

  // 遮罩是同一个 Teleport 里紧挨浮层渲染的兄弟节点（Popup.mjs 的 default 返回 [overlay, transition]）
  const overlay = top?.previousElementSibling
  if (overlay instanceof HTMLElement && overlay.classList.contains('van-overlay')) overlay.click()
}

useEventListener(document, 'keydown', (e: KeyboardEvent) => {
  if (e.key !== 'Escape' || !isDesktop.value) return
  closeTopPopup()
})

/**
 * 面包屑：顺着路由已有的 meta.backTo 往上走，直到某个页面的 backTo 指向不存在的路由。
 *
 * 为什么用 backTo 而不是解析路径分段：每个详情/编辑页都已经声明了"我该退回哪里"
 * （移动端 nav-bar 的返回箭头用的就是它），那正是它在这棵树里的父节点。
 * 拿它当唯一的父子关系来源，面包屑与返回按钮永远不会各说各话；
 * 按 `/study/notes/edit/123` 切分段则要另建一套路径→标题的映射，两处容易走偏。
 *
 * 只在桌面端产出（移动端没有这条栏，返回 [] 会让模板里的 v-if 直接不渲染节点）。
 */
interface Crumb {
  title: string
  path: string
}

const breadcrumbs = computed<Crumb[]>(() => {
  if (!isDesktop.value || !navVisible.value) return []

  // 静态路由（不含 :param）才有确定的路径，也就才可能当父节点被链上去
  const byPath = new Map<string, { title?: string; backTo?: string }>()
  for (const r of router.getRoutes()) {
    if (!r.path.includes(':')) byPath.set(r.path, r.meta as { title?: string; backTo?: string })
  }

  const ancestors: Crumb[] = []
  const seen = new Set<string>()
  let cur = route.meta.backTo as string | undefined
  // seen 兜住环：真出现互相 backTo 的两个路由时，这里不能变成死循环
  while (cur && byPath.has(cur) && !seen.has(cur)) {
    seen.add(cur)
    const meta = byPath.get(cur)!
    ancestors.unshift({ title: meta.title || cur, path: cur })
    cur = meta.backTo
  }

  return [...ancestors, { title: (route.meta.title as string) || '', path: route.path }].filter((c) => c.title)
})
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
      <!--
        全局顶部栏（桌面端）。面包屑为空时（移动端、或登录这类不挂导航的整屏页）
        v-if 直接不渲染节点 —— 移动端的 DOM 与改造前逐字相同。
        样式只在 main.scss 的桌面断点里，所以即便渲染了也不影响移动端。
      -->
      <nav v-if="breadcrumbs.length" class="top-bar" aria-label="面包屑">
        <template v-for="(c, i) in breadcrumbs" :key="c.path">
          <span v-if="i > 0" class="sep" aria-hidden="true">/</span>
          <router-link v-if="i < breadcrumbs.length - 1" :to="c.path" class="crumb">{{ c.title }}</router-link>
          <span v-else class="crumb is-current" aria-current="page">{{ c.title }}</span>
        </template>
      </nav>

      <router-view v-slot="{ Component }">
        <keep-alive :include="['Home', 'HabitList', 'Record', 'Track', 'Profile']">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </div>
  </div>
</template>
