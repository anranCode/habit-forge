export const meta = {
  name: 'p2-ai-plan',
  description: 'P2 AI 今日安排：脚手架写定共享文件（依赖/SQL/契约）后，后端 modules/ai 全量 ∥ 前端计划页，再集成与验证-修复循环',
  phases: [
    { title: 'Scaffold', detail: '共享文件一次写定：pom/yml/compose/SQL/ErrorCode/路由桩/plan 契约 api+types' },
    { title: 'Implement', detail: '后端 modules/ai 全量 ∥ 前端计划域' },
    { title: 'Integrate', detail: '契约交叉核对 + Home 接线复核 + 全量构建' },
    { title: 'Verify', detail: '构建/测试/契约/规格双轨验证' },
    { title: 'Fix', detail: '按发现修复' },
  ],
}

const PLAN = 'C:\\Users\\ZHIXUAN_HU\\.claude\\plans\\readme-md-plan-1-ai-2-validated-pillow.md'
const ROOT = 'E:/study/code/habit-forge'

const CONV = `
## 项目约定
- 仓库根 ${ROOT}，分支 feature/study-ai-plan；计划文件（P2 节是本轮规格，含 prompt 设计/护栏三件套/状态机/联动打卡细节，务必通读）：${PLAN}
- P0/P1 已交付（提交 346c271/f5a520d），Boot 已升 3.5.16（2289655）；学习模块代码在 modules/study，写法全部可参照
- 只在被允许的目录/文件内改动；除明确允许的提交外不要 git commit/push；不要运行会写 dist/ 或 target/ 的构建除明确允许
- 前端类型检查统一用：cd habitforge-frontend && npx vue-tsc --noEmit（不跑 npm run build，避免并发冲突；最终 build 由集成代理跑）
- 凭证红线：AI token 绝不写入任何代码/配置/脚本/提交，只经环境变量 AI_API_KEY；单测一律 mock ChatModel，不打真实端点
- 如被允许 git commit：提交信息末尾加一行 "Co-Authored-By: Claude Code <noreply@anthropic.com>"
`

