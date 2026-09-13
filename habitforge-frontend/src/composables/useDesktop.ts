import { computed } from 'vue'
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
