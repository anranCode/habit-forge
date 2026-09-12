<script setup lang="ts">
import { useRouter } from 'vue-router'

defineOptions({ name: 'PlanToday' })

const router = useRouter()
</script>

<template>
  <div>
    <van-nav-bar title="今日安排" left-arrow @click-left="router.back()" />

    <div class="page-body">
      <div class="card placeholder text-light">建设中…</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.placeholder {
  padding: 48px 16px;
  text-align: center;
}
</style>
