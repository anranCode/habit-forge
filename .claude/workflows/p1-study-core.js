export const meta = {
  name: 'p1-study-core',
  description: 'P1 学习核心：脚手架消除共享文件冲突后，后端全量 + 前端三域并行，再集成与验证-修复循环',
  phases: [
    { title: 'Scaffold', detail: '共享文件一次写定：SQL/ErrorCode/AppConstant/路由桩/api+types/依赖' },
    { title: 'Implement', detail: '后端全量 ∥ 前端闪卡 ∥ 前端笔记 ∥ 前端题目错题' },
    { title: 'Integrate', detail: '科目详情入口 + 全量构建' },
    { title: 'Verify', detail: '构建/测试/契约/规格' },
    { title: 'Fix', detail: '按发现修复' },
  ],
}

const PLAN = 'C:\\Users\\ZHIXUAN_HU\\.claude\\plans\\readme-md-plan-1-ai-2-validated-pillow.md'
const ROOT = 'E:/study/code/habit-forge'

const CONV = `
## 项目约定
- 仓库根 ${ROOT}，分支 feature/study-ai-plan；计划文件（P1 节是本轮规格）：${PLAN}
- 只在被允许的目录/文件内改动；不要 git commit/push；不要运行会写 dist/ 或 target/ 的构建除明确允许
- 前端类型检查统一用：cd habitforge-frontend && npx vue-tsc --noEmit（不跑 npm run build，避免并发冲突；最终 build 由集成代理跑）
- 如被允许 git commit：提交信息末尾加一行 "Co-Authored-By: Claude Code <noreply@anthropic.com>"
`

