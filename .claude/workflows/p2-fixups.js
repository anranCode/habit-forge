export const meta = {
  name: 'p2-fixups',
  description: 'P2 收尾：把验证轮遗留的非阻塞缺陷（超时/锁持有者/流水缺口/状态机不可逆/校验 NPE/查询膨胀/文案与文档）逐条修掉并复核',
  phases: [
    { title: 'Fix', detail: '后端缺陷批次 ∥ 前端与文档批次' },
    { title: 'Verify', detail: '逐条核对落地 + 回归（mvn test / npm build）' },
  ],
}

const PLAN = 'C:\\Users\\ZHIXUAN_HU\\.claude\\plans\\readme-md-plan-1-ai-2-validated-pillow.md'
const ROOT = 'E:/study/code/habit-forge'

const CONV = `
## 项目约定
- 仓库根 ${ROOT}，分支 feature/study-ai-plan；计划文件 P2 节是规格：${PLAN}
- P2 主体已实现（modules/ai、views/plan、components/plan），两轮验证已通过，本轮只做下述缺陷修复，不做重构、不加新功能
- 只在被允许的文件内改动；不要 git commit/push；后端验证用 cd habitforge-backend && mvn -q test，前端用 cd habitforge-frontend && npm run build（本轮无并发构建冲突，可用 build）
- 凭证红线：不得写入任何 token；真实 AI 调用一律禁止，测试全 mock（真实联调只由 AiProviderLiveTest 在 AI_LIVE_TEST=1 时单独跑；供应商现为 DeepSeek，原 TokenPlan 已于 2026-09-13 弃用）
- 代码风格对齐既有 modules/study / P2 已落盘代码（中文注释、Result/ErrorCode、@Transactional(rollbackFor)、hutool、jakarta validation 中文消息）
`

const BACKEND_ITEMS = `
【后端修复清单】（文件均为相对 habitforge-backend 的路径；每条都要有对应单测或明确说明为何不加）
B1 超时缺失（最高优先）：application.yml 只配了 spring.ai.retry.max-attempts=2，整条 HTTP 客户端链路无超时。先**核实** Spring AI 1.1.8 的 anthropic 实现实际用哪个 HTTP 客户端与 bean（读依赖 jar 或 IDE 查 ChatModel/RestClient.Builder 的构造链），然后按核实结果落地：
   - 若消费 Boot 的 ClientHttpRequestFactory 自动配置 → 在 application.yml 加 spring.http.client.connect-timeout: 5s / read-timeout: 45s（含 prod profile 同步）
   - 否则 → 在 modules/ai/config/AiConfig 中提供定制的 RestClient.Builder（或 ClientHttpRequestFactory）显式设置两项超时
   目标不变式：最坏耗时 ≈ 45s×2 次重试 < AppConstant.AI_LOCK_TTL_SECONDS=120。返回里说明你如何确认超时确实生效（读了哪个类/哪行）。
B2 锁非 fenced：RedisUtil.tryLock 的 value 恒为 "1"，unlock(key) 无条件 delete → 长耗时请求锁过期后可能删掉他人的锁。改为：tryLock 接受 owner value（调用方传 UUID.randomUUID().toString()），新增/改造 unlock(key, owner) 用 Lua 脚本比对 value 再删（GET==owner 才 DEL）；AiScheduleServiceImpl 相应改动（生成 owner 并传入 finally 的 unlock）。补单测：非持有者 unlock 不得删除锁。
B3 流水缺口：AiScheduleServiceImpl 第 108 行 replaceProposedWithGenerated 未被 try/catch 包裹——此刻 token 已耗，落块失败会导致「有消耗无流水」。包 try/catch：失败时同样 writeGeneration(..., false, describe(e), result.content()) 后再抛出（异常类型按现有语义，落块失败属服务端错误）。
B4 错误语义被吞：AiScheduleServiceImpl:87-93 的 catch(Exception) 把 PlanAiClient 抛出的 BusinessException(AI_NOT_CONFIGURED=6001，ChatModel/凭证缺失时) 改写成 6004。改为 catch 中先判断 e 是 BusinessException 且 code 为 AI_NOT_CONFIGURED 时原样抛出，其余仍翻译 6004。补测：mock PlanAiClient 抛 6001 → 断言最终抛的是 6001。
B5 校验 NPE：FreeSlotSaveRequest.slots 无元素级约束，body {"slots":[null]} 会通过校验后在 DailyPlanServiceImpl 第 124 行 NPE → HTTP 500。改为元素级约束 List 元素加 @NotNull @Valid（或 service 循环首行 null 判断 → BusinessException(FREE_SLOT_INVALID)）。补测：slots 含 null → 6014。
B6 状态机不可逆：reopen 目前只接受 DONE，而前端对 SKIPPED 块渲染「恢复」按钮（调用同一 reopen）→ 必然 6013，功能不可达。改为 reopen 接受 DONE 与 SKIPPED（DONE/SKIPPED → ADOPTED），complete 对 SKIPPED 维持 6013。补测：SKIPPED→reopen→ADOPTED。
B7 未来日期连带失败：completeBlock 用 plan.getPlanDate() 作打卡日期，对未来日期的计划块勾「完成并打卡」会命中 CHECKIN_DATE_INVALID(3003) 非 DUPLICATE → 整块完成失败。改为：planDate 晚于今天时跳过打卡调用（仅置 DONE，日志说明），块仍能正常完成。补测覆盖。
B8 查询膨胀：ReflectionServiceImpl.listRecentByUser 先查该用户全部日记 id → IN 全量 → 再 selectBatchIds 重复查同一批日记，且每次 AI 生成都走这条路。改为不超过两次查询的方案（如先按 journal created_at DESC LIMIT 分页取最近的日记，再以其 id 查心得；复用已查到的 journal map，去掉 selectBatchIds 重复查询）。行为契约（返回最近 N 条心得、按时间倒序）不得变化。
B9 500 → 400：GET /plans/{date} 传非日期串（/plans/foo）目前落到兜底 Exception → 500。在 GlobalExceptionHandler 增加 MethodArgumentTypeMismatchException（至少覆盖 LocalDate 转换失败）→ 400 + 明确中文消息；如已有同类 handler 则复用其风格。
B10 测试盲点：DailyPlanServiceImplTest 补两条断言——ADOPTED→skip→SKIPPED、ADOPTED→complete(不勾打卡)→DONE。
B11 容器时区：docker-compose.yml backend environment 增加 TZ=Asia/Shanghai（对齐现有 env 风格），保证容器内 LocalDate.now() 为北京时间（计划风险节的日期边界要求）。若 compose 已有 TZ 则跳过并说明。

验证：cd habitforge-backend && mvn -q test 全绿（基线 115 项，只增不减）。
`

