<script setup lang="ts">
defineProps<{
  value: string | number
  label: string
  emoji?: string
}>()
</script>

<template>
  <div class="stats-card">
    <div class="value">{{ emoji }} {{ value }}</div>
    <div class="label">{{ label }}</div>
  </div>
</template>

<style scoped lang="scss">
.stats-card {
  flex: 1;
  background: $bg-card;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
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
