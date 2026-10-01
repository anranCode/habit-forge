<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useGoBack } from '@/composables/useGoBack'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import type { Habit } from '@/types/habit'
import type { Contract } from '@/types/contract'
import {
  apiContractStatus,
  apiContracts,
  apiCreateContract,
  apiDeleteContract,
  apiListHabits
} from '@/api'

const goBack = useGoBack()

const list = ref<Contract[]>([])
const habits = ref<Habit[]>([])
const loading = ref(false)
const busy = ref(false)

const showForm = ref(false)
const showHabitPicker = ref(false)
const form = ref({ habitId: '', partnerName: '', penalty: '', isPublic: false })

const habitName = computed(() => habits.value.find((h) => h.id === form.value.habitId)?.name || '请选择习惯')
const habitColumns = computed(() => habits.value.map((h) => ({ text: h.name, value: h.id })))

const STATUS_LABEL: Record<string, string> = { ACTIVE: '进行中', COMPLETED: '已做到', BROKEN: '已破戒' }

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [cs, hs] = await Promise.all([apiContracts(), apiListHabits(true).catch(() => [] as Habit[])])
    list.value = cs
    habits.value = hs
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    loading.value = false
  }
}

function openForm() {
  if (!habits.value.length) {
    showToast('先创建一个习惯，契约要挂在习惯上')
    return
  }
  form.value = { habitId: habits.value[0].id, partnerName: '', penalty: '', isPublic: false }
  showForm.value = true
}

function onHabitConfirm({ selectedOptions }: { selectedOptions: { value: string }[] }) {
  showHabitPicker.value = false
  form.value.habitId = selectedOptions[0]?.value ?? ''
}

async function submit() {
  if (!form.value.habitId) {
    showToast('请选择习惯')
    return
  }
  if (!form.value.partnerName.trim()) {
    showToast('请填写问责伙伴')
    return
  }
  if (!form.value.penalty.trim()) {
    showToast('请填写违约代价')
    return
  }
  busy.value = true
  try {
    await apiCreateContract({
      habitId: form.value.habitId,
      partnerName: form.value.partnerName.trim(),
      penalty: form.value.penalty.trim(),
      isPublic: form.value.isPublic
    })
    showForm.value = false
    showSuccessToast('契约已签订')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = false
  }
}

async function setStatus(item: Contract, status: 'COMPLETED' | 'BROKEN' | 'ACTIVE') {
  try {
    const updated = await apiContractStatus(item.id, status)
    item.status = updated.status
    showSuccessToast(STATUS_LABEL[status] + '，已记录')
  } catch {
    /* 拦截器已提示 */
  }
}

async function remove(item: Contract) {
  try {
    await showConfirmDialog({ title: '删除这份契约？', message: item.partnerName + ' · ' + item.penalty })
  } catch {
    return
  }
  try {
    await apiDeleteContract(item.id)
    list.value = list.value.filter((x) => x.id !== item.id)
  } catch {
    /* 拦截器已提示 */
  }
}
</script>

