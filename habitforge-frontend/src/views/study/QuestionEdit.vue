<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import type { Question, QuestionCreatePayload, QuestionType, QuestionUpdatePayload } from '@/types/question'
import type { Subject } from '@/types/study'
import { apiQuestion, apiCreateQuestion, apiUpdateQuestion, apiSubjects } from '@/api'
import { usePopupPosition } from '@/composables/useDesktop'

const popupPosition = usePopupPosition()
defineOptions({ name: 'QuestionEdit' })

const route = useRoute()
const router = useRouter()

const editId = computed(() => (route.name === 'QuestionEdit' ? String(route.params.id) : ''))
const isEdit = computed(() => !!editId.value)

const MAX_OPTIONS = 10

const subjects = ref<Subject[]>([])
const subjectName = ref('')
const showSubjectPicker = ref(false)
const subjectColumns = computed(() => subjects.value.map((s) => ({ text: s.name, value: s.id })))

interface OptionRow {
  key: string
  text: string
}

const form = reactive({
  subjectId: '',
  questionType: 'SINGLE' as QuestionType,
  stem: '',
  options: [
    { key: 'A', text: '' },
    { key: 'B', text: '' }
  ] as OptionRow[],
  singleAnswer: '',
  multiAnswer: [] as string[],
  judgeAnswer: 'T',
  shortAnswer: '',
  analysis: '',
  sourceType: 'CUSTOM',
  sourceDetail: '',
  difficulty: 3
})

const hasOptions = computed(() => form.questionType === 'SINGLE' || form.questionType === 'MULTI')

const TYPES: { value: QuestionType; label: string }[] = [
  { value: 'SINGLE', label: '单选' },
  { value: 'MULTI', label: '多选' },
  { value: 'JUDGE', label: '判断' },
  { value: 'SHORT', label: '主观' }
]

const SOURCES: { value: string; label: string }[] = [
  { value: 'CUSTOM', label: '自定义' },
  { value: 'PAST_EXAM', label: '真题' },
  { value: 'TEXTBOOK', label: '教材' },
  { value: 'AI', label: 'AI 生成' }
]

/** 选项 key 始终与位置对齐（删中间项后自动重排 A/B/C…） */
function rekeys() {
  form.options.forEach((o, i) => {
    o.key = String.fromCharCode(65 + i)
  })
}

function addOption() {
  if (form.options.length >= MAX_OPTIONS) return
  form.options.push({ key: '', text: '' })
  rekeys()
}

/** 删掉第 i 个选项后，答案 key（即位置）同步左移；被删项选中则清除 */
function shiftAnswer(deletedIndex: number) {
  const letter = (n: number) => String.fromCharCode(65 + n)
  if (form.singleAnswer) {
    const p = form.singleAnswer.charCodeAt(0) - 65
    if (p === deletedIndex) form.singleAnswer = ''
    else if (p > deletedIndex) form.singleAnswer = letter(p - 1)
  }
  form.multiAnswer = form.multiAnswer
    .map((k) => k.charCodeAt(0) - 65)
    .filter((p) => p !== deletedIndex)
    .map((p) => (p > deletedIndex ? p - 1 : p))
    .sort((a, b) => a - b)
    .map(letter)
}

function removeOption(i: number) {
  if (form.options.length <= 2) return
  form.options.splice(i, 1)
  rekeys()
  shiftAnswer(i)
}

// 切题型时清掉与新题型不符的答案态；ready 前置位防止编辑回填时误清
let ready = false
watch(
  () => form.questionType,
  () => {
    if (!ready) return
    form.singleAnswer = ''
    form.multiAnswer = []
    form.judgeAnswer = 'T'
  }
)

function onSubjectConfirm(p: { selectedValues: (string | number)[] }) {
  const v = String(p.selectedValues[0] ?? '')
  form.subjectId = v
  subjectName.value = subjects.value.find((s) => s.id === v)?.name ?? ''
  showSubjectPicker.value = false
}

async function loadSubjects() {
  try {
    subjects.value = await apiSubjects()
  } catch {
    /* 拦截器已提示 */
  }
}

