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

## 技术栈

- **前端**: Vue 3 + TypeScript + Vite 5 + Vant 4 + Pinia + Vue Router + Axios + Day.js
- **后端**: Spring Boot 3.2 + Spring Security + JWT (jjwt 0.12) + MyBatis-Plus 3.5.5
- **存储**: MySQL 8.0 + Redis 7（打卡限流 + 登出 token 黑名单）
- **部署**: Docker Compose（mysql + redis + backend + frontend/nginx）

## 目录结构

```
habit-forge/
├── habitforge-backend/     # Spring Boot 后端
│   └── src/main/
│       ├── java/com/habitforge/
│       │   ├── common/         # Result/异常/Jwt/Redis/频率工具/配置
│       │   └── modules/        # auth/user/habit/checkin/streak/achievement
│       └── resources/
│           ├── application*.yml
│           └── db/schema.sql   # 建库脚本
├── habitforge-frontend/    # Vue 3 前端
│   └── src/
│       ├── api/            # axios 封装 + 各模块接口
│       ├── components/     # HabitCard/HabitForm/CalendarHeatmap/...
│       ├── views/          # auth/home/habits/track/profile
│       ├── stores/         # Pinia（持久化登录态）
│       └── router/         # 路由 + 守卫
└── docker-compose.yml
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

统一响应格式：`{ code, message, data, timestamp }`，code=200 成功，401 未登录，3001 今日已打卡等业务码见 `ErrorCode.java`。

## 设计要点

1. **链计算采用全量重算**：单用户数据量小，每次打卡/撤销后从全部打卡记录按频率重算 current/longest，避免增量算法在撤销、漏卡、周频率下的边界 bug（`StreakServiceImpl`）。
2. **打卡幂等**：`uk_habit_date` 唯一键 + 捕获 `DuplicateKeyException` 返回友好错误。
3. **打卡事务**：写 checkins、重算 streaks、发积分在同一 `@Transactional`。
4. **频率模型**：DAILY / WEEKLY_DAYS（如周一三五）/ WEEKLY_COUNT（每周 N 次，链按"周"计）。
5. **积分体系**：打卡 +10，里程碑 7/30/100 天分别 +50/+200/+500（写入 achievements），每 100 分升 1 级。

## 环境变量

| 变量 | 说明 |
|------|------|
| `MYSQL_USER` / `MYSQL_PASSWORD` | MySQL 账号密码 |
| `JWT_SECRET` | JWT 签名密钥（务必使用强随机值，如 `openssl rand -hex 32`） |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO 对象存储（日记图片） |
| `APP_CORS_ALLOWED_ORIGIN_PATTERNS` | CORS 允许来源（生产环境必填） |

## Roadmap（v1.1+）

- [ ] 补卡保护卡（每月 1 次）
- [ ] 坏习惯戒断模式（表字段已预留 habit_type=BAD）
- [ ] 环境设计模块（environment_settings 表已建）
- [ ] 习惯契约（contracts 表已建，问责伙伴手填姓名）
- [ ] 复盘中心（reviews 表已建）
- [ ] 打卡提醒推送

## 📄 License

MIT