const SHARED_SPEC = `
## P2 共享规格（脚手架一次性写定，业务代理不得改动这些文件）
### pom.xml（habitforge-backend）
properties 加 <spring-ai.version>1.1.8</spring-ai.version>；dependencyManagement 导入 org.springframework.ai:spring-ai-bom:\${spring-ai.version}（type=pom, scope=import）；dependencies 加 org.springframework.ai:spring-ai-starter-model-anthropic（无版本号，BOM 管理）
### application.yml 追加（只放非敏感默认；base-url/model/api-key 均可环境变量覆盖）
spring.ai.anthropic.base-url: \${AI_BASE_URL:https://api.deepseek.com/anthropic}；spring.ai.anthropic.api-key: \${AI_API_KEY:}；spring.ai.anthropic.chat.options.model: \${AI_MODEL:deepseek-v4-flash}、temperature: 0.5、max-tokens: 4096、**thinking.type: disabled（硬约束：DeepSeek v4 flash 默认输出 thinking 块，而 spring-ai 1.1.8 的 AnthropicChatModel 为每个 content 块各生成一个 Generation、getResult() 只取第 0 个，thinking 块排在 text 之前 → 不关思考则正文取到思考文本、每次生成必然误报 6005；且思考 token 计入 max_tokens，实测重上下文能吃尽额度导致 JSON 截断）**；spring.ai.retry.max-attempts: 2（配合 spring.http.client read-timeout 45s / connect-timeout 5s）；顶层 app.ai.{enabled:\${AI_ENABLED:true}, daily-generate-limit:5, journal-days:3, journal-chars-per-day:600, reflection-limit:10, reflection-chars:200, min-blocks:3, max-blocks:12}。**prod profile 的 api-key 必须写 \${AI_API_KEY:}（带空默认值）**——写成 \${AI_API_KEY}（无默认值）时 Boot 3.5 的 PropertySourcesPlaceholdersResolver 是 ignoreUnresolvablePlaceholders=true，未设置该变量会原样绑成字符串 "\${AI_API_KEY}" 这个**非空**值，任何「配没配」的判断都拦不住，请求会带缺失的鉴权头发出去撞 401（表现为 6004）；留空默认值 + PlanAiClient#credentialsMissing 才能把「没配」明确翻译成 6001。供应商已于 2026-09-13 由阿里云百炼 TokenPlan 切到 DeepSeek（TokenPlan 周配额耗尽），两者配置形状一致。若 spring-ai 1.1.8 实际属性名与此有出入（base-url/重试键等），以依赖 jar 内 spring-configuration-metadata.json 为准并在返回中列出偏差。
### docker-compose.yml backend environment 追加 AI_API_KEY="\${AI_API_KEY:-}"（**引号不能省**：不加引号时插值成空值后 YAML 解析为 null，compose 对 null 的处理是"从宿主机环境取同名变量"，行为不确定）、AI_ENABLED=\${AI_ENABLED:-true}、AI_BASE_URL 与 AI_MODEL 也必须真的转发（只写 .env 不转发会**静默无效**）；另补 SPRING_DATA_REDIS_PASSWORD 转发（对齐现有 env 风格）
### ErrorCode.java 追加（6xxx）
AI_NOT_CONFIGURED(6001,"AI 服务未启用"), AI_GENERATE_LIMITED(6002,"今日生成次数已达上限"), AI_GENERATING(6003,"AI 正在生成中，请稍候"), AI_SERVICE_ERROR(6004,"AI 服务调用失败"), AI_RESPONSE_INVALID(6005,"AI 返回内容无法解析"), AI_FREE_SLOT_REQUIRED(6006,"请先设置今日空闲时段"), PLAN_NOT_FOUND(6011,"今日计划不存在"), PLAN_BLOCK_NOT_FOUND(6012,"计划块不存在"), PLAN_BLOCK_STATUS_INVALID(6013,"计划块状态不允许该操作"), FREE_SLOT_INVALID(6014,"空闲时段不合法")
### RedisUtil 追加方法（风格对齐现有）：tryLock(key,value,timeoutSeconds)（setIfAbsent）、unlock(key)（delete）、release(key)（decrement 限流退还用）
### DDL：新建 db/upgrade_2026-10_ai_plan.sql（头部含回滚 DROP 注释，沿 upgrade_2026-09_study.sql 风格），schema.sql 追加同 4 表（表编号顺延；执行顺序依赖 study 脚本已建 subjects/chapters）
- daily_plans：user_id FK CASCADE、plan_date DATE NOT NULL、gen_count INT DEFAULT 0、last_model VARCHAR(100)、UNIQUE uk_user_date(user_id,plan_date)、时间列 DEFAULT CURRENT_TIMESTAMP [ON UPDATE]
- plan_blocks：plan_id FK CASCADE、habit_id/subject_id/chapter_id 可空 FK SET NULL、block_type VARCHAR(20)（HABIT/STUDY/REST/OTHER）、title VARCHAR(100)、start_time TIME、end_time TIME、sort_order INT、status VARCHAR(20)（PROPOSED/ADOPTED/DONE/SKIPPED）、source VARCHAR(10)（AI/MANUAL）、completed_at DATETIME NULL、idx_plan(plan_id)、idx_habit(habit_id)
- plan_free_slots：plan_id FK CASCADE、start_time TIME、end_time TIME、label VARCHAR(50)、sort_order INT、idx_plan(plan_id)
- plan_generations：user_id FK CASCADE、plan_id 可空 FK SET NULL、model VARCHAR(100)、prompt_tokens INT、completion_tokens INT、total_tokens INT、success TINYINT(1)、error VARCHAR(500)、raw_output MEDIUMTEXT、idx_user_time(user_id,created_at)
### 前端契约（逐字建 types/plan.d.ts + api/modules/plan.ts + api/index.ts 追加 export，写法仿 P1 三对文件）
types：BlockType='HABIT'|'STUDY'|'REST'|'OTHER'；BlockStatus='PROPOSED'|'ADOPTED'|'DONE'|'SKIPPED'；FreeSlot{id?,startTime,endTime,label,sortOrder?}；PlanBlock{id,planId,blockType,title,startTime,endTime,sortOrder,status,source,habitId|null,habitName|null,habitCheckedToday,subjectId|null,subjectName|null,chapterId|null,completedAt|null}；DailyPlan{id,planDate,genCount,lastModel,blocks:PlanBlock[],freeSlots:FreeSlot[]}；PlanUsage{used,remaining,todayTokens}；FreeSlotSavePayload{date?,slots:{startTime,endTime,label}[]}；BlockCreatePayload{date,blockType,title,startTime,endTime,habitId?,subjectId?,chapterId?}；BlockUpdatePayload（同去 date）
api：apiGeneratePlan(date?:string)→DailyPlan POST /plans/generate（该单请求 timeout:120000，仿 apiUploadImage 写法）；apiPlanToday()→DailyPlan|null GET /plans/today；apiPlanByDate(date)→DailyPlan GET /plans/{date}；apiSaveFreeSlots(payload)→FreeSlot[] PUT /plans/free-slots；apiAdoptPlan(date?:string)→number POST /plans/adopt；apiCreateBlock(payload)→PlanBlock POST /plans/blocks；apiUpdateBlock(id,payload)→PlanBlock PUT /plans/blocks/{id}；apiDeleteBlock(id) DELETE；apiCompleteBlock(id,checkinHabit:boolean)→PlanBlock POST /plans/blocks/{id}/complete body {checkinHabit}；apiSkipBlock(id)→PlanBlock POST /plans/blocks/{id}/skip；apiReopenBlock(id)→PlanBlock POST /plans/blocks/{id}/reopen；apiPlanUsage()→PlanUsage GET /plans/usage
### 路由与桩：/plan→views/plan/PlanToday.vue（meta.title「今日安排」、无 tab、不进 keep-alive include；桩=nav-bar+「建设中」占位，保证 vue-tsc 过）
`

