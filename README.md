# HabitForge · 习惯锻造

> 基于《掌控习惯》(Atomic Habits) 四大定律打造的习惯养成应用。记录每日习惯、积累积分、升级成长，用"小赢"证明自己。

## ✨ 功能特性

- 🎯 **习惯管理**：创建、编辑、追踪每日习惯，支持打卡记录
- ⭐ **积分等级**：每次打卡获取积分，累积升级，可视化成长进度
- 🏆 **连续纪录**：统计最长连续打卡天数，激励坚持
- 🎭 **身份设定**：设定你想成为的人，用习惯小赢证明自己
- 📊 **个人统计**：累计打卡次数、习惯总数、最长连续纪录一览
- 🔐 **JWT 认证**：注册/登录，安全令牌管理

## 🛠 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 · Vite · Pinia · Vue Router |
| 后端 | Spring Boot (Java 17) |
| 数据库 | MySQL 8 · Redis |
| 认证 | JWT (JSON Web Token) |
| 部署 | Docker · docker-compose · Nginx |

## 📁 项目结构

```
habit-forge/
├── frontend/          # 前端源码 (Vue 3 + Vite)
├── backend/           # 后端源码 (Spring Boot)
└── deploy/            # 部署配置 (docker-compose / Dockerfile / nginx)
```

## 🚀 快速开始

### 本地开发

**前端：**
```bash
cd frontend
npm install
npm run dev
```

**后端：**
```bash
cd backend
mvn spring-boot:run
```

### Docker 部署

```bash
cd deploy
cp .env.example .env   # 配置数据库密码与 JWT_SECRET
docker compose up -d --build
```

前端默认运行于 `http://localhost:8081`，`/api/` 请求由 nginx 反代至后端服务。

## ⚙️ 环境变量

| 变量 | 说明 |
|------|------|
| `MYSQL_USER` | MySQL 用户名 |
| `MYSQL_PASSWORD` | MySQL 密码 |
| `JWT_SECRET` | JWT 签名密钥（务必使用强随机值） |

## 📄 License

MIT