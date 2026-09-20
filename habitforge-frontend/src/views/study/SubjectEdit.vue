<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useGoBack } from '@/composables/useGoBack'
import { showSuccessToast, showToast } from 'vant'
import dayjs from 'dayjs'
import { apiSubjectDetail, apiCreateSubject, apiUpdateSubject } from '@/api'
import { useIsDesktop, usePopupPosition } from '@/composables/useDesktop'

const isDesktop = useIsDesktop()
const popupPosition = usePopupPosition()
const route = useRoute()

/** 无历史可退时按路由的 meta.backTo 兜底（桌面端可直接深链进详情页） */
const goBack = useGoBack()

/** 编辑模式：/study/subjects/edit/:id；创建模式：/study/subjects/create */
const id = route.params.id as string | undefined
const isEdit = computed(() => !!id)

const name = ref('')
const examDate = ref<string | null>(null)
const examSession = ref('')
const description = ref('')
const saving = ref(false)

const showDatePicker = ref(false)
const pickerValue = ref<string[]>(dayjs().format('YYYY-MM-DD').split('-'))

onMounted(load)

async function load() {
  if (!id) return
  try {
    const s = await apiSubjectDetail(id)
    if (!s) {
      showToast('科目不存在或已删除')
      goBack()
      return
    }
    name.value = s.name
    examDate.value = s.examDate
    examSession.value = s.examSession || ''
    description.value = s.description || ''
    if (s.examDate) pickerValue.value = s.examDate.split('-')
  } catch {
    /* 错误已由拦截器提示 */
  }
}

function openDate() {
  if (examDate.value) pickerValue.value = examDate.value.split('-')
  showDatePicker.value = true
}

function onDateConfirm({ selectedValues }: { selectedValues: string[] }) {
  showDatePicker.value = false
  examDate.value = selectedValues.join('-')
}

async function onSave() {
  if (saving.value) return
  saving.value = true
  try {
    const payload = {
      name: name.value.trim(),
      examDate: examDate.value || undefined,
      examSession: examSession.value.trim() || undefined,
      description: description.value.trim() || undefined
    }
    if (isEdit.value && id) {
      await apiUpdateSubject(id, payload)
      showSuccessToast('已保存')
    } else {
      await apiCreateSubject(payload)
      showSuccessToast('科目已创建')
    }
    goBack()
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <van-nav-bar :title="isEdit ? '编辑科目' : '新建科目'" left-arrow @click-left="goBack" />

    <div class="page-body is-form">
      <van-form @submit="onSave">
        <div class="card">
          <!-- 名称 -->
          <div class="field-label">科目名称 *</div>
          <van-field
            v-model="name"
            name="name"
            placeholder="如：高等数学（一）"
            maxlength="100"
            clearable
            :rules="[{ required: true, message: '请输入科目名称' }]"
          />

          <!-- 考试日期（底部弹层） -->
          <div class="field-label">考试日期</div>
          <div class="date-row" @click="openDate">
            <span class="d">
              {{ (isDesktop ? '' : '📅 ') + (examDate ? dayjs(examDate).format('YYYY年M月D日 ddd') : '未设置（可选）') }}
            </span>
            <span class="text-light change">修改 ›</span>
          </div>

          <!-- 考期 -->
          <div class="field-label">考期</div>
          <van-field v-model="examSession" placeholder="如：2026 年 10 月（可选）" maxlength="50" />

          <!-- 描述 -->
          <div class="field-label">描述</div>
          <van-field
            v-model="description"
            type="textarea"
            rows="3"
            autosize
            maxlength="500"
            placeholder="教材、学分目标、备注…（可选）"
          />
        </div>

        <van-button
          block
          type="primary"
          color="#ff7a00"
          round
          native-type="submit"
          :loading="saving"
        >
          {{ isEdit ? '保存修改' : '创建科目' }}
        </van-button>
      </van-form>
    </div>

    <van-popup class="hf-popup" v-model:show="showDatePicker" :position="popupPosition" round>
      <van-date-picker
        v-model="pickerValue"
        title="选择考试日期"
        :min-date="new Date(2020, 0, 1)"
        :max-date="new Date(2040, 11, 31)"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
.field-label {
  font-size: 13px;
  font-weight: 600;
  margin: 14px 0 8px;

  &:first-child {
    margin-top: 0;
  }
}

.date-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: $bg-inset;
  border-radius: var(--hf-radius-panel, 10px);
  padding: 12px;
  cursor: pointer;

  .d {
    font-weight: 700;
    font-size: 14px;
  }

  .change {
    font-size: 12px;
  }
}

:deep(.van-cell) {
  padding-left: 0;
  padding-right: 0;

  &::after {
    display: none;
  }
}
</style>
