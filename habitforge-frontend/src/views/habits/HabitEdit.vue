<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import { showSuccessToast } from 'vant'
import HabitForm from '@/components/habit/HabitForm.vue'
import { apiHabitDetail, apiUpdateHabit } from '@/api'
import type { HabitCreatePayload } from '@/types/habit'

/** 无历史可退时按路由的 meta.backTo 兜底（桌面端可直接深链进详情页） */
const goBack = useGoBack()
const route = useRoute()
const habitId = route.params.id as string

const loading = ref(false)
const ready = ref(false)
const form = ref<HabitCreatePayload>({ name: '' })

onMounted(async () => {
  const h = await apiHabitDetail(habitId)
  form.value = {
    name: h.name,
    identityTag: h.identityTag || '',
    category: h.category,
    frequencyType: h.frequencyType,
    frequencyDays: h.frequencyDays,
    frequencyTarget: h.frequencyTarget,
    twoMinuteVersion: h.twoMinuteVersion || '',
    execTime: h.execTime || '',
    execPlace: h.execPlace || '',
    stackAfter: h.stackAfter || ''
  }
  ready.value = true
})

async function onSubmit(value: HabitCreatePayload) {
  loading.value = true
  try {
    await apiUpdateHabit(habitId, value)
    showSuccessToast('已保存')
    goBack()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="编辑习惯" left-arrow @click-left="goBack" />
    <HabitForm
      v-if="ready"
      v-model="form"
      submit-text="保存修改"
      :loading="loading"
      @submit="onSubmit"
      @cancel="goBack"
    />
  </div>
</template>
