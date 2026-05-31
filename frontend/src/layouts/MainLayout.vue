<template>
  <el-container class="main-layout">
    <el-header class="header">
      <div class="header-left">
        <div class="logo-wrapper">
          <div class="logo-icon">
            <el-icon size="22"><Box /></el-icon>
          </div>
          <span class="title">自助建模平台</span>
        </div>
        <nav class="main-nav">
          <button
            v-for="item in menuItems"
            :key="item.path"
            :class="['nav-item', { active: activeMenu === item.path }]"
            @click="handleNavClick(item.path)"
          >
            <el-icon :size="18"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </button>
        </nav>
      </div>
      <div class="header-right">
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-icon><User /></el-icon>
            {{ authStore.user?.nickname || authStore.user?.username || '用户' }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-main class="main-content">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { Box, User, ArrowDown, Document, DataAnalysis } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeMenu = computed(() => {
  if (route.path.startsWith('/metadata')) return '/metadata'
  if (route.path.startsWith('/models')) return '/models'
  return '/models'
})

const menuItems = [
  { path: '/models', label: '模型管理', icon: Document },
  { path: '/metadata', label: '元数据', icon: DataAnalysis }
]

onMounted(() => {
  authStore.fetchUserInfo()
})

const handleCommand = async (command: string) => {
  if (command === 'logout') {
    await authStore.logout()
    router.push('/login')
  }
}

const handleNavClick = (path: string) => {
  router.push(path)
}
</script>

<style scoped>
.main-layout {
  height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  padding: 0 24px;
  border-bottom: none;
}

:deep(.el-header) {
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 20px;
  border-right: 1px solid #e4e7ed;
}

.logo-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  letter-spacing: 0;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #606266;
  font-size: 14px;
  padding: 6px 14px;
  border-radius: 20px;
  background: #f5f7fa;
  transition: all 0.3s ease;
}

.user-info:hover {
  background: #e8ecf1;
  color: #303133;
}

.main-content {
  background: #f5f7fa;
  padding: 0;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* 主导航样式 */
.main-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 20px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px;
  height: 60px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: #606266;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.nav-item:hover {
  color: #667eea;
  background: #f5f7fa;
}

.nav-item.active {
  color: #667eea;
  border-bottom-color: #667eea;
}

/* 覆盖 Element Plus 下拉菜单 */
:deep(.el-dropdown-menu__item) {
  padding: 10px 20px;
}

:deep(.el-dropdown-menu__item:hover) {
  background: #f5f7fa;
  color: #667eea;
}

/* 直接覆盖 Element Plus el-main 的默认样式 */
:deep(.el-main) {
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}
</style>
