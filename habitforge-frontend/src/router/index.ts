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
    path: '/study/review',
    name: 'ReviewSession',
    component: () => import('@/views/study/ReviewSession.vue'),
    meta: { title: '闪卡复习' }
  },
  {
    path: '/study/flashcards/create',
    name: 'FlashcardCreate',
    component: () => import('@/views/study/FlashcardEdit.vue'),
    meta: { title: '新建闪卡' }
  },
  {
    path: '/study/notes',
    name: 'NoteList',
    component: () => import('@/views/study/NoteList.vue'),
    meta: { title: '笔记' }
  },
  {
    path: '/study/notes/create',
    name: 'NoteCreate',
    component: () => import('@/views/study/NoteEdit.vue'),
    meta: { title: '新建笔记' }
  },
  {
    path: '/study/notes/edit/:id',
    name: 'NoteEdit',
    component: () => import('@/views/study/NoteEdit.vue'),
    meta: { title: '编辑笔记' }
  },
  {
    path: '/study/notes/:id',
    name: 'NoteDetail',
    component: () => import('@/views/study/NoteDetail.vue'),
    meta: { title: '笔记详情' }
  },
  {
    path: '/study/questions',
    name: 'QuestionBank',
    component: () => import('@/views/study/QuestionBank.vue'),
    meta: { title: '题库' }
  },
  {
    path: '/study/questions/create',
    name: 'QuestionCreate',
    component: () => import('@/views/study/QuestionEdit.vue'),
    meta: { title: '新建题目' }
  },
  {
    path: '/study/questions/edit/:id',
    name: 'QuestionEdit',
    component: () => import('@/views/study/QuestionEdit.vue'),
    meta: { title: '编辑题目' }
  },
  {
    path: '/study/questions/:id',
    name: 'QuestionDetail',
    component: () => import('@/views/study/QuestionDetail.vue'),
    meta: { title: '题目详情' }
  },
  {
    path: '/study/wrongs',
    name: 'WrongBook',
    component: () => import('@/views/study/WrongBook.vue'),
    meta: { title: '错题本' }
  },
  {
    path: '/study/wrongs/practice',
    name: 'WrongPractice',
    component: () => import('@/views/study/WrongPractice.vue'),
    meta: { title: '错题练习' }
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