phase('Scaffold')

const scaffold = await agent(`${CONV}
你是脚手架代理。先读计划文件 ${PLAN} 的 P2 节与 ${SHARED_SPEC}，严格按 SHARED_SPEC 完成共享文件一次性写定（pom/yml 已是 Boot 3.5.16 现状，改动为「追加」，不破坏既有键）。
完成后验证：cd habitforge-backend && mvn -q -DskipTests compile 通过（首次下载 spring-ai 依赖，耐心等待）；cd habitforge-frontend && npx vue-tsc --noEmit 通过。
最后 git add -A && git commit -m "chore(p2): 脚手架——Spring AI 依赖/yml/compose/4 表 SQL/ErrorCode/plan 契约 api+types/路由桩"（仅这一次提交）。
返回：文件清单 + 两项检查结果 + 编译期发现（spring-ai 1.1.8 属性名与规格如有出入，以实际 metadata 为准并列出偏差）。`,
  { label: 'scaffold:p2', phase: 'Scaffold' })

if (!scaffold) throw new Error('scaffold failed')
log('脚手架完成，进入后端 ∥ 前端并行实施')

phase('Implement')

const impl = await parallel([
  () => agent(`${CONV}
你是后端全量代理，负责 ${ROOT}/habitforge-backend 的 modules/ai 新建，以及授权修改：common/util/RedisUtil（若脚手架未建方法则你补）、GlobalExceptionHandler（6002→429 映射，最小改动）、journal/reflection 两模块各加一个只读方法（JournalService.listRecentWithContent、ReflectionService.listRecentByUser，不碰既有方法）。SHARED_SPEC 涉及的 ErrorCode/SQL/yml/pom 已写定不得改动。
先通读计划文件 P2 节，全部对齐 modules/study 写法（Result/ErrorCode/loadOwned/@Transactional(rollbackFor)/DTO @Builder+from()/hutool JSONUtil/jakarta validation 中文消息）：
1. entity：DailyPlan/PlanBlock/PlanFreeSlot/PlanGeneration；mapper：4 个 BaseMapper
2. AiProperties：@ConfigurationProperties("app.ai")，字段 enabled/dailyGenerateLimit/journalDays/journalCharsPerDay/reflectionLimit/reflectionChars/minBlocks/maxBlocks，注册方式仿 MinioProperties
3. PlanAiClient：ChatClient 薄封装 chat(system,user)→ChatResult(content,promptTokens,completionTokens,model)；Usage 取 ChatResponse.getMetadata().getUsage()
4. PlanPromptTemplate：system 规则按计划的 text block（仅输出一个合法 JSON、schema、块落空闲时段内不重叠、单块≤120min、ID 只能从清单挑、优先级、REST 穿插、title 中文≤30 字）；BeanOutputConverter<PlanDraft>（record PlanDraft{List<PlanDraftBlock> blocks}、PlanDraftBlock{start,end,title,type,habitId,subjectId,chapterId}）自动追加格式指令并解析
5. PlanContextAssembler：分节拼装（今天日期星期/users.identity_goal/空闲时段/HabitService.listToday 直接复用 HabitResponseDTO/StudyOverviewService/近期日记+心得=上面两个新只读方法并按配置截断）；总长超预算按日记 3→2→1 天降档
6. PlanJsonParser（纯静态可单测；JSON 提取/反序列化已由 converter 完成）：HH:mm 解析、start<end、type 白名单外→OTHER、habitId/subjectId/chapterId ∉ 候选集→null（防幻觉白名单）、title 截 30、超 maxBlocks 截断、按时间去重，单条不合格丢弃不整体失败；有效块<minBlocks 抛 AI_RESPONSE_INVALID
7. DailyPlanService：懒创建（存时段或生成时建行）；GET today/{date}（blocks+freeSlots；habitId 富化 habitCheckedToday=getByHabitAndDate、habitName/subjectName 批量查）；PUT free-slots 事务内先删后插整体覆盖+校验（HH:mm、start<end、≤8、排序后两两不重叠→6014）；POST adopt 全部 PROPOSED→ADOPTED 返回条数；blocks create（MANUAL 即 ADOPTED）/update/delete；complete(id,checkinHabit)：PROPOSED→ADOPTED→DONE 隐式链、ADOPTED→DONE、仅 DONE 再 complete→6013；checkinHabit&&habitId→调 CheckinService.checkin 完整复用，catch BusinessException(重复打卡码) 视为幂等成功；skip：PROPOSED/ADOPTED→SKIPPED；reopen：DONE→ADOPTED
8. AiScheduleService.generate：enabled=false→6001；今日 free-slots 空→6006（绝不触达 LLM）；tryAcquire("ai:plan:"+userId+":"+date,5,25h) 超限→6002；tryLock("habitforge:ai:lock:"+userId,"1",120) 冲突→6003，finally unlock；调 aiClient→converter→parser(候选 ID 集=上下文清单)；上游异常 release 退额度+写 success=0 流水→6004；解析失败写流水（不退额度）→6005；成功：gen_count+1、只删 PROPOSED 旧块、插新块（sort_order 按 start）、last_model 更新、写流水（tokens+raw_output）
9. PlanController /api/v1/plans 全部 12 端点，路径/字段与前端契约逐字一致（先读 habitforge-frontend/src/api/modules/plan.ts）；GET /today 未生成 Result.success(null) 仿 /journals/today；GET /usage 返回 {used,remaining,todayTokens(当日 plan_generations SUM)}
10. 单测（mock mapper/Redis/ChatModel，风格对齐 src/test 现有）：PlanJsonParserTest（幻觉 ID/时间非法或反转/空数组/超长 title/type 越界/不足 min）、AiScheduleServiceTest（成功/上游异常→6004 且 release 被调/解析失败→6005 不退/6002/6003/6001/6006）、DailyPlanServiceImplTest（状态机逐转移/时段校验/重新生成只删 PROPOSED/complete 打卡幂等）
验证：cd habitforge-backend && mvn -q test 全绿（含既有 70 项）。返回：新增文件清单 + 测试结果 + 与规格/契约偏差说明。`,
    { label: 'impl:backend-ai', phase: 'Implement' }),

  () => agent(`${CONV}
你是前端代理，负责 ${ROOT}/habitforge-frontend 的 views/plan、components/plan 新建与 PlanToday 桩替换。types/plan.d.ts、api/modules/plan.ts、router /plan 路由由脚手架写定不得改动；Home.vue 仅允许最小改动（挂 PlanCard，仿 StudyTaskCard 位置与刷新方式），其余共享文件不碰。
通读计划文件 P2 前端节 + types/plan.d.ts + api/modules/plan.ts 后实现（主题橙 #ff7a00、Vant4、页面级拉数无新 store、接口错误交给拦截器 toast）：
1. components/plan/FreeSlotEditor.vue：van-popup bottom；快捷标签 chips（纯前端预设：早 06:00-08:00/上午 09:00-12:00/下午 14:00-18:00/晚 19:00-22:30，点击即添加可再改）；van-time-picker 改起止；行删除；保存调 apiSaveFreeSlots（前端先挡 >8 条，后端 6014 消息展示）
2. components/plan/BlockItem.vue：[HH:mm-HH:mm][图标 HABIT⏰/STUDY📖/REST☕/OTHER📌][title][habitName/subjectName tag][按 status 操作区：ADOPTED→完成(有 habitId 显示「同时打卡」勾选默认勾)/跳过；DONE→撤销+灰标；PROPOSED→完成(隐式采纳)/跳过；SKIPPED→恢复+半透明]；habitCheckedToday→「已打卡」标；STUDY 块点击深链（chapterId→/study/subjects/{subjectId}，仅 subjectId→/study/review?subjectId=）
3. components/plan/BlockList.vue：按 startTime 排序渲染，事件上抛
4. components/plan/PlanCard.vue：Home 摘要卡：无计划→「设置空闲时段，让 AI 安排今天」引导；有计划→进度 x/y + 下一个未完成块预览；点击→/plan
5. views/plan/PlanToday.vue：nav-bar；空闲时段摘要行（点开 FreeSlotEditor，显示 n 个时段）；「AI 生成今日安排」大按钮（全屏 loading「AI 正在规划…」；已生成则「重新生成」二次确认「将替换未采纳的建议块」）；顶部 apiPlanUsage().remaining「今日剩余 n/5 次」；BlockList+进度条；一键采纳（存在 PROPOSED 时）；「手动添加」弹层（type chips/title/时间/习惯下拉）；日期右箭头可切昨日及以前（历史只读，操作按钮隐藏）
6. Home.vue：PlanCard 插 StudyTaskCard 之上，onActivated 一并刷新
验证：npx vue-tsc --noEmit 通过。返回：文件清单 + 检查结果 + 契约疑点（不得擅自改契约文件）。`,
    { label: 'impl:frontend-plan', phase: 'Implement' }),
])

