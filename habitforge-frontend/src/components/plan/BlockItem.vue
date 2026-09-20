<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { PlanBlock, BlockType } from '@/types/plan'

const props = defineProps<{
  block: PlanBlock
  /** 历史只读：隐藏所有操作按钮 */
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'complete', id: string, checkinHabit: boolean): void
  (e: 'skip', id: string): void
  (e: 'reopen', id: string): void
}>()

const router = useRouter()

const META: Record<BlockType, { icon: string; name: string }> = {
  HABIT: { icon: '⏰', name: '习惯' },
  STUDY: { icon: '📖', name: '学习' },
  REST: { icon: '☕', name: '休息' },
  OTHER: { icon: '📌', name: '其他' }
}

const meta = computed(() => META[props.block.blockType] || META.OTHER)

// 「完成并打卡」勾选（文案见模板）：有 habitId 且今日未打卡时展示，默认勾；可取消
const checkin = ref(true)
watch(
  () => props.block.id,
  () => (checkin.value = true)
)

const canComplete = computed(() => props.block.status === 'ADOPTED' || props.block.status === 'PROPOSED')
const showCheckinBox = computed(
  () => !!props.block.habitId && !props.block.habitCheckedToday && !props.readonly
)

/** STUDY 块可深链：chapterId → 科目详情（章节树）；仅 subjectId → 该科到期卡复习 */
const linkable = computed(() => props.block.blockType === 'STUDY' && !!props.block.subjectId)

function onRowClick() {
  if (!linkable.value) return
  const b = props.block
  if (b.chapterId) {
    router.push(`/study/subjects/${b.subjectId}`)
  } else {
    router.push({ path: '/study/review', query: { subjectId: b.subjectId! } })
  }
}
</script>

<template>
  <div class="block-item" :class="{ skipped: block.status === 'SKIPPED', done: block.status === 'DONE' }">
    <!-- 左侧主体（STUDY 块可点击深链） -->
    <div class="block-main" :class="{ linkable }" @click="onRowClick">
      <div class="block-time">{{ block.startTime }}<br />–{{ block.endTime }}</div>
      <div class="block-body">
        <div class="block-title">
          <span class="icon">{{ meta.icon }}</span>
          <span class="t">{{ block.title }}</span>
          <span v-if="linkable" class="go">›</span>
        </div>
        <div class="block-tags">
          <span v-if="block.habitName" class="tag habit">{{ block.habitName }}</span>
          <span v-if="block.subjectName" class="tag study">{{ block.subjectName }}</span>
          <span v-if="block.habitCheckedToday" class="tag checked">已打卡</span>
          <span v-if="block.status === 'PROPOSED'" class="tag proposed">待采纳</span>
          <span v-if="block.status === 'DONE'" class="tag done">已完成</span>
        </div>
      </div>
    </div>

    <!-- 右侧操作区（按 status；历史只读隐藏） -->
    <div v-if="!readonly" class="block-actions">
      <template v-if="canComplete">
        <van-checkbox
          v-if="showCheckinBox"
          v-model="checkin"
          shape="square"
          icon-size="16"
          checked-color="#ff7a00"
          class="checkin-box"
        >完成并打卡</van-checkbox>
        <div class="btn-row">
          <!-- 习惯今日已打卡：块状态不自动联动，主按钮降级为「同步为完成」（不重复打卡） -->
          <van-button
            size="mini"
            type="primary"
            color="#ff7a00"
            round
            @click="emit('complete', block.id, showCheckinBox ? checkin : false)"
          >
            {{ block.habitCheckedToday ? '同步为完成' : '完成' }}
          </van-button>
          <van-button size="mini" plain round class="skip-btn" @click="emit('skip', block.id)">跳过</van-button>
        </div>
      </template>
      <template v-else-if="block.status === 'DONE'">
        <div class="btn-row">
          <van-button size="mini" plain round class="skip-btn" @click="emit('reopen', block.id)">撤销</van-button>
        </div>
      </template>
      <template v-else-if="block.status === 'SKIPPED'">
        <div class="btn-row">
          <van-button size="mini" plain round class="skip-btn" @click="emit('reopen', block.id)">恢复</van-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.block-item {
  display: flex;
  align-items: center;
  gap: 10px;
  background: $bg-card;
  border-radius: var(--hf-radius-panel, 12px);
  box-shadow: var(--hf-shadow-card, #{$shadow-card});
  // 桌面端由 --hf-hairline 换成 1px 描边；移动端解析成 border: 0，渲染与原来一致
  border: var(--hf-hairline, 0);
  padding: 12px;
  margin-bottom: 10px;

  &.skipped {
    opacity: 0.55;
  }
}

.block-main {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;

  &.linkable {
    cursor: pointer;
  }
}

.block-time {
  font-size: 12px;
  font-weight: 700;
  color: $primary;
  line-height: 1.4;
  text-align: center;
  min-width: 44px;
  font-variant-numeric: tabular-nums;
}

.block-body {
  flex: 1;
  min-width: 0;

  .block-title {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    font-weight: 600;
    color: $text-main;

    .t {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .go {
      color: $text-light;
      font-weight: 400;
    }
  }

  .block-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 6px;

    .tag {
      font-size: 11px;
      padding: 2px 8px;
      border-radius: 999px;
      font-weight: 600;

      &.habit {
        background: rgba(255, 122, 0, 0.1);
        color: $primary;
      }

      &.study {
        background: rgba(52, 120, 246, 0.1);
        color: #3478f6;
      }

      &.checked {
        background: rgba(39, 174, 96, 0.12);
        color: $success;
      }

      &.proposed {
        background: rgba(138, 148, 166, 0.14);
        color: $text-light;
      }

      &.done {
        background: rgba(138, 148, 166, 0.14);
        color: $text-light;
      }
    }
  }
}

.block-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;

  .checkin-box {
    :deep(.van-checkbox__label) {
      font-size: 11px;
      color: $text-light;
    }
  }

  .btn-row {
    display: flex;
    gap: 6px;
  }

  .skip-btn {
    color: $text-light;
  }
}

/* 桌面端只改「学习」胶囊的**文字**色。
   同类里另外三个胶囊的文字色走 $success/$text-light，已随间接层自动降饱和，不用在这重复。
   而各自的淡底（rgba(...) 十几的透明度）渲染出来是很浅的色块、色相几乎不可辨 ——
   按"只处理有面积或有厚度的饱和色"这条线留着不动，移动端更是原样。
   这个蓝 #3478f6 与 --hf-info 里的 #3498db 不是同一个值，共用一个自定义属性
   会改掉其中一边的移动端像素，所以这里直接取桌面端令牌。 */
@media (min-width: #{$bp-desktop}) {
  .block-body .block-tags .tag.study {
    color: $info-desktop;
  }
}
</style>
