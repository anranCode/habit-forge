# 🔥 HabitForge — 习惯锻造

基于《掌控习惯》(Atomic Habits) 四大定律的习惯管理打卡系统。

> 微小的变化，显著的结果 —— 每天进步 1%，一年后进步 37 倍。

## 功能特性

| 书中概念 | 系统实现 |
|---------|---------|
| 身份认同（第二层改变） | 注册时设定「我想成为…」，习惯可绑定身份标签 |
| 执行意图（第一定律） | 习惯设定执行时间 + 执行地点 |
| 习惯叠加（第一定律） | 「继[某习惯]之后」字段 |
| 两分钟规则（第三定律） | 每个习惯可设「两分钟微习惯版本」 |
| 灵活频率 | 每日 / 每周指定几天 / 每周 N 次 |
| 习惯追踪（第四定律） | 日历热力图、连续链 🔥、积分与等级 |
| 绝不错过两次（第四定律） | 昨日漏卡的习惯在首页标红提醒 |
| 里程碑奖励 | 连续 7/30/100 天自动解锁成就 + 额外积分 |
| 学习中心（自考） | 科目-章节树进度 + 考试倒计时，章节完成 +20 积分 |
| 间隔重复 | 闪卡 SM-2 四档评分复习队列，每日复习满 5 张 +10 积分 |
| 学习笔记 | Markdown 笔记 + 图片上传（MinIO），前端 marked+DOMPurify 渲染 |
| 错题本 | 题库录入 + 错题重练，连对 2 次自动摘除 |
| AI 今日安排 | 空闲时段录入 → AI 生成结构化时间块 → 一键采纳 → 完成并联动打卡 |
| 学习时长记录 | 学习中心「开始/结束」计时 + 手动补录，按日/按科目汇总（学习维度 30 分钟达标线） |
| 每周 AI 复盘 | 客观数据快照（学习时长/打卡/复习/日记/手机节制）→ AI 输出评分 + 客观评价 + 可执行建议，可编辑 |
| 手机节制（注意力） | 每日娱乐时长 vs 上限达标（差量 ±10 积分）、「想刷但忍住了」冲动记录与忍住率、近 14 天趋势 |
| 环境设计清单 | 启用 `environment_settings`：手机放远/卸载 App/灰度模式等可勾选清单，手机节制预设一键导入（幂等） |
| 坏习惯戒断打卡 | `habit_type=BAD` 打通：打卡语义为「今天忍住了」，链按连续天数计 |
| 习惯契约 | 启用 `contracts`：问责伙伴 + 违约代价，状态 ACTIVE/COMPLETED/BROKEN |

## 技术栈

- **前端**: Vue 3 + TypeScript + Vite 5 + Vant 4 + Pinia + Vue Router + Axios + Day.js + marked/DOMPurify
- **后端**: Spring Boot 3.5 + Spring Security + JWT (jjwt 0.12) + MyBatis-Plus 3.5.5
- **AI**: Spring AI 1.1.x（`spring-ai-starter-model-anthropic`）→ Anthropic 兼容端点，默认 DeepSeek `deepseek-v4-flash`
- **存储**: MySQL 8.0 + Redis 7（打卡限流 + 登出 token 黑名单 + AI 生成限流/锁）+ MinIO（图片）
- **部署**: Docker Compose（开发：mysql + redis + backend + frontend/nginx；生产见 `deploy/`）

## 目录结构

```
habit-forge/
├── habitforge-backend/     # Spring Boot 后端
│   └── src/main/
│       ├── java/com/habitforge/
│       │   ├── common/         # Result/异常/Jwt/Redis/频率工具/配置
│       │   └── modules/        # auth/user/habit/checkin/streak/achievement/journal/reflection/study/ai
│       └── resources/
│           ├── application*.yml
│           └── db/
│               ├── schema.sql              # 全量建库脚本（新库）
│               └── upgrade_*.sql           # 增量脚本（存量库手工执行，顺序见文件名）
├── habitforge-frontend/    # Vue 3 前端
│   └── src/
│       ├── api/            # axios 封装 + 各模块接口
│       ├── components/     # HabitCard/HabitForm/CalendarHeatmap/study/plan/...
│       ├── views/          # auth/home/habits/track/profile/journal/study/plan
│       ├── stores/         # Pinia（持久化登录态）
│       └── router/         # 路由 + 守卫
├── deploy/                 # 生产部署包（预构建产物 + 部署专用 compose + 部署说明）
│   └── README-部署说明.md   # ← 部署前必读
└── docker-compose.yml      # 本机开发编排
```

## 快速开始

### 方式一：Docker Compose（一键启动）

```bash
docker compose up -d --build
# 前端: http://localhost    后端: http://localhost:8080
```

首次启动 MySQL 会自动执行 `schema.sql` 建表。

### 方式二：本地开发

**1. 准备 MySQL 8 与 Redis**（或直接用已有实例），导入建表脚本：

