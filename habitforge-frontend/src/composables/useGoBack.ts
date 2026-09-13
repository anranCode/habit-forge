import { useRoute, useRouter } from 'vue-router'

/**
 * 返回上一页：应用内没有上一页时，退到 `route.meta.backTo`（未声明则退到首页）。
 *
 * 为什么不能直接 router.back()：
 *   移动端总是"从列表点进详情"，history 里一定有上一页，裸调没问题；
 *   桌面端可以直接把链接粘进地址栏、在新标签页打开、或刷新后再点标题栏的返回箭头，
 *   这时应用内的 history 是空的，router.back() 不产生任何跳转 ——
 *   用户被困在当前页，唯一出路是手改地址栏。
 *
 * 判据用 window.history.state.back：vue-router 每次导航都会把上一页的 fullPath 写进去，
 * 为 falsy 就说明这是应用内的第一条记录（深链/新标签页/刷新后抵达）。
 * 不用 router.options.history.state —— 那条路径在 TS 里要绕过类型，取的也是同一个值。
 *
 * 兜底目标写在路由表里（meta.backTo），而不是各页面自己传参：
 * 它属于"这条路由从哪里来"的知识，和 path/title 放在一起才不会各改各的。
 * 例如 /habits/edit/:id 的兜底是 /habits 而不是 /habits/edit（后者根本不是一条路由，
 * 落到 /habits/:id 会被当成 id='edit' 的习惯详情）。
 */
export function useGoBack() {
  const router = useRouter()
  const route = useRoute()

  return function goBack() {
    if (window.history.state?.back) {
      router.back()
      return
    }
    router.replace((route.meta.backTo as string) || '/home')
  }
}