const BACKEND_SPEC = `
## P1 后端共享规格（脚手架只写 SQL/ErrorCode/AppConstant，业务代码由后端代理写）
### ErrorCode.java 追加
FLASHCARD_NOT_FOUND(7004,"闪卡不存在"), REVIEW_RATING_INVALID(7005,"复习评分无效（1-4）"), NOTE_NOT_FOUND(7006,"笔记不存在"), QUESTION_NOT_FOUND(7007,"题目不存在"), WRONG_NOT_FOUND(7008,"错题记录不存在")
### AppConstant.java 追加
POINTS_PER_DAILY_REVIEW=10, REVIEW_REWARD_MIN_CARDS=5, WRONG_MASTER_STREAK=2, REVIEW_QUEUE_DEFAULT_LIMIT=50
### upgrade_2026-09_study.sql：替换「-- P1 表将追加于此」标记为 6 表 DDL（风格逐列对齐现有表：VARCHAR(36) PK DEFAULT (UUID())、FK CASCADE/SET NULL、时间列 DEFAULT CURRENT_TIMESTAMP [ON UPDATE]）并在 schema.sql 同步追加（表编号顺延）
- flashcards(user_id,subject_id,chapter_id NULL,front TEXT,back TEXT,ease_factor DECIMAL(4,2) DEFAULT 2.50,interval_days INT DEFAULT 0,repetition INT DEFAULT 0,lapses INT DEFAULT 0,due_date DATE NOT NULL,last_reviewed_at DATETIME NULL,status VARCHAR(20) DEFAULT 'ACTIVE', idx_user_due(user_id,due_date), idx_subject, idx_chapter)
- card_review_logs(card_id FK CASCADE,user_id FK CASCADE,rating TINYINT,interval_before,interval_after,ease_before DECIMAL(4,2),ease_after DECIMAL(4,2),reviewed_at, idx_card, idx_user_time(user_id,reviewed_at))
- notes(user_id,subject_id,chapter_id NULL,title VARCHAR(200),content MEDIUMTEXT, idx_subject, idx_chapter, idx_user_updated(user_id,updated_at))
- note_images(note_id FK CASCADE,object_key VARCHAR(255),original_name,content_type,file_size,width,height,sort_order, idx_note_id) —— 完全镜像 journal_images 列
- questions(user_id,subject_id,chapter_id NULL,question_type VARCHAR(20) DEFAULT 'SINGLE',stem TEXT,options TEXT NULL,answer VARCHAR(1000),analysis TEXT NULL,source_type VARCHAR(20) DEFAULT 'CUSTOM',source_detail VARCHAR(100),difficulty TINYINT NULL, idx_subject, idx_chapter, idx_user)
- wrong_questions(user_id,question_id FK CASCADE,wrong_count DEFAULT 1,correct_streak DEFAULT 0,mastered TINYINT(1) DEFAULT 0,last_wrong_at,last_practiced_at, UNIQUE uk_user_question(user_id,question_id), idx_user_mastered(user_id,mastered))
### 前端桩 api 模块与 types 的导出名（必须逐字一致，功能代理不得改动这三对文件）
types/flashcard.d.ts + api/modules/flashcard.ts + api/index.ts export：
  type ReviewRating=1|2|3|4; Flashcard; ReviewQueueResponse{cards,dueTotal,newTotal}; ReviewResultResponse{intervalDays,easeFactor,dueDate,reviewedToday,rewardPoints}; FlashcardStats{dueToday,reviewedToday,total,next7Days}; FlashcardCreatePayload{subjectId,chapterId?,front,back}; FlashcardUpdatePayload
  apiCreateFlashcard(data)->Flashcard POST /flashcards；apiReviewQueue(params?:{subjectId?:string,limit?:number})->ReviewQueueResponse GET /flashcards/queue；apiReviewFlashcard(id,rating:ReviewRating)->ReviewResultResponse POST /flashcards/{id}/review；apiFlashcardStats()->FlashcardStats GET /flashcards/stats；apiUpdateFlashcard(id,data)->Flashcard；apiDeleteFlashcard(id)
types/note.d.ts + api/modules/note.ts：
  Note{id,subjectId,chapterId|null,title,content,createdAt,updatedAt}; NoteSummary{id,subjectId,chapterId|null,title,excerpt,updatedAt}; NoteImage{id,objectKey,originalName,contentType,fileSize,width|null,height|null,sortOrder}; NoteCreatePayload{subjectId,chapterId?,title,content?}; NoteUpdatePayload{chapterId?,title?,content?}
  apiCreateNote,apiNotes(params?:{subjectId?,chapterId?,keyword?})→NoteSummary[],apiNote(id)→Note,apiUpdateNote,apiDeleteNote,apiUploadNoteImage(id,file)（FormData+multipart+60s 超时，仿 apiUploadImage）,apiNoteImages(id)→NoteImage[],apiDeleteNoteImage(noteId,imageId)
types/question.d.ts + api/modules/question.ts：
  QuestionType='SINGLE'|'MULTI'|'JUDGE'|'SHORT'; QuestionOption{key,text}; Question{...,options:QuestionOption[]|null,...}; PageResult<T>{records:T[],total:number,current:number,size:number}; WrongQuestion{questionId,question:Question,wrongCount,correctStreak,mastered:boolean,lastWrongAt,lastPracticedAt|null}; QuestionQuery{subjectId?,chapterId?,sourceType?,difficulty?,keyword?,page?,size?}
  apiCreateQuestion,apiQuestions(q:QuestionQuery)→PageResult<Question> GET /questions,apiQuestion(id),apiUpdateQuestion,apiDeleteQuestion,apiAddWrong(questionId) POST /wrong-questions {questionId},apiWrongList(params?:{subjectId?,limit?})→WrongQuestion[],apiPracticeWrong(questionId,correct:boolean)→WrongQuestion POST /wrong-questions/{questionId}/practice,apiSetWrongMastered(questionId,mastered:boolean)→WrongQuestion PATCH /wrong-questions/{questionId}/mastered
### 路由与视图桩（脚手架建桩，功能代理替换自己名下的桩文件，其余不碰）
/study/review→ReviewSession.vue(FE-A)；/study/flashcards/create→FlashcardEdit.vue(FE-A)；/study/notes/create、/study/notes/edit/:id→NoteEdit.vue(FE-B)；/study/notes/:id→NoteDetail.vue(FE-B)；/study/questions→QuestionBank.vue(FE-C)；/study/questions/create、/study/questions/edit/:id→QuestionEdit.vue(FE-C)；/study/questions/:id→QuestionDetail.vue(FE-C)；/study/wrongs→WrongBook.vue(FE-C)；/study/wrongs/practice→WrongPractice.vue(FE-C)。桩内容：defineOptions name 无需、<van-nav-bar left-arrow @click-left="router.back()" />+「建设中」占位，保证 vue-tsc 过。
`

phase('Scaffold')

