import { computed, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'

/**
 * 桌面端（PC 网页窗口）断点的唯一 JS 来源。
 *
 * 两套标准的分工（不许越界）：
 *   · **视口宽度**决定布局 —— 断点常量在这里 + main.scss 的 @media；
 *   · **输入能力**决定交互 —— CSS 侧用 (hover: hover) and (pointer: fine)。
 * 一律不做 UA 嗅探：触屏笔记本、iPad 接鼠标、桌面浏览器窗口缩窄，都会落到正确的一套。
 *
 * 注意 DESKTOP_MIN_WIDTH 与 variables.scss 的 $bp-desktop 必须一致，
 * scripts/check-desktop-css.mjs 会核对这一对常量，改一处忘另一处会被构建拦下。
 */
export const DESKTOP_MIN_WIDTH = 1024

const DESKTOP_QUERY = `(min-width: ${DESKTOP_MIN_WIDTH}px)`

// 模块级单例：所有组件共用同一个 matchMedia 监听，避免每个调用点各建一个
const isDesktop = useMediaQuery(DESKTOP_QUERY)

/** 当前是否按桌面标准渲染（响应式，窗口缩放会实时更新） */
export function useIsDesktop() {
  return isDesktop
}

/**
 * 一次性读取（不订阅）。给路由守卫、scrollBehavior 这类非组件上下文用 ——
 * 它们只需要"这一刻"的答案，不需要跟着窗口缩放重跑。
 */
export function isDesktopNow() {
  return typeof window !== 'undefined' && window.matchMedia(DESKTOP_QUERY).matches
}

/**
 * 弹出层的位置标准：底部抽屉（移动）↔ 居中对话框（桌面）。
 *
 * 必须由 JS 给 `position` 传值，不能靠 CSS 覆盖 `.van-popup--bottom`：
 * Vant 的进出场动画是写在 `van-popup-slide-bottom-*` 里的 transform，
 * 用 CSS 强改 transform 做居中会和动画打架（开合瞬间飞到别处）。
 */
export function usePopupPosition() {
  return computed(() => (isDesktop.value ? ('center' as const) : ('bottom' as const)))
}

/**
 * 侧边栏折叠状态（仅桌面端有意义；移动端侧栏本来就不参与布局）。
 *
 * 模块级单例而不是各自 useStorage：App.vue 要拿它给 .page 加类名（好把内容区
 * 左边距从 220px 收到 56px），AppTabbar.vue 要拿它切自己的宽度与文案 ——
 * 两处必须是同一个 ref，否则会出现"侧栏收窄了、内容区还在让 220px"的错位。
 * 自己去写 localStorage 而不是引 useStorage，是为了不依赖库内部的同 key 共享实现。
 */
const SIDEBAR_COLLAPSED_KEY = 'hf-sidebar-collapsed'

const sidebarCollapsed = ref(
  typeof localStorage !== 'undefined' && localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1'
)

/** 侧栏是否折叠（响应式，读写同一个单例） */
export function useSidebarCollapsed() {
  return sidebarCollapsed
}

export function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  try {
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, sidebarCollapsed.value ? '1' : '0')
  } catch {
    // 无痕模式 / 存储被禁用：折叠态退化成"仅本次会话有效"，不该因此报错
  }
}
