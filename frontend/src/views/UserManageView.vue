<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userAdminApi, type AdminUser } from '@/api/userAdmin'

const SPECIAL_USERNAME = 'admin'

const users = ref<AdminUser[]>([])
const keyword = ref('')
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)

const form = reactive({
  username: '',
  password: '',
  role: 'VIEWER'
})

async function load() {
  loading.value = true
  try {
    users.value = await userAdminApi.list(keyword.value || undefined)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.username = ''
  form.password = ''
  form.role = 'VIEWER'
  dialogVisible.value = true
}

async function create() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (form.password.length < 8) {
    ElMessage.warning('密码长度至少 8 位')
    return
  }
  saving.value = true
  try {
    await userAdminApi.create({ username: form.username.trim(), password: form.password, role: form.role })
    ElMessage.success('用户已创建')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(user: AdminUser) {
  try {
    const next = !user.enabled
    await userAdminApi.setEnabled(user.id, next)
    ElMessage.success(next ? '已启用' : '已屏蔽')
    await load()
  } catch {
    // 拦截器已提示（如 admin 不可屏蔽）
  }
}

function formatTime(value: string): string {
  return value ? new Date(value).toLocaleString() : '-'
}

function roleType(role: string): 'success' | 'warning' | 'info' | 'danger' {
  return role === 'ADMIN' ? 'danger' : role === 'VIEWER' ? 'success' : 'info'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-toolbar">
      <div style="display: flex; gap: 10px">
        <el-input v-model="keyword" placeholder="按用户名搜索" clearable style="width: 240px" @keyup.enter="load" />
        <el-button @click="load">查询</el-button>
      </div>
      <el-button type="primary" @click="openCreate">新建用户</el-button>
    </div>

    <el-table v-loading="loading" :data="users" border stripe>
      <el-table-column prop="username" label="用户名" min-width="160" />
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="roleType(row.role)" size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '已屏蔽' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <!-- admin 为特殊用户：不允许屏蔽（需求 FR4），启用按钮无意义（admin 恒启用） -->
          <el-tooltip v-if="row.username === SPECIAL_USERNAME && row.enabled" content="特殊用户 admin 不可屏蔽" placement="top">
            <span>
              <el-button link type="danger" disabled>屏蔽</el-button>
            </span>
          </el-tooltip>
          <el-button v-else-if="row.enabled" link type="danger" @click="toggleEnabled(row)">屏蔽</el-button>
          <el-button v-else link type="success" @click="toggleEnabled(row)">启用</el-button>
        </template>
      </el-table-column>
      <template #empty>暂无用户</template>
    </el-table>

    <el-dialog v-model="dialogVisible" title="新建用户" width="460px">
      <el-form label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="登录用户名，不可重复" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="ADMIN（管理员）" value="ADMIN" />
            <el-option label="VIEWER（只读）" value="VIEWER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