const scaffold = await agent(`${CONV}
你是脚手架代理。先读计划文件 ${PLAN} 的 P1 节与 ${BACKEND_SPEC}。注意：上一轮脚手架已完成一半（提交 6716bf9），后端共享文件已写定，本轮只需补完前端部分：
1. 后端（只验证，不重写）：确认 ErrorCode 已有 7004-7008、AppConstant 已有复习常量、upgrade_2026-09_study.sql 与 schema.sql 已含 6 张 P1 表且两文件一致；确认 habitforge-frontend/package.json 已含 marked 与 dompurify。任何一项缺失才补齐，否则不动。
2. 前端（本轮主体）：创建 3 个 types + 3 个 api 模块文件（逐字按契约，写法仿 src/api/modules/journal.ts 的 request.get<never,T>）；api/index.ts 追加 3 行 export；router/index.ts 追加 11 条路由（懒加载、meta.title、无 tab）；创建 9 个桩 view 文件（views/study/ 下，仿现有页面最小骨架）。
3. 验证：cd habitforge-backend && mvn -q -DskipTests compile 通过；cd habitforge-frontend && npx vue-tsc --noEmit 通过。
4. 完成后 git add -A && git commit -m "chore(p1): 脚手架补完——前端路由桩/契约 api+types"（仅这一次提交）。
返回：文件清单 + 两项检查结果。`,
  { label: 'scaffold:p1', phase: 'Scaffold' })

if (!scaffold) throw new Error('scaffold failed')
log('脚手架完成，进入 4 路并行实施')

phase('Implement')

const IMPL_NOTE = '返回：新建/修改文件清单 + 验证命令结果关键输出。'