if (!impl.filter(Boolean).length) throw new Error('both implement agents failed')
log('双轨实现完成，进入集成')

phase('Integrate')

const integrate = await agent(`${CONV}
你是集成代理，两轨已落盘未提交，允许跨轨最小改动修复不一致：
1. 契约交叉核对：api/modules/plan.ts 12 函数 vs PlanController 端点逐条（方法/路径/字段名/类型）；types/plan.d.ts vs Response DTO 逐字段（habitCheckedToday/source/completedAt/freeSlots 可空语义）；GET /plans/today null 在 PlanToday 与 PlanCard 两处均安全。不一致以脚手架契约为准改实现。
2. Home.vue 复核：PlanCard 位置、onActivated 刷新链、/plan 无 tab meta、keep-alive include 未破坏、无「建设中」残留。
3. 全量构建：cd habitforge-frontend && npm run build 通过；cd habitforge-backend && mvn -q test 全绿。
返回：修复清单 + 两项构建结果。`,
  { label: 'integrate:p2', phase: 'Integrate' })

if (!integrate) throw new Error('integrate failed')

phase('Verify')

const FINDINGS_SCHEMA = {
  type: 'object',
  properties: {
    pass: { type: 'boolean' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          track: { type: 'string', enum: ['backend', 'frontend', 'contract', 'spec'] },
          file: { type: 'string' },
          issue: { type: 'string' },
          severity: { type: 'string', enum: ['blocking', 'non-blocking'] },
          suggestedFix: { type: 'string' },
        },
        required: ['track', 'file', 'issue', 'severity'],
      },
    },
  },
  required: ['pass', 'findings'],
}

