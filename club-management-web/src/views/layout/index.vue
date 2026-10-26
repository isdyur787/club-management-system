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
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
}
</style>