<template>
  <div>
    <van-nav-bar title="习惯契约" left-arrow @click-left="goBack">
      <template #right>
        <span class="nav-btn" @click="openForm">新建</span>
      </template>
    </van-nav-bar>

    <div class="page-body">
      <div class="card intro">
        <div class="t">让承诺有分量</div>
        <div class="d text-light">
          写下问责伙伴和违约代价 —— 哪怕只是"做不到就请对方喝一杯咖啡"。
          人的一致性倾向会让写下来的承诺比心里的决心更有约束力。
        </div>
      </div>

      <div v-if="loading" class="card empty-tip">加载中…</div>

      <template v-else-if="list.length">
        <div v-for="item in list" :key="item.id" class="card ct" :class="item.status.toLowerCase()">
          <div class="head">
            <span class="habit">{{ item.habitName || '（习惯已删除）' }}</span>
            <van-tag :type="item.status === 'ACTIVE' ? 'primary' : item.status === 'COMPLETED' ? 'success' : 'danger'">
              {{ STATUS_LABEL[item.status] || item.status }}
            </van-tag>
          </div>
          <div class="row"><span class="lb text-light">问责伙伴</span><span class="v">{{ item.partnerName }}</span></div>
          <div class="row"><span class="lb text-light">违约代价</span><span class="v">{{ item.penalty }}</span></div>
          <div class="row" v-if="item.isPublic"><span class="lb text-light">公开</span><span class="v">是</span></div>

          <div class="actions">
            <van-button
              v-if="item.status !== 'ACTIVE'"
              size="mini"
              plain
              round
              @click="setStatus(item, 'ACTIVE')"
            >
              恢复进行中
            </van-button>
            <template v-else>
              <van-button size="mini" type="success" round @click="setStatus(item, 'COMPLETED')">做到</van-button>
              <van-button size="mini" type="danger" plain round @click="setStatus(item, 'BROKEN')">破戒</van-button>
            </template>
            <van-button size="mini" plain round @click="remove(item)">删除</van-button>
          </div>
        </div>
      </template>

      <div v-else class="card empty-tip">
        <p>还没有契约</p>
        <p class="sub">挑一个你最想坚持的习惯，找个愿意盯你的人</p>
        <van-button size="small" type="primary" color="#ff7a00" round class="create-btn" @click="openForm">
          新建契约
        </van-button>
      </div>
    </div>

    <!-- 新建契约 -->
    <van-popup class="hf-popup" v-model:show="showForm" position="bottom" round>
      <div class="form">
        <div class="form-title">新建习惯契约</div>
        <van-field :model-value="habitName" label="习惯" readonly is-link @click="showHabitPicker = true" />
        <van-field v-model="form.partnerName" label="问责伙伴" placeholder="姓名（如：老婆 / 老张）" maxlength="100" />
        <van-field
          v-model="form.penalty"
          label="违约代价"
          type="textarea"
          rows="2"
          autosize
          maxlength="500"
          placeholder="如：做不到就请对方喝一杯咖啡"
        />
        <van-cell title="公开这份契约" center>
          <template #right-icon>
            <van-switch v-model="form.isPublic" size="20" />
          </template>
        </van-cell>
        <div class="form-actions">
          <van-button plain round block @click="showForm = false">取消</van-button>
          <van-button type="primary" color="#ff7a00" round block :loading="busy" @click="submit">
            签订
          </van-button>
        </div>
      </div>
    </van-popup>

    <van-popup class="hf-popup" v-model:show="showHabitPicker" position="bottom" round>
      <van-picker
        :columns="habitColumns"
        title="选择习惯"
        @confirm="onHabitConfirm"
        @cancel="showHabitPicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
.nav-btn {
  color: $primary;
  font-size: 14px;
  font-weight: 600;
}

.intro {
  .t {
    font-size: 15px;
    font-weight: 700;
  }

  .d {
    font-size: 12px;
    line-height: 1.6;
    margin-top: 6px;
  }
}

.ct {
  .head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;

    .habit {
      font-size: 15px;
      font-weight: 700;
    }
  }

  .row {
    display: flex;
    gap: 10px;
    font-size: 13px;
    margin-top: 8px;

    .lb {
      width: 64px;
      flex-shrink: 0;
    }

    .v {
      flex: 1;
      word-break: break-word;
    }
  }

  .actions {
    display: flex;
    gap: 8px;
    margin-top: 12px;
  }

  &.completed {
    opacity: 0.72;
  }

  &.broken {
    opacity: 0.72;
  }
}

.form {
  padding: 16px;

  .form-title {
    font-size: 15px;
    font-weight: 700;
    margin-bottom: 8px;
  }

  .form-actions {
    display: flex;
    gap: 10px;
    margin-top: 14px;
  }
}

.create-btn {
  margin-top: 14px;
}

.empty-tip .sub {
  font-size: 12px;
  margin-top: 6px;
}
</style>
