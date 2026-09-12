import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 移动端轻量方案：marked + DOMPurify（见计划 P1「Markdown 方案」）
// breaks:true —— 单换行即换行，符合笔记随手写习惯；gfm:true —— 表格/删除线/任务列表
marked.use({ breaks: true, gfm: true })

/**
 * Markdown 原文 -> 已消毒 HTML 字符串。
 * v-html 渲染前必须过 DOMPurify，防笔记正文注入脚本（XSS）。
 */
export function renderMarkdown(md: string): string {
  const html = marked.parse(md ?? '', { async: false }) as string
  return DOMPurify.sanitize(html)
}
