<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div>
    <van-nav-bar title="新建闪卡" left-arrow @click-left="router.back()" />
    <div class="page-body">
      <div class="empty-tip">建设中…</div>
    </div>
  </div>
</template>
