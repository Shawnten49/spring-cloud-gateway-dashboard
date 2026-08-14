<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const passwordDialog = ref(false)
const saving = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirm: ''
})

const isLoginPage = computed(() => route.name === 'login')

function openPasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirm = ''
  passwordDialog.value = true
}

async function submitPassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (passwordForm.newPassword.length < 8) {
    ElMessage.warning('新密码长度至少 8 位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    await auth.changePassword(passwordForm.oldPassword, passwordForm.newPassword)
    ElMessage.success('密码已修改')
    passwordDialog.value = false
  } finally {
    saving.value = false
  }
}

async function handleCommand(command: string) {
  if (command === 'password') {
    openPasswordDialog()
  } else if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    auth.logout()
    router.push({ name: 'login' })
  }
}
</script>

<template>
  <router-view v-if="isLoginPage" />
  <el-container v-else class="layout">
    <el-aside width="200px" style="background-color: #1f2d3d">
      <div class="logo">Gateway Dashboard</div>
      <el-menu
        router
        :default-active="route.path"
        background-color="#1f2d3d"
        text-color="#c0c4cc"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/routes">路由管理</el-menu-item>
        <el-menu-item index="/gateway">网关状态</el-menu-item>
        <el-menu-item index="/audit">操作日志</el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/permissions">权限配置</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div style="font-weight: 600">{{ route.meta.title || '' }}</div>
        <el-dropdown @command="handleCommand">
          <span class="user">
            {{ auth.user?.username }}（{{ auth.roleLabel }}）
            <el-icon style="vertical-align: middle"><arrow-down /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="passwordDialog" title="修改密码" width="420px">
    <el-form label-width="90px">
      <el-form-item label="原密码">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="passwordForm.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input v-model="passwordForm.confirm" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="passwordDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitPassword">确定</el-button>
    </template>
  </el-dialog>
</template>