const [be, feA, feB, feC] = await parallel([
  () => agent(`${CONV}
你是 P1 后端业务代理。**允许新建 habitforge-backend 内 modules/study 的 P1 业务代码与单测；禁止改动脚手架已写定的共享文件（ErrorCode/AppConstant/SQL）与桩 api**（发现契约问题记入返回，不擅改）。
先精读计划 P1 节 + 范例：modules/study 现有 Subject/Chapter 分层、modules/journal 的图片上传全流程（JournalServiceImpl uploadImage/删除/getOwned）、journal 上传 Controller 写法、CheckinServiceImpl 限流 fail-open、JournalServiceImplTest 单测风格。
实施四块：
1. **闪卡**：Flashcard/CardReviewLog entity+mapper；Sm2Scheduler 静态纯函数严格按计划评分表（含 EF 1.30/3.00 边界、rating=1 当日重现 due=today）；FlashcardService（创建校验 subject 归属 7001→用 SubjectService 复用其归属校验或按 subject 归属查 chapter 校验 7002；queue 查询 due<=today+status ACTIVE 按 due_date,created_at LIMIT，dueTotal 计数、newTotal=repetition=0 且 due<=today；review：rating 白名单 7005、loadOwned 7004、更新卡+写日志 before/after 快照、当日累计≥REVIEW_REWARD_MIN_CARDS 且 Redis setIfAbsent 标记 study:review:reward:{userId}:{date} TTL 48h 成功→addPoints(+10) rewardPoints=10 否则 0；stats）；Controller 按契约 URL。
2. **笔记**：Note/NoteImage entity+mapper；CRUD+摘要（excerpt=content 前 100 字去 markdown 符号即可简单截断）+keyword（title/content LIKE）；图片上传**完全照搬 journal 流程**（类型/大小/ImageIO 宽高/StorageService.putObject 先传后落库失败回收/deleteObjectQuietly/UPLOAD_RATE_LIMIT 限流 fail-open），objectKey=habitforge/note/{yyyy/MM}/{uuid}.{ext}。
3. **题库**：Question entity+mapper（options TEXT 存 JSON 字符串，DTO 出入用 List<QuestionOption>，hutool JSONUtil 序列化/反序列化+写入校验）；CRUD+分页（PaginationInnerInterceptor，page 默认1 size 默认20，条件 subjectId/chapterId/sourceType/difficulty/keyword(stem LIKE)，按 createdAt desc）。
4. **错题**：WrongQuestion entity+mapper；POST 按计划 upsert（uk 冲突捕获 DuplicateKeyException→查询后累加 wrong_count+1/correct_streak=0/mastered=0/last_wrong_at=now）；GET list（mastered=0，wrong_count desc,last_wrong_at asc，附 question 详情——两次 IN 查询组装防 N+1）；practice（loadOwned→correct?streak+1 判断≥WRONG_MASTER_STREAK 置 mastered:wrong_count+1 且 streak=0，更新 last_practiced_at）；PATCH mastered 手动。
5. **Overview 真实化**：StudyOverviewServiceImpl 填 dueCards（count due<=today,ACTIVE）、wrongsTotal（mastered=0）、reviewedToday（card_review_logs 今日 count）；SubjectResponse 的 dueCards/wrongCount 同样接真实值（一次 GROUP BY 聚合防 N+1）。
单测：Sm2SchedulerTest 全矩阵（4 档×新卡/rep1/rep2/成熟卡+EF 边界）、FlashcardServiceImplTest（队列/评分入队重现/奖励只发一次）、WrongQuestionServiceImplTest（upsert 累加/连对2次摘除/答错重置）、NoteServiceImplTest（图片流程 mock StorageService 失败回收、摘要截断）。
验证：cd habitforge-backend && mvn -q test 全绿（含存量）。${IMPL_NOTE}`,
    { label: 'impl:backend-p1', phase: 'Implement' }),

  () => agent(`${CONV}
你是 P1 前端代理 FE-A（闪卡+复习会话）。**只允许**：替换 views/study/ReviewSession.vue、FlashcardEdit.vue 桩，新建 components/study/{FlashcardFlip,ReviewProgress}.vue。禁止改 api/types/router/Home/Profile/SubjectDetail（契约 api 用脚手架写定的 flashcard.ts）。
先精读计划 P1 节 + 范例：views/record/JournalEdit.vue（表单/弹窗风格）、components/record/ReflectionEditor.vue（popup 表单模式）、Home.vue 的卡片样式 class（.card/.flex-between 等全局 class 直接复用）。
实现：
1. ReviewSession.vue：进入调 apiReviewQueue()（顶部 van-tabs 可切换科目：科目列表来自 apiSubjects）；ReviewProgress 顶栏（进度 x/y、剩余、今日已复习）；FlashcardFlip 翻卡（front 显示→点击翻 back→4 档评分按钮「忘记/模糊/记得/轻松」= rating 1-4，CSS 3D transform，主题橙）；评完取 ReviewResultResponse 更新统计 toast rewardPoints>0 时「复习达标 +10 积分」；队列评完显示完成态（今日到期 N 张已清空）。
2. FlashcardEdit.vue：van-form（科目必选 van-popup 选择器、章节可选级联于科目、front/back van-field textarea）→ apiCreateFlashcard，成功 toast 返回；支持 ?subjectId=&chapterId= 预选。
3. 从 QuestionDetail/笔记等跳来的「新建闪卡」入口不在此做（集成代理负责科目页入口）。
验证：npx vue-tsc --noEmit 全过。${IMPL_NOTE}`,
    { label: 'impl:fe-flashcard', phase: 'Implement' }),

  () => agent(`${CONV}
你是 P1 前端代理 FE-B（笔记+Markdown 体系）。**只允许**：替换 views/study/{NoteEdit,NoteDetail}.vue 桩，新建 src/utils/markdown.ts、components/study/{MarkdownEditor,MarkdownPreview}.vue。禁止改 api/types/router/其他页面（用脚手架 note.ts 契约 api）。
先精读计划 P1 节 Markdown 方案 + 范例：JournalEdit.vue（van-uploader 即选即传/保存后补传两种模式的处理）、assets/styles/variables.scss 可用变量。
实现：
1. utils/markdown.ts：renderMarkdown(md) = marked.parse + DOMPurify.sanitize 返回 HTML 字符串；配置 marked breaks:true gfm:true。
2. MarkdownPreview.vue：props content，v-html=renderMarkdown(content)，内部 scss 类 md-body（标题/列表/引用/代码块/图片 max-width:100% 排版，强调色用 $主橙 #ff7a00）。
3. MarkdownEditor.vue：v-model；顶部轻量工具栏按钮（H2/H3/加粗/斜体/无序列表/引用/代码/图片）对 textarea 光标处插入语法（document.execCommand('insertText') 或 selectionStart 拼接，保持光标可读性）；图片按钮→隐藏 van-uploader→apiUploadNoteImage 已有 id 时即传，新建模式暂存本地（仿 JournalEdit 的 pending 队列）→保存后补传并把 ![](/images/{objectKey}) 追加正文；textarea 全屏编辑（van-nav-bar fixed）+ van-tabs 编辑/预览切换；编辑态 localStorage 草稿（key 含 subjectId，保存成功清除，进入时询问恢复）。
4. NoteEdit.vue：标题 field+科目必选/章节可选+MarkdownEditor；编辑模式 apiNote 拉详情；保存 apiCreateNote/apiUpdateNote+补传图片。NoteDetail.vue：van-nav-bar+MarkdownPreview 渲染正文+更新时间+编辑入口+删除（showConfirmDialog，删除后 best-effort 由后端清 MinIO）。
验证：npx vue-tsc --noEmit 全过。${IMPL_NOTE}`,
    { label: 'impl:fe-note', phase: 'Implement' }),

  () => agent(`${CONV}
你是 P1 前端代理 FE-C（题库+错题本）。**只允许**：替换 views/study/{QuestionBank,QuestionEdit,QuestionDetail,WrongBook,WrongPractice}.vue 桩，新建 components/study/{QuestionCard,WrongQuestionItem}.vue。禁止改 api/types/router/其他页面（用脚手架 question.ts 契约 api）。
先精读计划 P1 节错题交互 + 范例：HabitList.vue（van-swipe-cell+分页列表）、Record.vue（月份翻页式列表）。
实现：
1. QuestionBank.vue：筛选条（科目 chips+题型 van-dropdown-menu+来源）+ apiQuestions 分页（van-list 触底加载，page/size→records/total）；题目卡片点击跳详情；右下悬浮「录题」→QuestionEdit。
2. QuestionEdit.vue：题型 radio（SINGLE/MULTI/JUDGE/SHORT）；SINGLE/MULTI 动态选项编辑器（key 自动 A/B/C…，text field，可删可加≤10）；JUDGE 显示对/错答案选择；SHORT 答案 textarea；stem/answer/analysis（analysis 用 textarea 即可，渲染走 MarkdownPreview）；sourceType radio+sourceDetail field；difficulty van-stepper 1-5。
3. QuestionDetail.vue：MarkdownPreview 渲染 stem；选项列表；「显示答案」折叠→答案+解析；操作行：「我答错了」apiAddWrong toast 入本、「加入/移出错题本」状态、编辑/删除（swipe 或按钮）。
4. WrongBook.vue：apiWrongList 列表（WrongQuestionItem：错题条显示错 N 次徽章+题干摘要+最近错误时间，点击展开或跳 QuestionDetail；左滑恢复/删除）；顶部「开始重练」→WrongPractice。
5. WrongPractice.vue：apiWrongList(limit=20) 取队列；复用 QuestionCard 式流程：题干+选项→回想→「显示答案与解析」→自报「答对了/答错了」（apiPracticeWrong）→toast 摘除/重置 streak→下一题；队列完成显示战报（本次练了 N 道、摘除 M 道）。
验证：npx vue-tsc --noEmit 全过。${IMPL_NOTE}`,
    { label: 'impl:fe-question', phase: 'Implement' }),
])

