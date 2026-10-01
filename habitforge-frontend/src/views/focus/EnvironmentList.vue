<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useGoBack } from '@/composables/useGoBack'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import type { EnvironmentSetting, EnvironmentSettingPayload } from '@/types/environment'
import {
  apiBatchCreateEnvironmentSettings,
  apiCreateEnvironmentSetting,
  apiDeleteEnvironmentSetting,
  apiEnvironmentSettings,
  apiToggleEnvironmentSetting
} from '@/api'

const goBack = useGoBack()

const list = ref<EnvironmentSetting[]>([])
const loading = ref(false)
const busy = ref(false)
const newText = ref('')

/** 手机节制预设：一键导入（后端按描述去重，可重复点） */
const PRESETS: EnvironmentSettingPayload[] = [
  { type: 'RESISTANCE', category: 'PHONE', description: '手机充电器移出卧室，睡前不在床上刷手机' },
  { type: 'RESISTANCE', category: 'PHONE', description: '卸载短视频 App，需要时只用网页版' },
  { type: 'RESISTANCE', category: 'PHONE', description: '把手机屏幕调成灰度模式（颜色本身就是钩子）' },
  { type: 'RESISTANCE', category: 'PHONE', description: '关闭所有非必要推送通知' },
  { type: 'RESISTANCE', category: 'PHONE', description: '用系统「屏幕使用时间」给娱乐类 App 设限额' },
  { type: 'RESISTANCE', category: 'PHONE', description: '工作/学习时把手机放到另一个房间' },
  { type: 'PROMPT', category: 'PHONE', description: '书桌上放一本书，代替伸手拿手机' },
  { type: 'COMMITMENT', category: 'PHONE', description: '和家人约定：吃饭时手机放进抽屉' }
]

const TYPE_LABEL: Record<string, string> = {
  PROMPT: '让它显而易见',
  RESISTANCE: '让它难以发生',
  COMMITMENT: '承诺机制'
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = await apiEnvironmentSettings('PHONE')
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    loading.value = false
  }
}

async function importPresets() {
  if (busy.value) return
  busy.value = true
  try {
    const created = await apiBatchCreateEnvironmentSettings(PRESETS)
    await load()
    showSuccessToast(created.length ? `已添加 ${created.length} 项` : '推荐清单都已经在了')
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = false
  }
}

async function add() {
  const text = newText.value.trim()
  if (!text) {
    showToast('请填写清单内容')
    return
  }
  busy.value = true
  try {
    await apiCreateEnvironmentSetting({ type: 'RESISTANCE', category: 'PHONE', description: text })
    newText.value = ''
    await load()
    showSuccessToast('已添加')
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = false
  }
}

async function toggle(item: EnvironmentSetting) {
  try {
    const updated = await apiToggleEnvironmentSetting(item.id, !item.isActive)
    item.isActive = updated.isActive
  } catch {
    /* 拦截器已提示 */
  }
}

async function remove(item: EnvironmentSetting) {
  try {
    await showConfirmDialog({ title: '删除这一项？', message: item.description })
  } catch {
    return
  }
  try {
    await apiDeleteEnvironmentSetting(item.id)
    list.value = list.value.filter((x) => x.id !== item.id)
  } catch {
    /* 拦截器已提示 */
  }
}

const activeCount = () => list.value.filter((x) => x.isActive).length
</script>

<template>
  <div>
    <van-nav-bar title="环境设计清单" left-arrow @click-left="goBack" />

    <div class="page-body">
      <div class="card intro">
        <div class="t">让坏习惯难以发生</div>
        <div class="d text-light">
          《掌控习惯》第一定律的反面用法：与其每天和手机较劲意志力，不如先把环境改掉。
          勾选出你已经做到的，做不到的先留着当清单。
        </div>
        <div class="stat text-light">已生效 {{ activeCount() }} / {{ list.length }} 项</div>
      </div>

      <div class="card">
        <van-button
          type="primary"
          color="#ff7a00"
          round
          block
          size="small"
          :loading="busy"
          @click="importPresets"
        >
          一键导入手机节制推荐清单
        </van-button>
        <div class="text-light tip">已存在的项会自动跳过，可以放心重复点。</div>
      </div>

      <div class="card">
        <div class="add-row">
          <van-field v-model="newText" placeholder="自定义一项，如：睡前把手机放客厅" maxlength="500" />
          <van-button type="primary" color="#ff7a00" round size="small" :loading="busy" @click="add">
            添加
          </van-button>
        </div>
      </div>

      <div v-if="loading" class="card empty-tip">加载中…</div>

      <template v-else-if="list.length">
        <div v-for="item in list" :key="item.id" class="card item" :class="{ done: item.isActive }">
          <van-checkbox
            :model-value="item.isActive"
            shape="square"
            @click="toggle(item)"
          />
          <div class="body" @click="toggle(item)">
            <div class="desc">{{ item.description }}</div>
            <div class="meta text-light">{{ TYPE_LABEL[item.type] || item.type }}</div>
          </div>
          <van-icon name="delete-o" color="var(--hf-text-light)" @click.stop="remove(item)" />
        </div>
      </template>

      <div v-else class="card empty-tip">
        <p>清单还是空的</p>
        <p class="sub">点上面的按钮导入推荐清单，或自己加一条</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
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

  .stat {
    font-size: 12px;
    margin-top: 10px;
  }
}

.tip {
  font-size: 12px;
  margin-top: 8px;
}

.add-row {
  display: flex;
  align-items: center;
  gap: 8px;

  :deep(.van-cell) {
    padding: 0;
    background: transparent;
  }

  :deep(.van-cell::after) {
    display: none;
  }
}

.item {
  display: flex;
  align-items: center;
  gap: 10px;

  .body {
    flex: 1;
    min-width: 0;
    cursor: pointer;

    .desc {
      font-size: 14px;
      line-height: 1.5;
    }

    .meta {
      font-size: 11px;
      margin-top: 3px;
    }
  }

  &.done .desc {
    color: $text-light;
    text-decoration: line-through;
  }
}

.empty-tip .sub {
  font-size: 12px;
  margin-top: 6px;
}
</style>
