import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { isDesktopNow } from '@/composables/useDesktop'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    // fullBleed：整屏页面。App.vue 据此不挂导航，main.scss 据此解掉内容区限宽
    // （登录页自带满屏渐变背景，被 540px 内容列裁一刀就成了斜切一半的怪图）
    meta: { public: true, title: '登录', fullBleed: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { public: true, title: '注册', fullBleed: true }
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
    meta: { title: '创建新习惯', backTo: '/habits' }
  },
  {
    path: '/habits/edit/:id',
    name: 'HabitEdit',
    component: () => import('@/views/habits/HabitEdit.vue'),
    meta: { title: '编辑习惯', backTo: '/habits' }
  },
  {
    path: '/habits/:id',
    name: 'HabitDetail',
    component: () => import('@/views/habits/HabitDetail.vue'),
    meta: { title: '习惯详情', backTo: '/habits' }
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
    meta: { title: '写记录', backTo: '/record' }
  },
  {
    path: '/record/edit/:id',
    name: 'JournalEdit',
    component: () => import('@/views/record/JournalEdit.vue'),
    meta: { title: '编辑记录', backTo: '/record' }
  },
  {
    path: '/record/:id',
    name: 'JournalDetail',
    component: () => import('@/views/record/JournalDetail.vue'),
    meta: { title: '记录详情', backTo: '/record' }
  },
  {
    path: '/study',
    name: 'StudyHome',
    component: () => import('@/views/study/StudyHome.vue'),
    meta: { title: '学习中心', backTo: '/home' }
  },
  {
    path: '/study/subjects/create',
    name: 'SubjectCreate',
    component: () => import('@/views/study/SubjectEdit.vue'),
    meta: { title: '新建科目', backTo: '/study' }
  },
  {
    path: '/study/subjects/edit/:id',
    name: 'SubjectEdit',
    component: () => import('@/views/study/SubjectEdit.vue'),
    meta: { title: '编辑科目', backTo: '/study' }
  },
  {
    path: '/study/subjects/:id',
    name: 'SubjectDetail',
    component: () => import('@/views/study/SubjectDetail.vue'),
    meta: { title: '科目详情', backTo: '/study' }
  },
  {
    path: '/study/review',
    name: 'ReviewSession',
    component: () => import('@/views/study/ReviewSession.vue'),
    meta: { title: '闪卡复习', backTo: '/study' }
  },
  {
    path: '/study/flashcards/create',
    name: 'FlashcardCreate',
    component: () => import('@/views/study/FlashcardEdit.vue'),
    meta: { title: '新建闪卡', backTo: '/study' }
  },
  {
    path: '/study/notes',
    name: 'NoteList',
    component: () => import('@/views/study/NoteList.vue'),
    meta: { title: '笔记', backTo: '/study' }
  },
  {
    path: '/study/notes/create',
    name: 'NoteCreate',
    component: () => import('@/views/study/NoteEdit.vue'),
    meta: { title: '新建笔记', backTo: '/study/notes' }
  },
  {
    path: '/study/notes/edit/:id',
    name: 'NoteEdit',
    component: () => import('@/views/study/NoteEdit.vue'),
    meta: { title: '编辑笔记', backTo: '/study/notes' }
  },
  {
    path: '/study/notes/:id',
    name: 'NoteDetail',
    component: () => import('@/views/study/NoteDetail.vue'),
    meta: { title: '笔记详情', backTo: '/study/notes' }
  },
  {
    path: '/study/questions',
    name: 'QuestionBank',
    component: () => import('@/views/study/QuestionBank.vue'),
    meta: { title: '题库', backTo: '/study' }
  },
  {
    path: '/study/questions/create',
    name: 'QuestionCreate',
    component: () => import('@/views/study/QuestionEdit.vue'),
    meta: { title: '新建题目', backTo: '/study/questions' }
  },
  {
    path: '/study/questions/edit/:id',
    name: 'QuestionEdit',
    component: () => import('@/views/study/QuestionEdit.vue'),
    meta: { title: '编辑题目', backTo: '/study/questions' }
  },
  {
    path: '/study/questions/:id',
    name: 'QuestionDetail',
    component: () => import('@/views/study/QuestionDetail.vue'),
    meta: { title: '题目详情', backTo: '/study/questions' }
  },
  {
    path: '/study/wrongs',
    name: 'WrongBook',
    component: () => import('@/views/study/WrongBook.vue'),
    meta: { title: '错题本', backTo: '/study' }
  },
  {
    path: '/study/wrongs/practice',
    name: 'WrongPractice',
    component: () => import('@/views/study/WrongPractice.vue'),
    meta: { title: '错题练习', backTo: '/study/wrongs' }
  },
  {
    path: '/plan',
    name: 'PlanToday',
    component: () => import('@/views/plan/PlanToday.vue'),
    meta: { title: '今日安排', backTo: '/home' }
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
  routes,
  /**
   * 切页后的滚动位置。
   *
   * 不定义 scrollBehavior 时 vue-router 什么都不做（handleScroll 直接 return），
   * 浏览器又不会因为 pushState 重置滚动条 —— 于是"在今日页翻到一半，点侧栏进习惯页"
   * 会停在上一个页面的滚动偏移上；新页面比那个偏移短时直接被夹到底部，看起来像坏了。
   * 移动端有同样的症状，但本轮承诺"移动端逐像素/逐行为不变"，所以只在桌面端生效。
   *
   * 返回 false = 不滚动，与"没定义 scrollBehavior"完全等价
   * （vue-router 内部是 `position && scrollToPosition(position)`），
   * 因此移动端这条分支不会引入任何差异。返回 savedPosition 则让浏览器后退能回到原位。
   */
  scrollBehavior(_to, _from, savedPosition) {
    if (!isDesktopNow()) return false
    return savedPosition || { top: 0 }
  }
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
