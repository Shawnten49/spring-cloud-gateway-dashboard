<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { permissionsApi } from '@/api/permissions'
import type { PermissionRule, PermissionRuleRequest } from '@/types'

const rules = ref<PermissionRule[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<PermissionRule | null>(null)
const saving = ref(false)

const methodOptions = ['*', 'GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS', 'HEAD']

const form = reactive<PermissionRuleRequest>({
  name: '',
  httpMethod: 'GET',
  pathPattern: '',
  roles: '',
  priority: 0,
  enabled: true
})

async function load() {
  loading.value = true
  try {
    rules.value = await permissionsApi.list()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.name = ''
  form.httpMethod = 'GET'
  form.pathPattern = ''
  form.roles = ''
  form.priority = 0
  form.enabled = true
  dialogVisible.value = true
}

function openEdit(rule: PermissionRule) {
  editing.value = rule
  form.name = rule.name
  form.httpMethod = rule.httpMethod
  form.pathPattern = rule.pathPattern
  form.roles = rule.roles
  form.priority = rule.priority
  form.enabled = rule.enabled
  dialogVisible.value = true
}

async function save() {
  if (!form.name.trim() || !form.pathPattern.trim() || !form.roles.trim()) {
    ElMessage.warning('请填写完整：名称、路径、角色')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await permissionsApi.update(editing.value.id, { ...form })
    } else {
      await permissionsApi.create({ ...form })
    }
    ElMessage.success('已保存，权限规则即时生效')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(rule: PermissionRule) {
  try {
    await permissionsApi.update(rule.id, {
      name: rule.name,
      httpMethod: rule.httpMethod,
      pathPattern: rule.pathPattern,
      roles: rule.roles,
      priority: rule.priority,
      enabled: !rule.enabled
    })
    ElMessage.success(rule.enabled ? '已停用' : '已启用')
    await load()
  } catch {
    await load()
  }
}

async function removeRule(rule: PermissionRule) {
  try {
    await ElMessageBox.confirm(`确定删除规则「${rule.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await permissionsApi.remove(rule.id)
    ElMessage.success('已删除，规则即时生效')
    await load()
  } catch {
    // 用户取消或请求失败（内置规则不可删除等）
  }
}

function formatTime(value: string): string {
  return value ? new Date(value).toLocaleString() : '-'
}

function methodType(method: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  switch (method) {
    case 'GET':
      return 'success'
    case 'POST':
      return 'warning'
    case 'PUT':
    case 'PATCH':
      return 'primary'
    case 'DELETE':
      return 'danger'
    default:
      return 'info'
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-toolbar">
      <el-alert type="info" :closable="false" style="flex: 1; margin-right: 12px">
        规则按优先级匹配，数字越小越先匹配（首个命中的规则生效）。roles 取值：
        <code>*</code> = 公开；<code>AUTHENTICATED</code> = 登录即可；角色列表如 <code>ADMIN,VIEWER</code>。
        修改后即时生效，无需重启。内置规则不可删除。
      </el-alert>
      <el-button type="primary" @click="openCreate">新建规则</el-button>
    </div>

    <el-table v-loading="loading" :data="rules" border stripe>
      <el-table-column prop="name" label="规则名称" min-width="150" />
      <el-table-column label="方法" width="90">
        <template #default="{ row }">
          <el-tag :type="methodType(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="pathPattern" label="路径" min-width="200">
        <template #default="{ row }">
          <code>{{ row.pathPattern }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="roles" label="允许角色" min-width="160" />
      <el-table-column prop="priority" label="优先级" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内置" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.builtin" type="warning" size="small">内置</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggleEnabled(row)">
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="!row.builtin" link type="danger" @click="removeRule(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? `编辑规则：${editing.name}` : '新建权限规则'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="规则名称">
          <el-input v-model="form.name" placeholder="如 路由-新增" />
        </el-form-item>
        <el-form-item label="HTTP 方法">
          <el-select v-model="form.httpMethod" style="width: 100%">
            <el-option v-for="m in methodOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径模式">
          <el-input v-model="form.pathPattern" placeholder="/api/routes/**" />
        </el-form-item>
        <el-form-item label="允许角色">
          <el-input v-model="form.roles" placeholder="ADMIN 或 ADMIN,VIEWER 或 AUTHENTICATED 或 *" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存并生效</el-button>
      </template>
    </el-dialog>
  </div>
</template>
