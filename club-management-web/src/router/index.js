import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/views/layout/index.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', roles: ['STUDENT', 'CLUB_PRESIDENT', 'TEACHER', 'ADMIN'] }
      },
      {
        path: 'clubs',
        name: 'Clubs',
        component: () => import('@/views/club/index.vue'),
        meta: { title: '社团管理', roles: ['ADMIN', 'TEACHER'] }
      },
      {
        path: 'activities',
        name: 'Activities',
        component: () => import('@/views/activity/index.vue'),
        meta: { title: '活动管理', roles: ['ADMIN', 'TEACHER', 'CLUB_PRESIDENT'] }
      },
      {
        path: 'activities/:id',
        name: 'ActivityDetail',
        component: () => import('@/views/activity/detail.vue'),
        meta: { title: '活动详情', roles: ['STUDENT', 'CLUB_PRESIDENT', 'TEACHER', 'ADMIN'] }
      },
      {
        path: 'registrations',
        name: 'Registrations',
        component: () => import('@/views/registration/index.vue'),
        meta: { title: '我的报名', roles: ['STUDENT'] }
      },
      {
        path: 'resources',
        name: 'Resources',
        component: () => import('@/views/resource/index.vue'),
        meta: { title: '资源管理', roles: ['ADMIN', 'TEACHER', 'CLUB_PRESIDENT'] }
      },
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('@/views/notification/index.vue'),
        meta: { title: '通知中心', roles: ['STUDENT', 'CLUB_PRESIDENT', 'TEACHER', 'ADMIN'] }
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/statistics/index.vue'),
        meta: { title: '数据统计', roles: ['ADMIN', 'TEACHER'] }
      },
      // === 打卡相关 ===
      {
        path: 'checkin/form/:activityId',
        name: 'CheckinForm',
        component: () => import('@/views/checkin/CheckinForm.vue'),
        meta: { title: '活动打卡', roles: ['STUDENT'] }
      },
      {
        path: 'checkin/manage/:activityId',
        name: 'CheckinManage',
        component: () => import('@/views/checkin/CheckinManage.vue'),
        meta: { title: '打卡管理', roles: ['ADMIN', 'TEACHER', 'CLUB_PRESIDENT'] }
      },
      // === 积分 ===
      {
        path: 'points',
        name: 'Points',
        component: () => import('@/views/points/PointsPage.vue'),
        meta: { title: '我的积分', roles: ['STUDENT', 'CLUB_PRESIDENT', 'TEACHER', 'ADMIN'] }
      },
      // === AI 功能 ===
      {
        path: 'ai/chat',
        name: 'AiChat',
        component: () => import('@/views/ai/ChatView.vue'),
        meta: { title: 'AI 知识助手', roles: ['STUDENT', 'CLUB_PRESIDENT', 'TEACHER', 'ADMIN'] }
      },
      {
        path: 'ai/report/:activityId',
        name: 'AiReport',
        component: () => import('@/views/ai/ReportView.vue'),
        meta: { title: '成果报告', roles: ['ADMIN', 'TEACHER', 'CLUB_PRESIDENT'] }
      },
      {
        path: 'ai/knowledge',
        name: 'AiKnowledge',
        component: () => import('@/views/ai/KnowledgeView.vue'),
        meta: { title: '知识库管理', roles: ['ADMIN', 'CLUB_PRESIDENT'] }
      },
      {
        path: 'ai/compilation/:activityId',
        name: 'CompilationView',
        component: () => import('@/views/checkin/CompilationView.vue'),
        meta: { title: '汇编文档', roles: ['ADMIN', 'CLUB_PRESIDENT'] }
      }
    ]
  },
  // 404 兜底路由
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 解析 JWT 的 exp 字段，判断是否过期
function isTokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.exp * 1000 < Date.now()
  } catch {
    return true
  }
}

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  // 1. 认证检查
  if (to.meta.requiresAuth !== false) {
    if (!userStore.token || isTokenExpired(userStore.token)) {
      userStore.logout()
      next('/login')
      return
    }
  }

  // 2. 角色权限检查（meta.roles 存在且非空时生效）
  const requiredRoles = to.meta.roles
  if (Array.isArray(requiredRoles) && requiredRoles.length > 0) {
    const userRoles = userStore.roles || []
    const hasPermission = requiredRoles.some(role => userRoles.includes(role))
    if (!hasPermission) {
      next('/dashboard')
      return
    }
  }

  next()
})

export default router
