<script setup lang="ts">
import { ref, watch } from 'vue'
import MarkdownPreview from '@/components/study/MarkdownPreview.vue'
import type { Flashcard, ReviewRating } from '@/types/flashcard'

const props = defineProps<{
  card: Flashcard
  /** 卡片所属科目名（可选，显示在正面角标） */
  subjectName?: string
}>()

const emit = defineEmits<{
  (e: 'rate', rating: ReviewRating): void
}>()

const flipped = ref(false)

// 换卡时回到正面
watch(
  () => props.card.id,
  () => {
    flipped.value = false
  }
)

const ratings: { v: ReviewRating; label: string; cls: string }[] = [
  { v: 1, label: '忘记', cls: 'r-forget' },
  { v: 2, label: '模糊', cls: 'r-vague' },
  { v: 3, label: '记得', cls: 'r-good' },
  { v: 4, label: '轻松', cls: 'r-easy' }
]
</script>

<template>
  <div class="flip-wrap">
    <div class="flip" :class="{ flipped }" @click="flipped = !flipped">
      <div class="face front">
        <div v-if="subjectName" class="tag">{{ subjectName }}</div>
        <div class="face-inner">
          <MarkdownPreview class="q" :content="card.front" />
        </div>
        <div class="hint text-light">点击卡片查看答案</div>
      </div>
      <div class="face back">
        <div v-if="subjectName" class="tag">{{ subjectName }}</div>
        <div class="face-inner">
          <MarkdownPreview class="q a" :content="card.back" />
        </div>
        <div class="hint text-light">回忆准确吗？点击翻转</div>
      </div>
    </div>

    <transition name="van-fade">
      <div v-if="flipped" class="rating-row">
        <div
          v-for="r in ratings"
          :key="r.v"
          class="rating-btn"
          :class="r.cls"
          @click.stop="emit('rate', r.v)"
        >
          {{ r.label }}
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
.flip-wrap {
  perspective: 1200px;
}

.flip {
  position: relative;
  width: 100%;
  min-height: 320px;
  transform-style: preserve-3d;
  transition: transform 0.5s cubic-bezier(0.4, 0.2, 0.2, 1);
  cursor: pointer;

  &.flipped {
    transform: rotateY(180deg);
  }
}

.face {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  display: flex;
  flex-direction: column;
  background: $bg-card;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  padding: 20px 18px;
  overflow: hidden;

  &.back {
    transform: rotateY(180deg);
    background: linear-gradient(160deg, #fff 0%, #fff8f0 100%);
  }
}

.face-inner {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;

  // 卡面为 Markdown 渲染（MarkdownPreview），块级元素间距由 md-body 样式控制，
  // 不再用 white-space: pre-wrap（会把渲染 HTML 里的源码换行显示成空行）
  .q {
    width: 100%;
    font-size: 18px;
    font-weight: 600;
    line-height: 1.7;
    text-align: center;
    word-break: break-word;
    max-height: 240px;
    overflow-y: auto;
  }

  .q.a {
    color: $primary;
  }
}

.tag {
  align-self: flex-start;
  font-size: 11px;
  color: $primary;
  background: rgba(255, 122, 0, 0.1);
  border-radius: 999px;
  padding: 3px 10px;
  margin-bottom: 8px;
}

.hint {
  text-align: center;
  font-size: 12px;
  margin-top: 12px;
}

.rating-row {
  display: flex;
  gap: 8px;
  margin-top: 16px;

  .rating-btn {
    flex: 1;
    text-align: center;
    padding: 12px 0;
    border-radius: 12px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    background: $bg-card;
    box-shadow: $shadow-card;
    transition: transform 0.12s ease;

    &:active {
      transform: scale(0.95);
    }

    &.r-forget {
      color: $danger;
      background: rgba(231, 76, 60, 0.08);
    }
    &.r-vague {
      color: $warning;
      background: rgba(243, 156, 18, 0.1);
    }
    &.r-good {
      color: $success;
      background: rgba(39, 174, 96, 0.1);
    }
    &.r-easy {
      color: #fff;
      background: $primary;
    }
  }
}
</style>
