/**
 * 构建护栏：桌面端两套标准的约定不许被写坏。
 *
 * 背景：桌面适配承诺"移动端（<1024px）逐像素不变"，靠的不是自觉，而是结构 ——
 * 所有桌面规则都待在 @media (min-width: $bp-desktop) 里，断点外一条声明都没有。
 * 这条约定很容易在后续开发里被无意识地破坏（顺手加一句 .card{...} 就回到移动端了），
 * 而移动端的回归只有跑 repro/mobile_invariance.sh 才看得出来 —— 那一步不该是唯一防线。
 * 这里把约定变成构建期能拦下来的事实。
 *
 * 五项检查：
 *   A 断点常量一致 —— useDesktop.ts 的 DESKTOP_MIN_WIDTH ↔ variables.scss 的 $bp-desktop
 *   B 断点只有两个 —— src 里不允许出现第三种 @media 写法（写错断点＝悄悄多出一套标准）
 *   C viewport 未动 —— index.html 的 meta 逐字比对（移动端像素的地基）
 *   D 移动端基线未动 —— .page 的 540px 限宽与 Tabbar 留白仍在顶层规则里
 *   E 桌面词汇不出界 —— 构建产物里，桌面专属类名的每条声明都落在许可的媒体查询内
 *
 * E 是唯一需要 dist 的一项（跑在 vite build 之后，与 check-vant-css 同一条链）。
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const root = process.cwd()
const failures = []
const fail = (msg) => failures.push(msg)

// ---------------------------------------------------------------- A 断点常量
const useDesktop = readFileSync(join(root, 'src/composables/useDesktop.ts'), 'utf8')
const variables = readFileSync(join(root, 'src/assets/styles/variables.scss'), 'utf8')

const tsMatch = useDesktop.match(/export\s+const\s+DESKTOP_MIN_WIDTH\s*=\s*(\d+)/)
const scssMatch = variables.match(/^\$bp-desktop:\s*(\d+)px\s*;/m)
if (!tsMatch) fail('useDesktop.ts 里找不到 `export const DESKTOP_MIN_WIDTH = <数字>`')
if (!scssMatch) fail('variables.scss 里找不到 `$bp-desktop: <数字>px;`')
if (tsMatch && scssMatch && tsMatch[1] !== scssMatch[1]) {
  fail(
    `断点不一致：useDesktop.ts 是 ${tsMatch[1]}px，variables.scss 是 ${scssMatch[1]}px。\n` +
      '    JS 的 matchMedia 与 CSS 的 @media 必须卡在同一个宽度，否则会出现"JS 认为进了桌面端、CSS 还没进"的错位。'
  )
}

// ---------------------------------------------------------------- B 只有两个断点
// 只认真正的 at-rule（行首 @media），注释里提到的断点不算。
const MEDIA_OK_SRC = [
  '@media (min-width: #{$bp-desktop})',
  '@media (hover: hover) and (pointer: fine)'
]
// 构建产物里 scss 插值已经展开成字面量（1024px），所以 E 段要按展开后的形式匹配
const MEDIA_OK_DIST = [
  `@media (min-width: ${scssMatch ? scssMatch[1] : '?'}px)`,
  '@media (hover: hover) and (pointer: fine)'
]
const srcFiles = (function walk(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = join(dir, e.name)
    if (e.isDirectory()) return walk(p)
    return /\.(vue|scss|css|ts)$/.test(e.name) ? [p] : []
  })
})(join(root, 'src'))

for (const file of srcFiles) {
  const lines = readFileSync(file, 'utf8').split('\n')
  lines.forEach((line, i) => {
    // 开括号锚在行尾：`#{$bp-desktop}` 自己带一对花括号，用 [^{]* 会在插值中途截断
    const m = line.match(/^\s*(@media.*?)\s*\{\s*$/)
    if (!m) return
    const prelude = m[1].trim()
    if (!MEDIA_OK_SRC.includes(prelude)) {
      fail(
        `${file.replace(root, '')}:${i + 1} 出现了第三个断点：\`${prelude}\`\n` +
          `    只允许这两个：\n      ${MEDIA_OK_SRC.join('\n      ')}\n` +
          '    （写死宽度或用别的查询，都会多出第三套标准。）'
      )
    }
  })
}

// ---------------------------------------------------------------- C viewport 未动
const INDEX_HTML_VIEWPORT =
  '<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />'
const indexHtml = readFileSync(join(root, 'index.html'), 'utf8')
if (!indexHtml.includes(INDEX_HTML_VIEWPORT)) {
  fail(
    'index.html 的 viewport meta 变了。\n' +
      '    它是移动端像素的地基：改宽度会连带改掉每个页面的排版，改 maximum-scale/user-scalable\n' +
      '    会让移动端能双指缩放（桌面适配不需要动它）。真要改，请连同 repro/mobile_invariance.sh\n' +
      '    的全量比对一起改，并同步更新本脚本里的基准串。'
  )
}

// ---------------------------------------------------------------- D 移动端基线未动
const mainScss = readFileSync(join(root, 'src/assets/styles/main.scss'), 'utf8')
const topLevel = mainScss.split(/^@media/m)[0] // 第一个 @media 之前＝移动端基线区
const baselineChecks = [
  [/\$content-max-width:\s*540px\s*;/, 'variables.scss 的 $content-max-width 不再是 540px'],
  [/\.page\s*\{[^}]*max-width:\s*\$content-max-width/, 'main.scss 的 .page 不再用 $content-max-width 限宽'],
  [/\.page\s*\{[^}]*padding-bottom:\s*68px/, 'main.scss 的 .page 不再给底部 Tabbar 留 68px']
]
for (const [re, msg] of baselineChecks) {
  const hay = msg.includes('variables.scss') ? variables : topLevel
  if (!re.test(hay)) fail(`${msg} —— 这是移动端的基线，桌面端不应该去动它`)
}

// ---------------------------------------------------------------- E 桌面词汇不出界
const distIndex = join(root, 'dist/index.html')
if (!existsSync(distIndex)) {
  console.error('[check-desktop-css] 找不到 dist/index.html —— 请先运行 vite build')
  process.exit(1)
}

/**
 * 桌面专属的类名（"桌面词汇"）—— 断点外出现即违规。
 * 新增一个只在桌面端存在的类名时，把它加到这里，否则它就可以被写到断点外而无人拦。
 *
 * 有意不受本表约束的，两种都不是偷懒：
 *   .van-* —— Vant 自己的命名空间，它的顶层规则不该被我们判违规（isVantOnly 已单独跳过）；
 *   .nav-side —— 唯一一个"桌面专属、但故意在断点外留一条声明"的例子：
 *     AppTabbar.vue 给它写了顶层 display:none，是为了让侧边栏常驻 DOM、只靠 CSS 显隐，
 *     从而不由 JS 断点决定 DOM 结构（否则跨断点会重挂载）。这条顶层声明的存在正是设计意图，
 *     收进本表只会逼着加例外。它的桌面规则（display:flex + 定位）照样在断点内。
 */
