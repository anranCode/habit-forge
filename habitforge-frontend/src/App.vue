<script setup lang="ts">
import AppTabbar from '@/components/common/AppTabbar.vue'
import { useRoute } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()

// 登录/注册/详情等页面不显示底部导航
const showTabbar = computed(() => route.meta.tab !== undefined)
</script>

<template>
  <div class="page">
    <router-view v-slot="{ Component }">
      <keep-alive :include="['Home', 'HabitList', 'Record', 'Track', 'Profile']">
        <component :is="Component" />
      </keep-alive>
    </router-view>
    <AppTabbar v-if="showTabbar" />
  </div>
</template>