```bash
mysql -h <host> -uroot -p < habitforge-backend/src/main/resources/db/schema.sql
```

**2. 启动后端**（修改 `application-dev.yml` 中的数据库/Redis 地址）：

```bash
cd habitforge-backend
mvn spring-boot:run
# http://localhost:8080
```

**3. 启动前端**：

```bash
cd habitforge-frontend
npm install
npm run dev
# http://localhost:5173 （/api 已代理到 8080）
```

## 核心 API（/api/v1）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/register` | 注册（返回 token） |
| POST | `/auth/login` | 登录 |
| POST | `/auth/logout` | 登出（token 进黑名单） |
| GET | `/auth/me` | 当前用户信息 |
| GET/POST | `/habits` · `/habits/{id}` | 习惯 CRUD |
| GET | `/habits/today` | 今日待打卡（按频率过滤） |
| GET | `/habits/stats` | 追踪看板统计 |
| POST | `/checkins` | 打卡（幂等防重、重算链、发积分） |
| DELETE | `/checkins/{id}` | 撤销打卡 |
| GET | `/checkins/month?month=2026-08` | 月度打卡日期（热力图） |
| GET | `/streaks/top` | 习惯链排行 |
| GET/POST | `/journals` · `/journals/{id}` | 日记 CRUD（含图片上传） |
| GET/POST | `/reflections` · `/reflections/{id}` | 心得 CRUD（`GET /journal/{id}`、`/habit/{id}` 按来源查） |
| GET/POST | `/subjects` · `/subjects/{id}` | 科目 CRUD（含章节树、考试倒计时） |
| POST | `/chapters` · `/chapters/{id}` | 章节 CRUD（`GET /subject/{id}` 取树，`PATCH /{id}/status` 置进度） |
| GET | `/study/overview` | 学习总览（进度/到期卡/错题数，Home 卡片与 AI 上下文共用） |
| GET/POST | `/flashcards` · `/flashcards/{id}` | 闪卡 CRUD；`GET /queue` 到期队列，`POST /{id}/review` 四档评分 |
| GET/POST | `/notes` · `/notes/{id}` | Markdown 笔记 CRUD（`POST /{id}/images` 传图） |
| GET/POST | `/questions` | 题库 CRUD |
| GET/POST | `/wrong-questions` | 错题本（`POST /{questionId}/practice` 记对错，`PATCH /{questionId}/mastered` 摘除） |
| POST | `/plans/generate` | AI 生成今日安排（同步阻塞，可能数十秒） |
| GET | `/plans/today` · `/plans/{date}` | 今日/指定日安排（未生成时 `data=null`） |
| PUT | `/plans/free-slots` | 覆盖式保存当日空闲时段 |
| POST | `/plans/adopt` · `/plans/blocks/{id}/complete` | 一键采纳 / 完成（可联动打卡） |
| GET | `/plans/usage` | 今日 AI 生成额度用量 |
| POST | `/study/sessions/start` · `/{id}/end` | 开始/结束学习计时（已有进行中返 7010，超 12 小时返 7012） |
| POST | `/study/sessions` | 手动补录时长（不允许未来日期） |
| GET | `/study/sessions` · `/sessions/active` | 某日记录 / 进行中的计时（刷新后恢复计时） |
| GET | `/study/time/summary` · `/time/daily` | 某日汇总 / 区间每日分钟（无记录的日子补 0） |
| POST | `/reviews/weekly/generate` | AI 生成/重新生成某周复盘（同步阻塞，可能数十秒） |
| GET | `/reviews/weekly` · `/weekly/usage` | 某周报告（未生成 data=null） / 本周生成额度 |
| PUT | `/reviews/weekly/{id}` | 编辑周报（AI 只出初稿，终稿归用户） |
| GET/PUT | `/focus/today` · `/focus/logs` | 今日注意力状态 / 录入某日娱乐时长（达标变化结算积分） |
| POST | `/focus/urges` | 记一次「想刷手机」的冲动（忍住与否都记，只记今天） |
| GET | `/focus/trend?from&to` · `PUT /focus/limit` | 区间趋势（未录入日 minutes 为 null） / 设置每日上限 |
| GET/POST/PUT/DELETE | `/environment-settings` | 环境设计清单 CRUD（`PATCH /{id}/active` 勾选、`POST /batch` 预设幂等导入） |
| GET/POST/PUT/DELETE | `/contracts` | 习惯契约 CRUD（`PATCH /{id}/status` 流转 ACTIVE/COMPLETED/BROKEN） |

统一响应格式：`{ code, message, data, timestamp }`，code=200 成功，401 未登录，3001 今日已打卡等业务码见 `ErrorCode.java`。

## 设计要点

