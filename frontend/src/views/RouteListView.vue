<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { routesApi } from '@/api/routes'
import { metaApi } from '@/api/meta'
import { useAuthStore } from '@/stores/auth'
import type { RouteConfig } from '@/types'
import RouteEditorDrawer from '@/components/RouteEditorDrawer.vue'

const auth = useAuthStore()
const routes = ref<RouteConfig[]>([])
const keyword = ref('')
const loading = ref(false)
const drawerVisible = ref(false)
const editingRoute = ref<RouteConfig | null>(null)
const predicateFactories = ref<string[]>([])
const filterFactories = ref<string[]>([])

async function loadRoutes() {
  loading.value = true
  try {
    routes.value = await routesApi.list(keyword.value || undefined)
  } finally {
    loading.value = false
  }
}

async function loadFactories() {
  const predicates = await metaApi.factories('predicate')
  const filters = await metaApi.factories('filter')
  predicateFactories.value = predicates.predicate || []
  filterFactories.value = filters.filter || []
}

function openCreate() {
  editingRoute.value = null
  drawerVisible.value = true
}

function openEdit(route: RouteConfig) {
  editingRoute.value = route
  drawerVisible.value = true
}

async function toggleEnabled(route: RouteConfig) {
  try {
    const next = !route.enabled
    await routesApi.setEnabled(route.routeId, next)
    ElMessage.success(next ? '已启用' : '已停用')
    await loadRoutes()
  } catch {
    // 拦截器已提示
  }
}

async function removeRoute(route: RouteConfig) {
  try {
    await ElMessageBox.confirm(`确定删除路由「${route.routeId}」吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await routesApi.remove(route.routeId)
    ElMessage.success('已删除')
    await loadRoutes()
  } catch {
    // 用户取消或请求失败
  }
}

function stepSummary(steps: { name: string }[]): string {
  return steps.map((s) => s.name).join(', ') || '-'
}

function formatTime(value: string): string {
  return value ? new Date(value).toLocaleString() : '-'
}

onMounted(() => {
  loadRoutes()
  loadFactories()
})
</script>

<template>
  <div>
    <div class="page-toolbar">
      <div style="display: flex; gap: 10px">
        <el-input v-model="keyword" placeholder="按路由 ID / URI 搜索" clearable style="width: 280px" @keyup.enter="loadRoutes" />
        <el-button @click="loadRoutes">查询</el-button>
      </div>
      <el-button v-if="auth.isAdmin" type="primary" @click="openCreate">新建路由</el-button>
    </div>

    <el-table v-loading="loading" :data="routes" border stripe>
      <el-table-column prop="routeId" label="路由 ID" min-width="180" />
      <el-table-column prop="uri" label="目标地址" min-width="220" show-overflow-tooltip />
      <el-table-column label="Predicates" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ stepSummary(row.predicates) }}</template>
      </el-table-column>
      <el-table-column label="Filters" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ stepSummary(row.filters) }}</template>
      </el-table-column>
      <el-table-column prop="order" label="顺序" width="70" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column v-if="auth.isAdmin" label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggleEnabled(row)">
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" @click="removeRoute(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>暂无路由配置</template>
    </el-table>

    <RouteEditorDrawer
      v-model="drawerVisible"
      :route="editingRoute"
      :predicate-factories="predicateFactories"
      :filter-factories="filterFactories"
      @saved="loadRoutes"
    />
  </div>
</template>