log(`实施完成（后端 ${be ? 'ok' : 'FAIL'} / A ${feA ? 'ok' : 'FAIL'} / B ${feB ? 'ok' : 'FAIL'} / C ${feC ? 'ok' : 'FAIL'}），进入集成`)

phase('Integrate')

const integration = await agent(`${CONV}
你是集成代理。P1 四路实施已完成，你做收尾并跑全量构建：
1. views/study/SubjectDetail.vue：在章节树下方加「学习工具」入口区（4 个 van-cell-group inset cell：🃏 闪卡复习→/study/review?subjectId=xx（显示到期数，来自 apiSubjectDetail 的 dueCards）、📝 笔记→跳笔记列表（若无列表页则新建最小 NoteList.vue：apiNotes({subjectId}) 列表+新建入口）、❓ 题库→/study/questions?subjectId=xx、📕 错题本→/study/wrongs?subjectId=xx）；SubjectDetail 加载后角标用 overview 或 subject.dueCards/wrongCount。
2. StudyHome.vue：把 P0 恒 0 的汇总条接通真实数据（apiStudyOverview），并加「今日待复习」快捷入口卡→/study/review。
3. 检查后端四域 Controller 的 URL/方法与脚手架 api 模块完全一致（GET /flashcards/queue、POST /wrong-questions/{questionId}/practice 等），不一致处**改后端**对齐契约；检查脚手架 types 与后端 Response DTO 字段逐一对齐（camelCase、可空性、PageResult 结构 records/total/current/size、ReviewResultResponse 含 rewardPoints、WrongQuestion 含 question 嵌套），不一致处改后端 DTO 或记 finding。
4. 后端实现代理报告的契约问题（如下）逐条处理：
${[be, feA, feB, feC].filter(Boolean).map((r) => '- ' + String(r).slice(0, 400).replace(/\n/g, ' ')).join('\n')}
5. 验证：cd habitforge-backend && mvn -q test 全绿；cd habitforge-frontend && npm run build 通过。失败修到过。
返回：改动清单+两项构建结果。`,
  { label: 'integrate:p1', phase: 'Integrate' })

