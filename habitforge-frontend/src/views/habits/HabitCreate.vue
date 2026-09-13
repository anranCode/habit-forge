<script setup lang="ts">
import { ref } from 'vue'
import { useGoBack } from '@/composables/useGoBack'
import { showSuccessToast } from 'vant'
import HabitForm from '@/components/habit/HabitForm.vue'
import { useHabitStore } from '@/stores'
import type { HabitCreatePayload } from '@/types/habit'

/** 无历史可退时按路由的 meta.backTo 兜底（桌面端可直接深链进详情页） */
const goBack = useGoBack()
const habitStore = useHabitStore()
const loading = ref(false)

const emptyForm: HabitCreatePayload = {
  name: '',
  identityTag: '',
  category: 'OTHER',
  frequencyType: 'DAILY',
  twoMinuteVersion: '',
  execTime: '',
  execPlace: '',
  stackAfter: ''
}
const form = ref<HabitCreatePayload>({ ...emptyForm })

async function onSubmit(value: HabitCreatePayload) {
  loading.value = true
  try {
    await habitStore.create(value)
    showSuccessToast('习惯创建成功！')
    goBack()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="创建新习惯" left-arrow @click-left="goBack" />
    <HabitForm v-model="form" submit-text="保存习惯" :loading="loading" @submit="onSubmit" @cancel="goBack" />
  </div>
</template>
