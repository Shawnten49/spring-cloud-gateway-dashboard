<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { routesApi } from '@/api/routes'
import type { RouteConfig, RouteRequest, Step } from '@/types'
import { parseRequestJson, toRequestJson, validateRequestClient } from '@/utils/routeJson'

const props = defineProps<{
  modelValue: boolean
  route: RouteConfig | null
  predicateFactories: string[]
  filterFactories: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved'): void
}>()

interface StepRow {
  name: string
  argsText: string
}

const form = reactive({
  routeId: '',
  uri: '',
  order: 0,
  enabled: true,
  predicates: [] as StepRow[],
  filters: [] as StepRow[],
  metadataText: '{}'
})

const jsonMode = ref(false)
const jsonText = ref('')
const saving = ref(false)
const serverErrors = ref<string[]>([])

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const isEdit = computed(() => !!props.route)

function resetForm() {
  form.routeId = ''
  form.uri = ''
  form.order = 0
  form.enabled = true
  form.predicates = []
  form.filters = []
  form.metadataText = '{}'
  jsonMode.value = false
  jsonText.value = ''
  serverErrors.value = []
}

function initFromRoute(route: RouteConfig | null) {
  resetForm()
  if (!route) {
    form.predicates = [{ name: 'Path', argsText: '{"patterns": "/**"}' }]
    return
  }
  form.routeId = route.routeId
  form.uri = route.uri
  form.order = route.order
  form.enabled = route.enabled
  form.predicates = route.predicates.map((step) => ({ name: step.name, argsText: argsToText(step.args) }))
  form.filters = route.filters.map((step) => ({ name: step.name, argsText: argsToText(step.args) }))
  form.metadataText = JSON.stringify(route.metadata || {}, null, 2)
  jsonText.value = toRequestJson(routeToRequest(route))
}

function argsToText(args: Record<string, unknown>): string {
  return Object.keys(args).length === 0 ? '' : JSON.stringify(args, null, 2)
}

function routeToRequest(route: RouteConfig): RouteRequest {
  return {
    routeId: route.routeId,
    uri: route.uri,
    order: route.order,
    enabled: route.enabled,
    predicates: route.predicates,
    filters: route.filters,
    metadata: route.metadata
  }
}

watch(
  () => [props.modelValue, props.route] as const,
  ([open]) => {
    if (open) {
      initFromRoute(props.route)
    }
  },
  { immediate: true }
)

function addStep(kind: 'predicates' | 'filters') {
  form[kind].push({ name: kind === 'predicates' ? 'Path' : '', argsText: '' })
}

function removeStep(kind: 'predicates' | 'filters', index: number) {
  form[kind].splice(index, 1)
}

function enableJsonMode() {
  jsonText.value = toRequestJson(buildRequest())
  jsonMode.value = true
}

function parseArgs(text: string): Record<string, unknown> {
  const trimmed = text.trim()
  if (!trimmed) return {}
  const parsed = JSON.parse(trimmed)
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('参数必须是 JSON 对象，如 {"patterns": "/**"}')
  }
  return parsed as Record<string, unknown>
}

function buildRequest(): RouteRequest {
  return {
    routeId: form.routeId.trim(),
    uri: form.uri.trim(),
    order: form.order || 0,
    enabled: form.enabled,
    predicates: form.predicates.map((row) => ({ name: row.name.trim(), args: parseArgs(row.argsText) })),
    filters: form.filters.map((row) => ({ name: row.name.trim(), args: parseArgs(row.argsText) })),
    metadata: JSON.parse(form.metadataText.trim() || '{}')
  }
}

async function save() {
  serverErrors.value = []
  let request: RouteRequest
  try {
    request = jsonMode.value ? parseRequestJson(jsonText.value) : buildRequest()
  } catch (e) {
    ElMessage.error((e as Error).message)
    return
  }

  const clientErrors = validateRequestClient(request)
  if (clientErrors.length > 0) {
    serverErrors.value = clientErrors
    return
  }

  saving.value = true
  try {
    const validation = await routesApi.validate(request)
    if (!validation.valid) {
      serverErrors.value = validation.errors
      return
    }
    if (isEdit.value && props.route) {
      await routesApi.update(props.route.routeId, request)
    } else {
      await routesApi.create(request)
    }
    ElMessage.success('保存成功，路由已生效')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-drawer
    v-model="visible"
    :title="isEdit ? `编辑路由：${props.route?.routeId}` : '新建路由'"
    size="640px"
    destroy-on-close
  >
    <div v-if="!jsonMode">
      <el-form label-width="110px">
        <el-form-item label="路由 ID">
          <el-input v-model="form.routeId" :disabled="isEdit" placeholder="如 user-service" />
        </el-form-item>
        <el-form-item label="目标地址 URI">
          <el-input v-model="form.uri" placeholder="如 http://user-service:8080 或 lb://user-service" />
        </el-form-item>
        <el-form-item label="执行顺序 order">
          <el-input-number v-model="form.order" :min="0" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <el-divider content-position="left">Predicates（匹配条件）</el-divider>
        <div v-for="(row, index) in form.predicates" :key="'p-' + index" class="step-row">
          <el-select v-model="row.name" filterable allow-create placeholder="选择工厂" style="width: 220px">
            <el-option v-for="name in predicateFactories" :key="name" :label="name" :value="name" />
          </el-select>
          <el-input v-model="row.argsText" type="textarea" :rows="2" placeholder='参数 JSON，如 {"patterns": "/api/**"}' />
          <el-button type="danger" link @click="removeStep('predicates', index)">删除</el-button>
        </div>
        <el-button type="primary" plain size="small" @click="addStep('predicates')">+ 添加 Predicate</el-button>

        <el-divider content-position="left">Filters（过滤器）</el-divider>
        <div v-for="(row, index) in form.filters" :key="'f-' + index" class="step-row">
          <el-select v-model="row.name" filterable allow-create placeholder="选择工厂" style="width: 220px">
            <el-option v-for="name in filterFactories" :key="name" :label="name" :value="name" />
          </el-select>
          <el-input v-model="row.argsText" type="textarea" :rows="2" placeholder='参数 JSON，如 {"name": "X-Token", "value": "abc"}' />
          <el-button type="danger" link @click="removeStep('filters', index)">删除</el-button>
        </div>
        <el-button type="primary" plain size="small" @click="addStep('filters')">+ 添加 Filter</el-button>

        <el-divider content-position="left">元数据 Metadata</el-divider>
        <el-input v-model="form.metadataText" type="textarea" :rows="2" placeholder="{}" />
      </el-form>
    </div>

    <div v-else>
      <el-alert type="info" :closable="false" style="margin-bottom: 12px" title="高级模式：直接编辑完整路由 JSON，保存前会做服务端校验" />
      <el-input v-model="jsonText" type="textarea" :rows="22" style="font-family: monospace" />
    </div>

    <div v-if="serverErrors.length" style="margin-top: 12px">
      <el-alert
        v-for="(err, i) in serverErrors"
        :key="i"
        type="error"
        :closable="false"
        :title="err"
        style="margin-bottom: 6px"
      />
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button @click="jsonMode = !jsonMode">
        {{ jsonMode ? '返回表单模式' : '高级 JSON 模式' }}
      </el-button>
      <el-button type="primary" :loading="saving" @click="save">保存并生效</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.step-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  align-items: flex-start;
}
</style>
