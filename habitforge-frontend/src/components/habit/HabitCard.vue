<script setup lang="ts">
import { computed } from 'vue'
import type { Habit } from '@/types/habit'
import { categoryColor, categoryColorDesktop, categoryEmoji, categoryLabel, frequencyLabel } from '@/utils/format'
import { useIsDesktop } from '@/composables/useDesktop'
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

const isDesktop = useIsDesktop()

/* 标签色走 JS 而不是 CSS（Vant 的 color 是内联 style），所以断点切换只能在这里做。
   移动端取原色，保证与改造前渲染一致；桌面端取降饱和版本，见 format.ts 的说明。 */
const tagColor = computed(() =>
  isDesktop.value ? categoryColorDesktop[props.habit.category] : categoryColor[props.habit.category]
)
</script>

<template>
  <div class="habit-card is-clickable" :class="{ missed: habit.missedYesterday && !habit.checkedToday }" @click="emit('click', habit)">
    <div class="habit-card__left">
      <div class="habit-card__name">
        <span class="emoji deco">{{ categoryEmoji[habit.category] || '✨' }}</span>
        <span class="name">{{ habit.name }}</span>
        <van-tag v-if="habit.identityTag" plain round size="medium" :color="tagColor">
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
/* 悬停抬升要写在这里而不是 main.scss 的 .is-clickable:hover：
   上面 .habit-card 自己带 box-shadow（同一个属性、同一档特异性），
   谁生效取决于打包后两份 CSS 的先后顺序 —— 与其赌它，不如就地加一个类名压过去。 */
@media (hover: hover) and (pointer: fine) {
  .habit-card.is-clickable:hover {
    box-shadow: 0 6px 20px rgba(44, 62, 80, 0.12);
  }
}

.habit-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: $bg-card;
  border-radius: var(--hf-radius-panel, #{$radius-card});
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

/* ==========================================================================
   桌面端（≥ $bp-desktop）
   --------------------------------------------------------------------------
   必须写在本文件最后：下面这条 hover 覆盖与上面 (hover:hover) 块里的
   `.habit-card.is-clickable:hover` 同特异性，谁生效只看文件内先后顺序。

   "昨天漏卡"那条 4px 状态色继续保留（功能性指示，不是装饰），但换一种画法：
   移动端它是 border-left，桌面端四条边统一成 1px 描边之后，4px 的左边框会让
   上边框够不到左端、矩形缺一个角 —— 改用 4px 内向投影，描边闭合、信号不变。
   ========================================================================== */
@media (min-width: #{$bp-desktop}) {
  .habit-card {
    box-shadow: none;
    border-radius: $radius-md;
    border: 1px solid $border-color;
    padding: $space-md;
    margin-bottom: $space-md;

    /* border-left-color 必须显式写回：基线里的 `.habit-card.missed`（特异性同为两个类）
       把左边框设成了 $danger，不压回去就会在白卡片边上漏出一条红边。
       （$danger 在桌面端解析为降饱和的 #c97a6e —— 与 --hf-danger 同一个值。） */
    &.missed {
      border-left-color: $border-color;
      box-shadow: inset 4px 0 0 0 $danger-desktop;
    }

    &__name {
      font-size: $font-body;
    }

    &__alert {
      color: $danger-desktop;
    }

    &__right {
      margin-left: $space-sm;
    }

    &__meta {
      gap: $space-sm;
      margin-top: $space-xs;
    }

    &__streak.hot {
      color: #c98a5e; // 原 #ff5722 的降饱和版
    }
  }

  /* 悬停不再靠阴影抬升 —— 宽屏上阴影投影面积太大、发灰。
     改为极轻的背景变化（用户 brief 里的"轻量化悬停反馈"），鼠标扫过一排卡片时
     不会有一串阴影此起彼伏。 */
  /* :not(.missed) —— 漏卡卡的粉底(#fff8f7)是状态信号，不能被悬停盖掉，
     否则鼠标扫过时"哪张漏了卡"反而看不出来。 */
  .habit-card.is-clickable:not(.missed):hover {
    box-shadow: none;
    background: $border-lightest;
  }

  /* 漏卡卡片的悬停单独补一条：上面 (hover:hover) 块里的 `.habit-card.is-clickable:hover`
     （三个类）特异性高于 `.habit-card.missed`（两个类），而两者写的是同一个 box-shadow ——
     鼠标一放上去，那条 4px 内向信号就被外阴影顶掉了。这里用同样的三个类 +
     :hover 压回去，只保留信号条。 */
  .habit-card.is-clickable.missed:hover {
    box-shadow: inset 4px 0 0 0 $danger-desktop;
  }
}
</style>
