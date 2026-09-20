<script setup lang="ts">
import { useIsDesktop } from '@/composables/useDesktop'

const isDesktop = useIsDesktop()

defineProps<{
  value: string | number
  label: string
  emoji?: string
}>()
</script>

<template>
  <div class="stats-card">
    <div class="value">{{ isDesktop ? value : (emoji || '') + ' ' + value }}</div>
    <div class="label">{{ label }}</div>
  </div>
</template>

<style scoped lang="scss">
.stats-card {
  flex: 1;
  background: $bg-card;
  border-radius: var(--hf-radius-panel, #{$radius-card});
  box-shadow: var(--hf-shadow-card, #{$shadow-card});
  /* 桌面端由 --hf-hairline 换成 1px 描边（宽屏上投影面积太大、发灰）。
     移动端这一行解析成 border: 0 —— 与不写它时的初始态（border-style: none）
     渲染完全一致：既不画线也不占位。 */
  border: var(--hf-hairline, 0);
  padding: 14px 10px;
  text-align: center;

  .value {
    font-size: 18px;
    font-weight: 800;
    color: $text-main;
  }

  .label {
    margin-top: 6px;
    font-size: 12px;
    color: $text-light;
  }
}
</style>
