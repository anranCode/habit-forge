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
    background: $success; // 桌面端由 --hf-success 换成降饱和 #5a9e7f，移动端仍是 #27ae60
    box-shadow: none;
    opacity: 0.92;
  }

  &:disabled {
    cursor: default;
  }
}

/* 桌面端：橙色按钮底下那圈橙色辉光（0 3px 8px rgba(255,122,0,.35)）去掉。
   它在移动端是"按得下去"的厚度感来源，铺到宽屏上却只是糊在橙块边缘的一圈暖色脏边，
   而且是全站唯一一处**有色**投影 —— 与桌面端"去阴影"的语言最不搭。
   不给它补 1px 描边：这是个实心药丸按钮，描边会让它看着像未选中的次级按钮。 */
@media (min-width: #{$bp-desktop}) {
  .check-btn {
    box-shadow: none;
  }
}
</style>