const FRONTEND_ITEMS = `
【前端与文档修复清单】（相对 habitforge-frontend 的路径）
F1 components/plan/BlockItem.vue：勾选框文案「同时打卡」→「完成并打卡」（计划逐字要求，可取消、默认勾选的行为不变）；当 habitCheckedToday=true 且块未 DONE 时，主按钮文案改为「同步为完成」（点击仍调 apiCompleteBlock(id, false)），带「已打卡」绿标。
F2 components/plan/BlockList.vue：历史只读态空列表文案改为「当日无安排」（当前文案指向已隐藏的生成/手动添加控件）；非只读态保持原文案。
F3 components/plan/PlanCard.vue：未生成态文案改为「AI 生成今日安排 · 先设置空闲时段」（计划描述），现有跳转行为不变。
F4 views/plan/PlanToday.vue：生成失败时在生成按钮下方显示一行带「重试」链接的失败提示（复用同一 onGenerate，失败消息取拦截器 error message 或兜底文案），保留手动加块兜底入口。

验证：cd habitforge-frontend && npm run build 通过（含 vue-tsc）。不新增 Pinia store、不改主题色、不动契约文件 types/plan.d.ts 与 api/modules/plan.ts。
`

phase('Fix')

const fixed = await parallel([
  () => agent(`${CONV}
你是后端修复代理，逐条修复下列缺陷。每条：先读现状代码确认问题成立，再最小改动修复；修完跑 cd habitforge-backend && mvn -q test 全绿。
${BACKEND_ITEMS}
返回：逐条处理说明（改了什么/为什么/对应测试）+ 最终 mvn test 统计行。`,
    { label: 'fix2:backend', phase: 'Fix' }),

  () => agent(`${CONV}
你是前端与文档修复代理，逐条修复下列项。
${FRONTEND_ITEMS}
另外：更新 ${ROOT}/README.md 的功能特性表，新增一行「AI 今日安排 | 空闲时段录入 → AI 生成结构化时间块 → 一键采纳 → 完成并联动打卡」（与既有表格风格一致），无需改 Roadmap 候选列表。
返回：逐条处理说明 + npm run build 结果。`,
    { label: 'fix2:frontend', phase: 'Fix' }),
])

if (!fixed.filter(Boolean).length) throw new Error('both fix agents failed')
log('修复批次完成，进入逐条复核')

phase('Verify')

const SCHEMA = {
  type: 'object',
  properties: {
    pass: { type: 'boolean' },
    landed: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          item: { type: 'string' },
          ok: { type: 'boolean' },
          evidence: { type: 'string' },
        },
        required: ['item', 'ok', 'evidence'],
      },
    },
    regressions: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          file: { type: 'string' },
          issue: { type: 'string' },
        },
        required: ['file', 'issue'],
      },
    },
  },
  required: ['pass', 'landed', 'regressions'],
}

const checks = await parallel([
  () => agent(`${CONV}
你是复核代理（后端）。对下列每条逐条核实是否真的落地（读代码/测试，不接受口头声称），并找出修复引入的回归：
B1 超时生效（说明依据的类与属性）｜B2 锁带 owner 且非持有者删不掉｜B3 落块失败也写流水｜B4 ChatModel 缺失仍是 6001｜B5 slots 含 null → 6014 而非 500｜B6 reopen 接受 SKIPPED｜B7 未来日期完成不因 3003 失败｜B8 查询次数与契约不变｜B9 /plans/foo → 400｜B10 两条补测存在且断言真实｜B11 compose TZ
另外必跑：cd habitforge-backend && mvn -q test（须全绿，统计行贴出）。
任何一条未落地或引入回归，进 regressions 并说明证据。`,
    { label: 'verify2:backend', phase: 'Verify', schema: SCHEMA }),

  () => agent(`${CONV}
你是复核代理（前端与文档）。逐条核实：
F1 文案「完成并打卡」与「同步为完成」路径｜F2 readonly 空态文案｜F3 PlanCard 文案｜F4 失败重试行存在且复用 onGenerate｜README 功能表新增行
另外必跑：cd habitforge-frontend && npm run build（须过，贴结果摘要）。核对未新增 Pinia store、未改契约文件（git diff --stat 看 types/plan.d.ts 与 api/modules/plan.ts 是否被动）。
任何一条未落地或引入回归，进 regressions。`,
    { label: 'verify2:frontend', phase: 'Verify', schema: SCHEMA }),
])

const all = checks.filter(Boolean)
const regressions = all.flatMap(c => c.regressions || [])
const notLanded = all.flatMap(c => (c.landed || []).filter(l => !l.ok))
return {
  pass: all.length === 2 && all.every(c => c.pass) && regressions.length === 0 && notLanded.length === 0,
  notLanded,
  regressions,
  detail: all,
}
