<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  checked: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'check'): void
}>()

const cls = computed(() => (props.checked ? 'check-btn checked' : 'check-btn'))
</script>

<template>
  <button :class="cls" :disabled="checked || loading" @click.stop="emit('check')">
    <van-loading v-if="loading" size="16" color="#fff" />
    <span v-else-if="checked">✓</span>
    <span v-else>打卡</span>
  </button>
</template>

<style scoped lang="scss">
.check-btn {
  border: none;
  border-radius: 999px;
  padding: 8px 18px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #ff9a3d, #ff7a00);
  box-shadow: 0 3px 8px rgba(255, 122, 0, 0.35);
  transition: transform 0.15s ease, opacity 0.15s ease;
  min-width: 64px;
  height: 34px;

  &:active {
    transform: scale(0.94);
  }

  &.checked {
    background: #27ae60;
    box-shadow: none;
    opacity: 0.92;
  }

  &:disabled {
    cursor: default;
  }
}
</style>
