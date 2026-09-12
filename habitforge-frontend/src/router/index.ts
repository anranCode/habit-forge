import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { public: true, title: '注册' }
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/home/Home.vue'),
    meta: { title: '今日看板', tab: 0 }
  },
  {
    path: '/habits',
    name: 'HabitList',
    component: () => import('@/views/habits/HabitList.vue'),
    meta: { title: '习惯管理', tab: 1 }
  },
  {
    path: '/habits/create',
    name: 'HabitCreate',
    component: () => import('@/views/habits/HabitCreate.vue'),
    meta: { title: '创建新习惯' }
  },
  {
    path: '/habits/edit/:id',
    name: 'HabitEdit',
    component: () => import('@/views/habits/HabitEdit.vue'),
    meta: { title: '编辑习惯' }
  },
  {
    path: '/habits/:id',
    name: 'HabitDetail',
    component: () => import('@/views/habits/HabitDetail.vue'),
    meta: { title: '习惯详情' }
  },
  {
    path: '/record',
    name: 'Record',
    component: () => import('@/views/record/Record.vue'),
    meta: { title: '每日记录', tab: 2 }
  },
  {
    path: '/record/create',
    name: 'JournalCreate',
    component: () => import('@/views/record/JournalEdit.vue'),
    meta: { title: '写记录' }
  },
  {
    path: '/record/edit/:id',
    name: 'JournalEdit',
    component: () => import('@/views/record/JournalEdit.vue'),
    meta: { title: '编辑记录' }
  },
  {
    path: '/record/:id',
    name: 'JournalDetail',
    component: () => import('@/views/record/JournalDetail.vue'),
    meta: { title: '记录详情' }
  },
  {
    path: '/study',
    name: 'StudyHome',
    component: () => import('@/views/study/StudyHome.vue'),
    meta: { title: '学习中心' }
  },
  {
    path: '/study/subjects/create',
    name: 'SubjectCreate',
    component: () => import('@/views/study/SubjectEdit.vue'),
    meta: { title: '新建科目' }
  },
  {
    path: '/study/subjects/edit/:id',
    name: 'SubjectEdit',
    component: () => import('@/views/study/SubjectEdit.vue'),
    meta: { title: '编辑科目' }
  },
  {
    path: '/study/subjects/:id',
    name: 'SubjectDetail',
    component: () => import('@/views/study/SubjectDetail.vue'),
    meta: { title: '科目详情' }
  },
  {
    path: '/track',
    name: 'Track',
    component: () => import('@/views/track/Track.vue'),
    meta: { title: '追踪看板', tab: 3 }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/profile/Profile.vue'),
    meta: { title: '我的', tab: 4 }
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局守卫：未登录跳转登录页
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public && userStore.isLoggedIn && (to.path === '/login' || to.path === '/register')) {
    return { path: '/home' }
  }
  document.title = `${to.meta.title || 'HabitForge'} · HabitForge`
  return true
})

export default router