1. **链计算采用全量重算**：单用户数据量小，每次打卡/撤销后从全部打卡记录按频率重算 current/longest，避免增量算法在撤销、漏卡、周频率下的边界 bug（`StreakServiceImpl`）。
2. **打卡幂等**：`uk_habit_date` 唯一键 + 捕获 `DuplicateKeyException` 返回友好错误。
3. **打卡事务**：写 checkins、重算 streaks、发积分在同一 `@Transactional`。
4. **频率模型**：DAILY / WEEKLY_DAYS（如周一三五）/ WEEKLY_COUNT（每周 N 次，链按"周"计）。
5. **积分体系**：打卡 +10，里程碑 7/30/100 天分别 +50/+200/+500（写入 achievements），每 100 分升 1 级。
6. **学习时长只认已结束的记录**：进行中的计时不计入统计，避免时长随秒数跳动；单段上限 12 小时（防挂机），超过则要求删除后手动补录。
7. **周报的客观数据与 AI 输入同源**：`WeeklyReportContextAssembler` 生成一份快照，既拼进 prompt，也原样存进 `reviews.stats_snapshot`——报告可复现、可审计；prompt 超预算时按「日记正文 → 心得」降档，**数字骨架永不裁**，因为那是 AI 下结论的唯一依据。
8. **节制达标按差量结算**：娱乐时长「不达标→达标」+10、「达标→不达标」−10（沿打卡撤销范式）；`entertainment_minutes` 允许为 NULL 表示"当日尚未录入"，**点了冲动按钮不会白拿达标分**；改每日上限不追溯调整已结算积分。
9. **周报统计区间止于今天**：本周未过完时 periodEnd 取当天而非周日，否则未来的空白日会把"日均"摊薄，AI 会据此得出"你本周只学了 3 天"的错误结论。

## 环境变量

两份 compose 用的变量名**不同**，别混：根 `docker-compose.yml`（本机开发，自带 mysql/redis 容器）用
`MYSQL_ROOT_PASSWORD`；`deploy/docker-compose.yml`（生产，连宿主机 3306 上独立运行的 `mysql8` 容器）用 `MYSQL_USER`/`MYSQL_PASSWORD`。

| 变量 | 说明 |
|------|------|
| `MYSQL_ROOT_PASSWORD` | 根 compose：自带 MySQL 容器的 root 密码（必填） |
| `MYSQL_USER` / `MYSQL_PASSWORD` | deploy compose：宿主机 MySQL 账号（`MYSQL_USER` 默认 root）/ 密码（必填） |
| `JWT_SECRET` | JWT 签名密钥（务必使用强随机值，如 `openssl rand -hex 32`） |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO 对象存储（日记/笔记图片）。⚠️ 改 `MINIO_ENDPOINT` 会同时影响后端上传与前端展示：展示路径由 nginx 的 `location /images/` 反代，**不读这个变量**，两边不一致就是「上传成功、图片裂图」 |
| `SPRING_DATA_REDIS_PASSWORD` | 仅当 Redis 设了 `requirepass` 时填（默认空=不鉴权）。必须写进 compose 的 `environment` 才进得了容器，写在 `.env` 却不转发会静默无效 |
| `APP_CORS_ALLOWED_ORIGIN_PATTERNS` | CORS 允许来源（deploy compose 必填） |
| `AI_API_KEY` | AI 服务密钥（DeepSeek）。只经环境变量注入，**绝不写入代码/配置/提交**。留空 = 生成接口恒返 6001，**不会**影响其它功能也不会报启动错 |
| `AI_ENABLED` | AI 今日安排总开关（prod 默认 true，设 false 时生成接口返 6001，不影响其他功能） |
| `AI_BASE_URL` | 选填，Anthropic 兼容端点，默认 `https://api.deepseek.com/anthropic` |
| `AI_MODEL` | 选填，默认 `deepseek-v4-flash`（换模型只改这一个环境变量） |

关于 `AI_API_KEY` 缺失时的行为：**不会**导致启动失败，也**不会**静默 401。key 为空（或 yml 里写成
`${AI_API_KEY}` 却未解析）时，生成接口明确返回 **6001「AI 服务未启用」**；key 有值但无效/欠费才返回
**6004「AI 服务调用失败」**。两者语义不同，排障时先看是哪个码（判定逻辑在 `PlanAiClient#credentialsMissing`）。
因此 `AI_ENABLED=false` 是一个真正可用的紧急开关：不需要先准备一个 key 就能关掉 AI。

## Roadmap（v1.1+）

- [ ] 补卡保护卡（每月 1 次）
- [x] 坏习惯戒断模式（habit_type=BAD 已打通到表单与卡片）
- [x] 环境设计模块（environment_settings + category=PHONE）
- [x] 习惯契约（contracts 表启用，问责伙伴手填姓名）
- [x] 每周 AI 复盘（reviews 表 type=WEEKLY；DAILY/MONTHLY/QUARTERLY 待补）
- [ ] 打卡提醒推送

## 📄 License

MIT