async function loadQuestion() {
  if (!editId.value) return
  try {
    const q: Question = await apiQuestion(editId.value)
    form.subjectId = q.subjectId
    subjectName.value = subjects.value.find((s) => s.id === q.subjectId)?.name ?? '（科目已删除）'
    form.questionType = q.questionType
    form.stem = q.stem
    form.analysis = q.analysis ?? ''
    form.sourceType = q.sourceType
    form.sourceDetail = q.sourceDetail ?? ''
    form.difficulty = q.difficulty ?? 3
    if (q.options && q.options.length) {
      form.options = q.options.map((o) => ({ ...o }))
    } else if (q.questionType === 'SINGLE' || q.questionType === 'MULTI') {
      form.options = [
        { key: 'A', text: '' },
        { key: 'B', text: '' }
      ]
    }
    if (q.questionType === 'SINGLE') form.singleAnswer = q.answer
    if (q.questionType === 'MULTI') form.multiAnswer = q.answer.split(',').map((s) => s.trim())
    if (q.questionType === 'JUDGE') form.judgeAnswer = q.answer
    if (q.questionType === 'SHORT') form.shortAnswer = q.answer
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(async () => {
  await loadSubjects()
  await loadQuestion()
  ready = true
})

const saving = ref(false)

function buildPayload(): QuestionCreatePayload | QuestionUpdatePayload | null {
  if (!form.subjectId) {
    showToast('请选择科目')
    return null
  }
  if (!form.stem.trim()) {
    showToast('请填写题干')
    return null
  }

  let options: OptionRow[] | null = null
  let answer = ''

  if (hasOptions.value) {
    options = form.options.filter((o) => o.text.trim())
    if (options.length < 2) {
      showToast('至少填写 2 个有内容的选项')
      return null
    }
    if (form.questionType === 'SINGLE') {
      if (!form.singleAnswer) {
        showToast('请选择正确答案')
        return null
      }
      // 删选项/改序后答案 key 可能失效
      if (!options.some((o) => o.key === form.singleAnswer)) {
        showToast('正确答案对应的选项已不存在，请重选')
        return null
      }
      answer = form.singleAnswer
    } else {
      const valid = form.multiAnswer.filter((k) => options!.some((o) => o.key === k))
      if (!valid.length) {
        showToast('请勾选正确答案')
        return null
      }
      answer = [...valid].sort().join(',')
    }
  } else if (form.questionType === 'JUDGE') {
    answer = form.judgeAnswer
  } else {
    answer = form.shortAnswer.trim()
    if (!answer) {
      showToast('请填写参考答案')
      return null
    }
  }

  const common = {
    questionType: form.questionType,
    stem: form.stem.trim(),
    answer,
    analysis: form.analysis.trim() || null,
    sourceType: form.sourceType,
    sourceDetail: form.sourceDetail.trim() || null,
    difficulty: form.difficulty
  }

  if (isEdit.value) {
    const payload: QuestionUpdatePayload = { ...common, options }
    return payload
  }
  const payload: QuestionCreatePayload = {
    ...common,
    subjectId: form.subjectId,
    analysis: form.analysis.trim() || undefined,
    sourceDetail: form.sourceDetail.trim() || undefined
  }
  if (options) payload.options = options
  return payload
}

async function save() {
  const payload = buildPayload()
  if (!payload || saving.value) return
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await apiUpdateQuestion(editId.value, payload as QuestionUpdatePayload)
    } else {
      await apiCreateQuestion(payload as QuestionCreatePayload)
    }
    showSuccessToast('已保存')
    router.back()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <van-nav-bar :title="isEdit ? '编辑题目' : '录题'" left-arrow @click-left="router.back()">
      <template #right>
        <span class="save-btn" @click="save">保存</span>
      </template>
    </van-nav-bar>

    <div class="page-body is-form">
      <van-cell-group inset class="grp">
        <van-field
          v-model="subjectName"
          label="科目"
          placeholder="选择科目"
          readonly
          is-link
          :disabled="isEdit"
          @click="!isEdit && (showSubjectPicker = true)"
        />
        <van-field label="题型">
          <template #input>
            <van-radio-group v-model="form.questionType" direction="horizontal">
              <van-radio v-for="t in TYPES" :key="t.value" :name="t.value">{{ t.label }}</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field v-model="form.stem" label="题干" type="textarea" rows="3" autosize placeholder="题干文字，支持 Markdown" />
      </van-cell-group>

      <!-- SINGLE/MULTI 动态选项编辑器 -->
      <van-cell-group v-if="hasOptions" inset class="grp" title="选项（最多 10 个）">
        <van-field
          v-for="(o, i) in form.options"
          :key="i"
          :label="o.key"
          label-width="34"
          placeholder="选项内容"
          v-model="o.text"
        >
          <template #button>
            <van-icon
              v-if="form.options.length > 2"
              name="delete-o"
              color="#e74c3c"
              size="18"
              class="del-opt"
              @click="removeOption(i)"
            />
          </template>
        </van-field>
        <van-cell>
          <span v-if="form.options.length < MAX_OPTIONS" class="add-opt" @click="addOption">＋ 添加选项</span>
          <span v-else class="text-light">已达 {{ MAX_OPTIONS }} 个选项上限</span>
        </van-cell>
        <!-- 答案：单选 radio / 多选 checkbox -->
        <van-field v-if="form.questionType === 'SINGLE'" label="正确答案">
          <template #input>
            <van-radio-group v-model="form.singleAnswer" direction="horizontal">
              <van-radio v-for="o in form.options" :key="o.key" :name="o.key">{{ o.key }}</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field v-else label="正确答案">
          <template #input>
            <van-checkbox-group v-model="form.multiAnswer" direction="horizontal">
              <van-checkbox v-for="o in form.options" :key="o.key" :name="o.key" shape="square">{{ o.key }}</van-checkbox>
            </van-checkbox-group>
          </template>
        </van-field>
      </van-cell-group>

      <!-- JUDGE 对错答案 -->
      <van-cell-group v-else-if="form.questionType === 'JUDGE'" inset class="grp">
        <van-field label="答案">
          <template #input>
            <van-radio-group v-model="form.judgeAnswer" direction="horizontal">
              <van-radio name="T">正确 ✓</van-radio>
              <van-radio name="F">错误 ✗</van-radio>
            </van-radio-group>
          </template>
        </van-field>
      </van-cell-group>

      <!-- SHORT 参考答案 -->
      <van-cell-group v-else inset class="grp">
        <van-field
          v-model="form.shortAnswer"
          label="参考答案"
          type="textarea"
          rows="3"
          autosize
          placeholder="参考答案，支持 Markdown"
        />
      </van-cell-group>

      <van-cell-group inset class="grp">
        <van-field
          v-model="form.analysis"
          label="解析"
          type="textarea"
          rows="2"
          autosize
          placeholder="可选，支持 Markdown"
        />
        <van-field label="来源">
          <template #input>
            <van-radio-group v-model="form.sourceType" direction="horizontal">
              <van-radio v-for="s in SOURCES" :key="s.value" :name="s.value">{{ s.label }}</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field v-model="form.sourceDetail" label="来源说明" placeholder="如：2025 年 4 月自考真题 第 3 题（可选）" />
        <van-field label="难度">
          <template #input>
            <van-stepper v-model="form.difficulty" :min="1" :max="5" />
          </template>
        </van-field>
      </van-cell-group>

      <div class="submit-row">
        <van-button block round type="primary" color="#ff7a00" :loading="saving" @click="save">
          {{ isEdit ? '保存修改' : '录入库中' }}
        </van-button>
      </div>
    </div>

    <van-popup class="hf-popup" v-model:show="showSubjectPicker" :position="popupPosition" round>
      <van-picker
        :columns="subjectColumns"
        title="选择科目"
        @confirm="onSubjectConfirm"
        @cancel="showSubjectPicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped lang="scss">
.save-btn {
  color: $primary;
  font-size: 14px;
  font-weight: 700;
}

.grp {
  margin-bottom: 12px;
}

.del-opt {
  margin-left: 6px;
  vertical-align: middle;
}

.add-opt {
  color: $primary;
  font-size: 14px;
  font-weight: 700;
}

.submit-row {
  margin-top: 20px;
}
</style>
