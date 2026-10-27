<template>
  <div class="layout-container">
    <div class="sidebar" :style="{ width: collapsed ? '64px' : '220px' }">
      <div class="logo" v-show="!collapsed">社团活动管理</div>
      <el-menu
        :default-active="activeMenu"
        :background-color="'var(--sidebar-bg)'"
        :text-color="'var(--sidebar-text)'"
        :active-text-color="'var(--primary-color)'"
        router
        :collapse="collapsed"
      >
        <template v-for="item in visibleMenus" :key="item.index">
          <el-menu-item :index="item.index">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>
              {{ item.title }}
              <el-badge
                v-if="item.badge && notificationStore.unreadCount > 0"
                :value="notificationStore.unreadCount"
                :max="99"
                style="margin-left: 8px"
              />
            </template>
          </el-menu-item>
        </template>
      </el-menu>
    </div>

    <div class="main-container">
      <div class="header">
        <div class="header-left">
          <el-icon :size="20" style="cursor: pointer" @click="toggleSidebar">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">
              {{ route.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-badge :value="notificationStore.unreadCount" :hidden="notificationStore.unreadCount === 0" :max="99">
            <el-icon :size="20" style="cursor: pointer" @click="$router.push('/notifications')">
              <Bell />
            </el-icon>
          </el-badge>
          <el-dropdown @command="handleCommand">
            <span class="user-info" style="cursor: pointer; display: flex; align-items: center; gap: 8px">
              <el-avatar :size="32">{{ userStore.userInfo?.realName?.charAt(0) || 'U' }}</el-avatar>
              <span>{{ userStore.userInfo?.realName || '用户' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div class="main-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore, useNotificationStore } from '@/store'
import { Role } from '@/constants/enums'
import {
  HomeFilled, OfficeBuilding, Calendar, Document, Box,
  Bell, DataAnalysis, Fold, Expand,
  Trophy, ChatDotRound, Collection
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
const collapsed = ref(false)
let pollingTimer = null

const activeMenu = computed(() => route.path)

const toggleSidebar = () => {
  collapsed.value = !collapsed.value
}

// 菜单配置：title / index(路由路径) / icon / 所需角色 / 是否显示通知角标
const menuConfig = [
  { index: '/dashboard', title: '首页', icon: HomeFilled, roles: [Role.STUDENT, Role.CLUB_PRESIDENT, Role.TEACHER, Role.ADMIN] },
  { index: '/clubs', title: '社团管理', icon: OfficeBuilding, roles: [Role.ADMIN, Role.TEACHER] },
  { index: '/activities', title: '活动管理', icon: Calendar, roles: [Role.ADMIN, Role.TEACHER, Role.CLUB_PRESIDENT] },
  { index: '/registrations', title: '我的报名', icon: Document, roles: [Role.STUDENT] },
  { index: '/resources', title: '资源管理', icon: Box, roles: [Role.ADMIN, Role.TEACHER, Role.CLUB_PRESIDENT] },
  { index: '/notifications', title: '通知中心', icon: Bell, roles: [Role.STUDENT, Role.CLUB_PRESIDENT, Role.TEACHER, Role.ADMIN], badge: true },
  { index: '/statistics', title: '数据统计', icon: DataAnalysis, roles: [Role.ADMIN, Role.TEACHER] },
  { index: '/points', title: '积分中心', icon: Trophy, roles: [Role.STUDENT, Role.CLUB_PRESIDENT, Role.TEACHER, Role.ADMIN] },
  { index: '/ai/chat', title: 'AI 助手', icon: ChatDotRound, roles: [Role.STUDENT, Role.CLUB_PRESIDENT, Role.TEACHER, Role.ADMIN] },
  { index: '/ai/knowledge', title: '知识库', icon: Collection, roles: [Role.ADMIN, Role.CLUB_PRESIDENT] }
]

// 根据当前用户角色动态过滤可见菜单
const visibleMenus = computed(() => {
  const userRoles = userStore.roles || []
  return menuConfig.filter(item => item.roles.some(r => userRoles.includes(r)))
})

const handleCommand = (command) => {
  if (command === 'logout') {
    userStore.logout()
    notificationStore.reset()
    router.push('/login')
  } else if (command === 'profile') {
    // 跳转到个人信息页
  }
}

onMounted(async () => {
  try {
    await userStore.fetchUserInfo()
  } catch {
    // 忽略
  }

  // 首次拉取未读数，然后每 30 秒轮询一次（替代 watch route 的逐次请求）
  notificationStore.fetchUnreadCount()
  pollingTimer = setInterval(() => {
    notificationStore.fetchUnreadCount()
  }, 30000)
})

onUnmounted(() => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
})
</script>

<style scoped>
.layout-container .main-container {
  --bg-color: #fff;
  background: #fff;
}

.layout-container .main-container .header {
  box-shadow: 0 1px 0 #edf2f7;
}

.layout-container .main-content :deep(.el-card),
.layout-container .main-content :deep(.stat-card),
.layout-container .main-content :deep(.filter-card),
.layout-container .main-content :deep(.table-card) {
  background: #fff;
  border-color: #edf2f7;
  border-radius: 8px;
  box-shadow: 0 0 0 1px rgba(227, 235, 245, 0.55),
    0 6px 22px rgba(51, 99, 164, 0.055);
}

.layout-container .sidebar {
  --sidebar-bg: #f0f3f5;
  --sidebar-text: #4e6079;
  --sidebar-icon: #6d87aa;
  --sidebar-hover: #e5ecf5;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  min-height: 0;
}

.layout-container .sidebar .logo {
  flex-shrink: 0;
  margin: 0 16px;
  color: #34445b;
  border-bottom: 1px solid #dce3eb;
  letter-spacing: 0.5px;
}

.sidebar :deep(.el-menu) {
  flex: 1;
  min-height: 0;
  width: 100%;
  padding: 20px 12px 24px;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #ccd5e1 transparent;
}

.sidebar :deep(.el-menu-item) {
  height: 46px;
  margin-bottom: 10px;
  padding: 0 16px !important;
  border-radius: 6px;
  color: var(--sidebar-text);
  font-size: 14px;
  font-weight: 500;
  line-height: 46px;
  transition: background-color 0.2s, color 0.2s, box-shadow 0.2s;
}

.sidebar :deep(.el-menu-item .el-icon) {
  width: 20px;
  margin-right: 12px;
  color: var(--sidebar-icon);
  font-size: 18px;
}

.sidebar :deep(.el-menu-item:hover),
.sidebar :deep(.el-menu-item:focus-visible) {
  background-color: var(--sidebar-hover);
  color: #345f9b;
}

.sidebar :deep(.el-menu-item:focus-visible) {
  outline: 2px solid #8fb7f5;
  outline-offset: -2px;
}

.sidebar :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #3478f6, #4189fa);
  color: #fff;
  box-shadow: 0 7px 18px rgba(52, 120, 246, 0.24);
}

.sidebar :deep(.el-menu-item.is-active .el-icon) {
  color: #fff;
}

.sidebar :deep(.el-badge) {
  display: inline-flex;
  align-items: center;
  line-height: 1;
  vertical-align: middle;
}

.sidebar :deep(.el-badge__content.is-fixed) {
  position: static;
  border: none;
  transform: none;
}

.sidebar :deep(.el-menu--collapse) {
  padding-right: 8px;
  padding-left: 8px;
}

.sidebar :deep(.el-menu--collapse .el-menu-item) {
  padding: 0 !important;
  justify-content: center;
}

.sidebar :deep(.el-menu--collapse .el-menu-item .el-icon) {
  margin-right: 0;
}

.sidebar :deep(.el-menu--collapse .el-menu-tooltip__trigger) {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 !important;
}

@media (prefers-reduced-motion: reduce) {
  .layout-container .sidebar,
  .sidebar :deep(.el-menu-item) {
    transition: none;
  }
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
}
</style>
