<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { gatewayApi } from '@/api/gateway'
import type { GatewayStatus, Step } from '@/types'

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

/** Predicates/Filters 完整 JSON 串（如 [{"name":"Path","args":{"patterns":"/api/user/**"}}]）；空数组显示 - */
function stepsJson(steps: Step[]): string {
  return steps.length ? JSON.stringify(steps) : '-'
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
      <el-table-column label="Predicates" min-width="200">
        <template #default="{ row }">
          <el-tooltip v-if="stepsJson(row.predicates) !== '-'" placement="top" popper-class="json-tooltip" :show-after="300" :content="stepsJson(row.predicates)">
            <span class="cell-ellipsis">{{ stepsJson(row.predicates) }}</span>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="Filters" min-width="180">
        <template #default="{ row }">
          <el-tooltip v-if="stepsJson(row.filters) !== '-'" placement="top" popper-class="json-tooltip" :show-after="300" :content="stepsJson(row.filters)">
            <span class="cell-ellipsis">{{ stepsJson(row.filters) }}</span>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="order" label="顺序" width="70" />
      <template #empty>暂无生效路由</template>
    </el-table>

    <el-divider content-position="left">外部网关实例</el-divider>

    <el-alert
      v-if="!status?.externalGateways?.length"
      type="info"
      :closable="false"
      title="未配置外部网关实例（application.yml 的 gateway-dashboard.external-gateways），当前仅管理内嵌网关"
      style="margin-bottom: 12px"
    />

    <div v-for="gw in status?.externalGateways ?? []" :key="gw.baseUrl" class="gw-card">
      <el-card>
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span style="font-weight: 600">{{ gw.baseUrl }}</span>
            <el-tag :type="gw.online ? 'success' : 'danger'">
              {{ gw.online ? '在线' : '离线' }}
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="2" size="small" style="margin-bottom: 10px">
          <el-descriptions-item label="最近检查">
            {{ formatTime(gw.lastCheckedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="最近推送">
            <template v-if="gw.push">
              <el-tag :type="gw.push.success ? 'success' : 'danger'" size="small">
                {{ gw.push.success ? '成功' : '失败' }}
              </el-tag>
              <span style="margin-left: 6px">{{ formatTime(gw.push.lastPushAt) }}</span>
              <div v-if="gw.push.error" style="color: #f56c6c; font-size: 12px">
                {{ gw.push.error }}
              </div>
            </template>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="生效路由数">
            {{ gw.effectiveRoutes.length }}
          </el-descriptions-item>
          <el-descriptions-item v-if="gw.error" label="连接异常" :span="2">
            <span style="color: #f56c6c; font-size: 12px">{{ gw.error }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-table :data="gw.effectiveRoutes" size="small" border max-height="260">
          <el-table-column prop="routeId" label="路由 ID" min-width="160" />
          <el-table-column prop="uri" label="目标地址" min-width="200" show-overflow-tooltip />
          <el-table-column prop="order" label="顺序" width="70" />
          <template #empty>离线或无生效路由</template>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.gw-card {
  margin-bottom: 14px;
}
/* .cell-ellipsis 与 .json-tooltip 为全局样式（src/style.css），两处表格共用 */
</style>