phase('Verify')

const VERIFY_SCHEMA = {
  type: 'object',
  properties: {
    passed: { type: 'boolean' },
    backendBuild: { type: 'string' },
    frontendBuild: { type: 'string' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          track: { type: 'string', enum: ['backend', 'frontend', 'contract', 'spec'] },
          file: { type: 'string' },
          issue: { type: 'string' },
          suggestedFix: { type: 'string' },
        },
        required: ['track', 'issue'],
      },
    },
  },
  required: ['passed', 'findings'],
}

const verifyPrompt = (round) => `${CONV}
你是 P1 验证代理（第 ${round} 轮），**只读+跑构建，不改文件**。
1. cd habitforge-backend && mvn -q test；cd habitforge-frontend && npm run build。记录结果。
2. 契约：后端 4 域 Response/Request DTO 字段名与 types/{flashcard,note,question}.d.ts 逐字段比对；Controller URL/方法/参数与 api/modules 三文件比对（track=contract）。
3. 规格（对照计划 P1 节逐条核，track=spec）：SM-2 评分表与计划矩阵逐项一致（读 Sm2Scheduler 源码+Sm2SchedulerTest）；rating=1 当日重现；复习奖励≥5 张且当日只发一次（Redis 标记）；笔记图片全流程镜像 journal（类型/大小/限流/先传后落库失败回收/删除清 MinIO）；questions.options JSON 字符串写入校验；错题 upsert 语义、连对 2 次摘除、答错重置 streak、重练排序 wrong_count desc+last_wrong_at asc；overview 三字段真实聚合无 N+1；schema.sql 与 upgrade 脚本 6 表一致；错题交互自报对错（无自动判分）。
4. 质量抽查：loadOwned 归属校验覆盖所有新端点、@Transactional、LambdaQueryWrapper、前端 v-html 是否只出现在 DOMPurify 消毒后、路由无 tab meta、桩文件是否全部被替换（grep 建设中/TODO）。${round > 1 ? '上一轮 findings 是否已真正修复，未修记 finding。' : ''}
passed=true 仅当两构建全绿且无 contract/spec finding（低危 quality finding 不阻塞）。`

let verdict = await agent(verifyPrompt(1), { label: 'verify:p1-r1', phase: 'Verify', schema: VERIFY_SCHEMA })

let round = 1
while (verdict && !verdict.passed && round < 3) {
  round++
  phase('Fix')
  const groups = { backend: [], frontend: [] }
  for (const f of verdict.findings) {
    const isFE = /habitforge-frontend|src\/(views|components|api|types|utils)/.test(f.file || '') || f.track === 'frontend'
    groups[isFE && f.track !== 'contract' && f.track !== 'spec' ? 'frontend' : (isFE ? 'frontend' : 'backend')].push(f)
  }
  const thunks = Object.entries(groups).filter(([, fs]) => fs.length).map(([track, fs]) => () =>
    agent(`${CONV}
你是 ${track} 修复代理，只改 habitforge-${track}（涉及契约/spec 的双向问题：后端 DTO 为准改前端 types，反之亦然，最小改动）。逐条修复：
${fs.map((f, i) => `${i + 1}. [${f.track}] ${f.file || ''}: ${f.issue}${f.suggestedFix ? ' — 建议: ' + f.suggestedFix : ''}`).join('\n')}
验证：${track === 'backend' ? 'cd habitforge-backend && mvn -q test 全绿' : 'cd habitforge-frontend && npx vue-tsc --noEmit 全过'}。返回逐条处理说明。`,
      { label: `fix:${track}-r${round}`, phase: 'Fix' }))
  await parallel(thunks)
  verdict = await agent(verifyPrompt(round), { label: `verify:p1-r${round}`, phase: 'Verify', schema: VERIFY_SCHEMA })
}

return {
  scaffold: !!scaffold,
  implemented: { backend: !!be, feFlashcard: !!feA, feNote: !!feB, feQuestion: !!feC, integrate: !!integration },
  rounds: round,
  passed: verdict ? verdict.passed : false,
  backendBuild: verdict?.backendBuild || '',
  frontendBuild: verdict?.frontendBuild || '',
  remainingFindings: verdict ? verdict.findings : [{ track: 'backend', issue: 'verify agent died' }],
}