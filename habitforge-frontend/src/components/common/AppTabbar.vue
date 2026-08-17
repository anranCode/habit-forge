<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()
const router = useRouter()

const tabs = [
  { path: '/home', label: '今日', icon: 'home-o' },
  { path: '/habits', label: '习惯', icon: 'todo-list-o' },
  { path: '/record', label: '记录', icon: 'description' },
  { path: '/track', label: '追踪', icon: 'chart-trending-o' },
  { path: '/profile', label: '我的', icon: 'contact-o' }
]

const active = computed(() => {
  const i = tabs.findIndex((t) => t.path === route.path || route.path.startsWith(t.path + '/'))
  return i === -1 ? 0 : i
})

function onChange(index: number) {
  router.push(tabs[index].path)
}
</script>

<template>
  <van-tabbar :model-value="active" @change="onChange" active-color="#ff7a00" inactive-color="#8a94a6">
    <van-tabbar-item v-for="t in tabs" :key="t.path" :icon="t.icon">{{ t.label }}</van-tabbar-item>
  </van-tabbar>
</template>
