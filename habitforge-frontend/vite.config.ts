import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'
import path from 'path'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [VantResolver()]
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
