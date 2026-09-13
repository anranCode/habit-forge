/**
 * 构建产物护栏：Vant 的 CSS 在整个 dist 里只允许存在一份。
 *
 * 为什么需要它：src/main.ts 整份引入 'vant/lib/index.css'（进 entry bundle），
 * 而 VantResolver 默认还会按组件注入 vant/<es|lib>/<comp>/style —— 于是同一批 Vant 规则
 * 被打成两份，其中一份被抽进懒加载路由的 chunk。chunk 的 <link> 由 Vite 的 preload helper
 * 在运行时 appendChild 到 <head>，永远排在 index.html 的 entry <link> 之后；.van-popup 与
 * .van-toast 优先级相同（都是单类选择器），后到的 .van-popup{background:var(--van-popup-background)}
 * 就覆盖掉 .van-toast 的深色底，toast 变白底白字（实测 contrast 0）——用户看到的就是
 * "点打卡后弹窗是空白的"。这个缺陷只在构建产物里复现，dev server 的注入顺序不同，
 * 所以必须对 dist 做检查，类型检查与单测都拦不住它。
 *
 * 判据：除 entry 样式表外，dist 里任何 CSS 文件都不得再出现"Vant 组件样式表"。
 * 只按 .van- 选择器判会误伤 —— 项目自己的 scoped 样式本来就会 :deep(.van-cell) 之类地
 * 去覆盖 Vant 外观（那些文件带 [data-v-*] 且不定义变量）。Vant 自己的每份组件样式表都会
 * 定义 --van-* 变量（vant/es/<comp>/index.css 里每个组件都带自己的 :root 段），
 * 因此判据取"同时出现 .van- 选择器 与 --van-* 变量定义"。
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { join, basename } from 'node:path'

const distDir = join(process.cwd(), 'dist')
const assetsDir = join(distDir, 'assets')

if (!existsSync(join(distDir, 'index.html'))) {
  console.error('[check-vant-css] 找不到 dist/index.html —— 请先运行 vite build')
  process.exit(1)
}

const html = readFileSync(join(distDir, 'index.html'), 'utf8')
const entryCss = new Set(
  [...html.matchAll(/<link[^>]+rel="stylesheet"[^>]+href="([^"]+\.css)"/g)].map((m) => basename(m[1]))
)

if (!entryCss.size) {
  console.error('[check-vant-css] index.html 里没有任何 stylesheet link，检查脚本需要更新')
  process.exit(1)
}

// 注意：带 g 的正则用 .test() 是有状态的（lastIndex 会跨调用残留），
// 所以判定用不带 g 的版本，抽取用带 g 的版本，两者分开。
const SELECTOR_ANY = /(^|[},;{>~+\s])\.van-[a-z0-9-]+/
const VAR_DEF_ANY = /--van-[a-z0-9-]+\s*:/
const SELECTOR_ALL = /(^|[},;{>~+\s])\.van-[a-z0-9-]+/g
const isVantStylesheet = (css) => SELECTOR_ANY.test(css) && VAR_DEF_ANY.test(css)
const violations = []

for (const file of readdirSync(assetsDir).filter((f) => f.endsWith('.css'))) {
  if (entryCss.has(file)) continue
  const css = readFileSync(join(assetsDir, file), 'utf8')
  if (!isVantStylesheet(css)) continue
  const hits = [...new Set([...css.matchAll(SELECTOR_ALL)].map((m) => m[0].trim()))]
  violations.push({ file, hits })
}

if (![...entryCss].some((f) => isVantStylesheet(readFileSync(join(assetsDir, f), 'utf8')))) {
  console.error('[check-vant-css] entry 样式表里没有 Vant 规则 —— main.ts 的 vant/lib/index.css 是不是被删了？')
  process.exit(1)
}

if (violations.length) {
  console.error('[check-vant-css] ✗ Vant CSS 被打成了多份，懒加载 chunk 里的那份会覆盖 entry 的层叠：')
  for (const v of violations) {
    console.error(`  - assets/${v.file}: ${v.hits.slice(0, 8).join(' ')}${v.hits.length > 8 ? ` … (${v.hits.length} 处)` : ''}`)
  }
  console.error('\n修复：vite.config.ts 里给 VantResolver 加 { importStyle: false }（main.ts 已整份引入 Vant CSS）。')
  process.exit(1)
}

console.log(`[check-vant-css] ✓ Vant CSS 只有一份（entry: ${[...entryCss].join(', ')}），${readdirSync(assetsDir).filter((f) => f.endsWith('.css')).length} 个 CSS 文件已核对`)