let rounds = 0, passed = false, remaining = []
while (rounds < 3 && !passed) {
  rounds++
  const checks = await parallel([
    () => agent(`${CONV}
你是后端验证代理（第 ${rounds} 轮），对抗式检查 modules/ai + RedisUtil + GlobalExceptionHandler + journal/reflection 新只读方法，对照计划文件 P2 节：
1. cd habitforge-backend && mvn -q test 须全绿
2. 状态机逐转移（PROPOSED→adopt→ADOPTED→complete→DONE→reopen；complete PROPOSED 隐式采纳；仅 DONE 再 complete→6013；MANUAL 即 ADOPTED）；重新生成只删 PROPOSED；打卡走 CheckinService.checkin 完整复用且重复打卡幂等成功
3. 护栏：enabled=false→6001；无时段→6006 且代码路径不可能触达 LLM；限流 key 含日期 5/25h→6002 且 6002 映射 HTTP 429；锁 finally 必释放→6003；上游失败 release、解析失败不退；每次生成（含失败）写流水
4. 防幻觉：PlanJsonParser 白名单置 null/时间校验/单条丢弃/不足 min→6005 均有单测证明；raw_output 无 markdown 围栏残留入库路径
5. free-slots：HH:mm/start<end/≤8/两两不重叠→6014；先删后插同事务
6. 凭证红线：grep -rI "sk-" habitforge-backend/src docker-compose.yml 不得出现真实 token
逐条给证据（文件:行），问题分 blocking/non-blocking。`,
      { label: `verify:backend-r${rounds}`, phase: 'Verify', schema: FINDINGS_SCHEMA }),

    () => agent(`${CONV}
你是前端验证代理（第 ${rounds} 轮），对抗式检查 habitforge-frontend：
1. cd habitforge-frontend && npm run build 须通过（含 vue-tsc）
2. api/modules/plan.ts vs PlanController 路径方法字段逐条；apiGeneratePlan 的 120s per-request timeout 写法确实生效（读 request 封装验证）
3. types vs DTO 逐字段；/plans/today null 两消费点安全
4. 交互对照计划文件：完成并打卡默认勾选可取消、habitCheckedToday 标记、STUDY 深链、一键采纳仅动 PROPOSED、剩余次数展示、失败重试+手动加块兜底、历史日期只读
5. 无新 Pinia store；Home 改动最小；全 src 无「建设中」残留；主题色一致
6. grep 前端源码不得有 sk- 开头字符串
逐条给证据，问题分 blocking/non-blocking。`,
      { label: `verify:frontend-r${rounds}`, phase: 'Verify', schema: FINDINGS_SCHEMA }),
  ])

  const all = checks.filter(Boolean)
  remaining = all.flatMap(c => c.findings || [])
  const blocking = remaining.filter(f => f.severity === 'blocking')
  passed = all.length === 2 && all.every(c => c.pass) && blocking.length === 0
  if (passed) { log(`第 ${rounds} 轮验证通过（低危不阻塞 ${remaining.length} 项）`); break }
  log(`第 ${rounds} 轮：${blocking.length} 项阻塞，进入修复`)

  const fixList = JSON.stringify(blocking.length ? blocking : remaining, null, 1)
  await parallel([
    () => agent(`${CONV}
你是后端修复代理。逐条修复以下发现（只动后端轨道文件），修完 cd habitforge-backend && mvn -q test 全绿：
${fixList}
返回逐条处理说明。`, { label: `fix:backend-r${rounds}`, phase: 'Fix' }),
    () => agent(`${CONV}
你是前端修复代理。逐条修复以下发现（只动前端轨道文件；types/plan.d.ts 与 api/modules/plan.ts 除非发现明确要求否则不动），修完 npx vue-tsc --noEmit 通过：
${fixList}
返回逐条处理说明。`, { label: `fix:frontend-r${rounds}`, phase: 'Fix' }),
  ])
}

return { rounds, passed, remainingFindings: remaining }
