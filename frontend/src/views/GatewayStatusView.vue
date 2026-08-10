<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { gatewayApi } from '@/api/gateway'
import type { GatewayStatus } from '@/types'

const status = ref<GatewayStatus | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    status.value = await gatewayApi.status()
  } finally {
    loading.value = false
  }
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString() : '-'
}

function predicateNames(row: { predicates: { name: string }[] }): string {
  return row.predicates.map((p) => p.name).join(', ') || '-'
}

function filterNames(row: { filters: { name: string }[] }): string {
  return row.filters.map((f) => f.name).join(', ') || '-'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-toolbar">
      <el-space>
        <span>健康状态：</span>
        <el-tag :type="status?.health === 'UP' ? 'success' : 'danger'">
          {{ status?.health === 'UP' ? '正常' : '异常' }}
        </el-tag>
        <span>最近刷新：{{ formatTime(status?.lastRefreshAt ?? null) }}</span>
      </el-space>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="status?.effectiveRoutes ?? []" border stripe>
      <el-table-column prop="routeId" label="路由 ID" min-width="180" />
      <el-table-column prop="uri" label="目标地址" min-width="220" show-overflow-tooltip />
      <el-table-column label="Predicates" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ predicateNames(row) }}</template>
      </el-table-column>
      <el-table-column label="Filters" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ filterNames(row) }}</template>
      </el-table-column>
      <el-table-column prop="order" label="顺序" width="70" />
      <template #empty>暂无生效路由</template>
    </el-table>
  </div>
</template>
