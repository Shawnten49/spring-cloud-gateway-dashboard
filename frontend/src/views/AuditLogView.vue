<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { auditApi } from '@/api/audit'
import type { AuditLog } from '@/types'

const items = ref<AuditLog[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await auditApi.page(page.value, size.value)
    items.value = result.items
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function actionType(action: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (action) {
    case 'CREATE':
    case 'ENABLE':
      return 'success'
    case 'UPDATE':
    case 'DISABLE':
      return 'warning'
    case 'DELETE':
      return 'danger'
    default:
      return 'info'
  }
}

function formatTime(value: string): string {
  return value ? new Date(value).toLocaleString() : '-'
}

onMounted(load)
</script>

<template>
  <div>
    <el-table v-loading="loading" :data="items" border stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <div style="padding: 8px 16px">
            <div><strong>变更前：</strong></div>
            <pre class="json-preview">{{ row.beforeJson || '（无）' }}</pre>
            <div><strong>变更后：</strong></div>
            <pre class="json-preview">{{ row.afterJson || '（无）' }}</pre>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="actorUsername" label="操作者" width="130" />
      <el-table-column label="动作" width="100">
        <template #default="{ row }">
          <el-tag :type="actionType(row.action)" size="small">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="routeId" label="路由 ID" min-width="180" />
      <el-table-column prop="ip" label="IP" width="140" />
    </el-table>

    <div style="display: flex; justify-content: flex-end; margin-top: 14px">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>
  </div>
</template>

<style scoped>
.json-preview {
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
