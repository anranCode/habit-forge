<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import HabitForm from '@/components/habit/HabitForm.vue'
import { useHabitStore } from '@/stores'
import type { HabitCreatePayload } from '@/types/habit'

const router = useRouter()
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
    router.back()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="创建新习惯" left-arrow @click-left="router.back()" />
    <HabitForm v-model="form" submit-text="保存习惯" :loading="loading" @submit="onSubmit" @cancel="router.back()" />
  </div>
</template>
