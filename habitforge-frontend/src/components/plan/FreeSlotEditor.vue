<script setup lang="ts">
import { ref, watch } from 'vue'
import { showToast, showSuccessToast } from 'vant'
import type { FreeSlot } from '@/types/plan'
import { apiSaveFreeSlots } from '@/api'
import { usePopupPosition } from '@/composables/useDesktop'

const popupPosition = usePopupPosition()
const props = defineProps<{
  show: boolean
  /** 当前已保存的时段（打开时作为初始草稿） */
  slots: FreeSlot[]
  /** yyyy-MM-dd，缺省 = 今天 */
  date?: string
}>()

const emit = defineEmits<{
  (e: 'update:show', v: boolean): void
  (e: 'saved'): void
}>()

/** 纯前端快捷标签预设，点击即添加、可再改 */
const PRESETS = [
  { label: '早', startTime: '06:00', endTime: '08:00' },
  { label: '上午', startTime: '09:00', endTime: '12:00' },
  { label: '下午', startTime: '14:00', endTime: '18:00' },
  { label: '晚', startTime: '19:00', endTime: '22:30' }
]

const MAX_SLOTS = 8

interface Draft {
  label: string
  startTime: string
  endTime: string
}

const drafts = ref<Draft[]>([])
const saving = ref(false)

// 打开时以已保存时段初始化本地草稿
watch(
  () => props.show,
  (v) => {
    if (v) {
      drafts.value = props.slots.map((s) => ({ label: s.label, startTime: s.startTime, endTime: s.endTime }))
    }
  }
)

function addPreset(p: (typeof PRESETS)[number]) {
  if (drafts.value.length >= MAX_SLOTS) {
    showToast(`最多 ${MAX_SLOTS} 个时段`)
    return
  }
  drafts.value.push({ ...p })
}

function removeSlot(i: number) {
  drafts.value.splice(i, 1)
}

// ============ 起止时间编辑（二级弹层，仿日记风格 van-time-picker） ============
const showPicker = ref(false)
const pickerVal = ref<string[]>(['09', '00'])
/** 正在编辑的时段下标与字段；null = 新增模式（暂不用） */
const editing = ref<{ idx: number; field: 'startTime' | 'endTime' } | null>(null)

function openPicker(idx: number, field: 'startTime' | 'endTime') {
  editing.value = { idx, field }
  const [h, m] = drafts.value[idx][field].split(':')
  pickerVal.value = [h, m]
  showPicker.value = true
}

function confirmPick() {
  if (!editing.value) return
  const { idx, field } = editing.value
  drafts.value[idx][field] = `${pickerVal.value[0]}:${pickerVal.value[1]}`
  showPicker.value = false
}

async function onSave() {
  if (saving.value) return
  if (!drafts.value.length) {
    showToast('至少添加 1 个时段')
    return
  }
  // 前端先挡：>8 条、起止非法（后端 6014 兜底，消息由拦截器 toast）
  if (drafts.value.length > MAX_SLOTS) {
    showToast(`最多 ${MAX_SLOTS} 个时段`)
    return
  }
  for (const d of drafts.value) {
    if (d.startTime >= d.endTime) {
      showToast(`「${d.label}」开始时间需早于结束时间`)
      return
    }
  }
  saving.value = true
  try {
    await apiSaveFreeSlots({
      ...(props.date ? { date: props.date } : {}),
      slots: drafts.value.map((d) => ({ startTime: d.startTime, endTime: d.endTime, label: d.label }))
    })
    showSuccessToast('时段已保存')
    emit('update:show', false)
    emit('saved')
  } catch {
    /* 拦截器已 toast（含 6014 重叠/条数消息），弹层保持打开供修改 */
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <van-popup
    class="hf-popup"
    :show="props.show"
    :position="popupPosition"
    round
    :style="{ maxHeight: '85%' }"
    @update:show="emit('update:show', $event)"
  >
    <div class="fs-editor">
      <div class="fs-title">⏰ 设置空闲时段</div>
      <div class="fs-hint text-light">点快捷标签添加常用时段，点时间可修改；AI 只在空闲时段内安排</div>

      <div class="chips">
        <div v-for="p in PRESETS" :key="p.label" class="chip" @click="addPreset(p)">
          {{ p.label }} {{ p.startTime }}-{{ p.endTime }}
        </div>
      </div>

      <div v-if="!drafts.length" class="fs-empty text-light">还没有时段，点上方标签快速添加</div>
      <div v-for="(d, i) in drafts" :key="i" class="slot-row">
        <span class="slot-label">{{ d.label }}</span>
        <span class="slot-time" @click="openPicker(i, 'startTime')">{{ d.startTime }}</span>
        <span class="slot-dash">–</span>
        <span class="slot-time" @click="openPicker(i, 'endTime')">{{ d.endTime }}</span>
        <van-icon name="cross" class="slot-del" @click="removeSlot(i)" />
      </div>

      <van-button block type="primary" color="#ff7a00" round :loading="saving" @click="onSave">
        保存时段（{{ drafts.length }}/{{ MAX_SLOTS }}）
      </van-button>
    </div>
  </van-popup>

  <!-- 起止时间选择二级弹层 -->
  <van-popup class="hf-popup" v-model:show="showPicker" :position="popupPosition" round>
    <div class="picker-head">
      <span class="cancel" @click="showPicker = false">取消</span>
      <span class="ok" @click="confirmPick">确定</span>
    </div>
    <van-time-picker v-model="pickerVal" title="选择时间" />
  </van-popup>
</template>

<style scoped lang="scss">
.fs-editor {
  padding: 20px 16px calc(20px + env(safe-area-inset-bottom));
  max-height: 85vh;
  overflow-y: auto;
}

.fs-title {
  font-size: 16px;
  font-weight: 700;
}

.fs-hint {
  font-size: 12px;
  margin: 6px 0 12px;
  line-height: 1.6;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;

  .chip {
    padding: 7px 12px;
    border-radius: 999px;
    background: rgba(255, 122, 0, 0.08);
    border: 1px solid rgba(255, 122, 0, 0.25);
    color: $primary;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;

    &:active {
      background: rgba(255, 122, 0, 0.18);
    }
  }
}

.fs-empty {
  font-size: 13px;
  text-align: center;
  padding: 18px 0;
}

.slot-row {
  display: flex;
  align-items: center;
  gap: 8px;
  background: $bg-inset;
  border-radius: var(--hf-radius-panel, 10px);
  padding: 10px 12px;
  margin-bottom: 8px;

  .slot-label {
    font-size: 13px;
    font-weight: 700;
    color: $text-main;
    min-width: 32px;
  }

  .slot-time {
    font-size: 14px;
    font-weight: 700;
    color: $primary;
    padding: 2px 8px;
    border-radius: 8px;
    cursor: pointer;

    &:active {
      background: rgba(255, 122, 0, 0.12);
    }
  }

  .slot-dash {
    color: $text-light;
  }

  .slot-del {
    margin-left: auto;
    color: $text-light;
    font-size: 16px;
    padding: 4px;
    cursor: pointer;
  }
}

.picker-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 0;
  font-size: 14px;

  .cancel {
    color: $text-light;
    cursor: pointer;
  }

  .ok {
    color: $primary;
    font-weight: 700;
    cursor: pointer;
  }
}
</style>