const DESKTOP_VOCAB = [
  'split',
  'col-main',
  'col-side',
  'span-all',
  'is-rail',
  'is-half',
  'is-thirds',
  'is-flow',
  'is-reading',
  'is-form',
  'is-clickable',
  'inline-actions',
  'inline-action',
  'card-actions',
  'page-main',
  // 装饰性 emoji 的容器：模板里始终渲染（不靠 v-if 增删 DOM），只在断点内 display:none。
  // 断点外一旦给它写声明，移动端的 emoji 就会跟着消失 —— 正是本表要拦的事。
  'deco'
]

const assetsDir = join(root, 'dist/assets')
const cssFiles = readdirSync(assetsDir).filter((f) => f.endsWith('.css'))

/** 去掉注释与字符串，免得里面的花括号把块结构数错 */
const strip = (css) => css.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(['"])(?:\\.|(?!\1)[^\\])*\1/g, '""')

/** 逐块遍历：prelude（选择器或 at-rule）+ body + 该块所处的媒体查询栈 */
function* blocks(css) {
  const stack = []
  let start = 0
  for (let i = 0; i < css.length; i++) {
    if (css[i] === '{') {
      stack.push(css.slice(start, i).trim())
      start = i + 1
    } else if (css[i] === '}') {
      const body = css.slice(start, i)
      const prelude = stack.pop() ?? ''
      yield { prelude, body, media: stack.filter((s) => s.startsWith('@media')) }
      start = i + 1
    }
  }
}

// 纯 Vant 命名空间的规则（每个复合选择器的类名都是 .van-*）不参与判定
const isVantOnly = (selector) => {
  const classes = selector.match(/\.[A-Za-z0-9_-]+/g)
  return !!classes && classes.every((c) => c.startsWith('.van-'))
}

const seenInsideBreakpoint = new Set()
const violations = []

for (const file of cssFiles) {
  for (const { prelude, media } of blocks(strip(readFileSync(join(assetsDir, file), 'utf8')))) {
    if (!prelude || prelude.startsWith('@')) continue
    const sanctioned = media.some((m) => MEDIA_OK_DIST.some((ok) => m.startsWith(ok)))
    for (const name of DESKTOP_VOCAB) {
      // 词边界要卡住：.is-form 不该被 .is-form-x 命中
      if (!new RegExp(`\\.${name}(?![\\w-])`).test(prelude)) continue
      if (sanctioned) {
        seenInsideBreakpoint.add(name)
        continue
      }
      if (isVantOnly(prelude)) continue
      violations.push({ file, selector: prelude.slice(0, 100), name })
    }
  }
}

if (violations.length) {
  fail(
    '桌面词汇漏到了断点外 —— 这些规则在移动端也会生效，会破坏"移动端逐像素不变"：\n' +
      violations
        .map((v) => `    assets/${v.file}  [.${v.name}]  ${v.selector}`)
        .slice(0, 12)
        .join('\n') +
      (violations.length > 12 ? `\n    …（共 ${violations.length} 处）` : '') +
      '\n    修复：把它们移进 @media (min-width: #{$bp-desktop})。'
  )
}

// 护栏自检：词汇表里每个名字都得真的在断点内出现过，否则改了名就会静默失效
const deadVocab = DESKTOP_VOCAB.filter((n) => !seenInsideBreakpoint.has(n))
if (deadVocab.length) {
  fail(
    `桌面词汇表里有 ${deadVocab.length} 个名字在断点内一次都没出现：${deadVocab.join(', ')}\n` +
      '    要么是把类名改名/删掉了（那护栏就白设了，请同步更新 DESKTOP_VOCAB），\n' +
      '    要么是它被写到了断点外 —— 看上面的违规列表。'
  )
}

// ---------------------------------------------------------------- 汇总
if (failures.length) {
  console.error('[check-desktop-css] ✗ 桌面/移动两套标准的约定被破坏了：\n')
  for (const f of failures) console.error('  · ' + f + '\n')
  process.exit(1)
}

console.log(
  `[check-desktop-css] ✓ 断点 ${scssMatch[1]}px 两端一致；` +
    `${srcFiles.length} 个源文件只有两个 @media；` +
    `viewport 与移动端基线未动；` +
    `${cssFiles.length} 个 CSS 文件里 ${DESKTOP_VOCAB.length} 个桌面类名全部待在断点内`
)
