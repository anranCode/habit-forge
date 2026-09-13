import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'
import path from 'path'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      // importStyle: false —— src/main.ts 已经整份引入 'vant/lib/index.css'，
      // 若这里再让解析器按组件注入样式，Vant 的 CSS 会被打成两份：完整那份进 entry，
      // 各组件那份进懒加载路由的 chunk。chunk 的 <link> 是运行时 appendChild 到 head 的，
      // 永远排在 index.html 的 entry <link> 之后；而 .van-popup 与 .van-toast 同为单类选择器
      // （优先级相同），后到的 .van-popup{background:var(--van-popup-background)} 会覆盖
      // .van-toast 的深色底 —— toast 变成白底白字（contrast 0），也就是"打卡后弹窗是空白的"。
      // 一份样式表、顺序确定，才不会再出现这种只在构建产物里复现的白板。
      resolvers: [VantResolver({ importStyle: false })]
    })
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 日记图片:MinIO habitforge-images 桶(dev 代理目标可通过环境变量 VITE_MINIO_PROXY_TARGET 覆盖)
      '/images': {
        target: process.env.VITE_MINIO_PROXY_TARGET || 'http://localhost:9000',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/images/, '/habitforge-images')
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/assets/styles/variables.scss" as *;\n`
      }
    }
  }
})
