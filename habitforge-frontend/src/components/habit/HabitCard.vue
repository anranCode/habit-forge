<script setup lang="ts">
import type { Habit } from '@/types/habit'
import { categoryColor, categoryEmoji, categoryLabel, frequencyLabel } from '@/utils/format'
import HabitCheckButton from './HabitCheckButton.vue'

const props = defineProps<{
  habit: Habit
  loading?: boolean
  /** 是否显示打卡按钮（今日看板用） */
  checkable?: boolean
}>()

const emit = defineEmits<{
  (e: 'check', habit: Habit): void
  (e: 'click', habit: Habit): void
}>()
</script>

<template>
  <div class="habit-card" :class="{ missed: habit.missedYesterday && !habit.checkedToday }" @click="emit('click', habit)">
    <div class="habit-card__left">
      <div class="habit-card__name">
        <span class="emoji">{{ categoryEmoji[habit.category] || '✨' }}</span>
        <span class="name">{{ habit.name }}</span>
        <van-tag v-if="habit.identityTag" plain round size="medium" :color="categoryColor[habit.category]">
          {{ habit.identityTag }}
        </van-tag>
      </div>
      <div class="habit-card__meta">
        <span v-if="habit.execTime">⏰ {{ habit.execTime }}</span>
        <span v-if="habit.execPlace">📍 {{ habit.execPlace }}</span>
        <span>{{ frequencyLabel(habit.frequencyType, habit.frequencyDays, habit.frequencyTarget) }}</span>
      </div>
      <div v-if="habit.missedYesterday && !habit.checkedToday" class="habit-card__alert">
        🔔 昨天漏卡了，今天别再错过 —— 绝不错过两次！
      </div>
      <div v-else-if="habit.twoMinuteVersion" class="habit-card__tip text-light">
        🌱 微习惯：{{ habit.twoMinuteVersion }}
      </div>
    </div>

    <div class="habit-card__right">
      <div class="habit-card__streak" :class="{ hot: (habit.currentStreak || 0) > 0 }">
        🔥{{ habit.currentStreak || 0 }}
      </div>
      <HabitCheckButton
        v-if="checkable"
        :checked="!!habit.checkedToday"
        :loading="loading"
        @check="emit('check', habit)"
      />
      <div v-else-if="habit.frequencyType === 'WEEKLY_COUNT'" class="habit-card__week text-light">
        本周 {{ habit.weekCheckedCount || 0 }}/{{ habit.frequencyTarget }}
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.habit-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: $bg-card;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  padding: 14px 16px;
  margin-bottom: 12px;
  transition: transform 0.12s ease;
  border-left: 4px solid transparent;

  &:active {
    transform: scale(0.99);
  }

  &.missed {
    border-left-color: $danger;
    background: #fff8f7;
  }

  &__left {
    flex: 1;
    min-width: 0;
  }

  &__name {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 16px;
    font-weight: 600;

    .emoji {
      font-size: 18px;
    }

    .name {
      max-width: 140px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  &__meta {
    display: flex;
    gap: 10px;
    font-size: 12px;
    color: $text-light;
    margin-top: 6px;
    flex-wrap: wrap;
  }

  &__alert {
    margin-top: 6px;
    font-size: 12px;
    color: $danger;
    font-weight: 500;
  }

  &__tip {
    margin-top: 6px;
    font-size: 12px;
  }

  &__right {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    margin-left: 10px;
  }

  &__streak {
    font-size: 14px;
    color: $text-light;
    font-weight: 600;

    &.hot {
      color: #ff5722;
    }
  }

  &__week {
    font-size: 12px;
  }
}
</style>
