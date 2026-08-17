# HabitForge 部署说明（目标服务器：<SERVER_IP>）

构建日期：2026-08-05 · 构建产物已通过静态验证（JAR 结构 / dist 完整性 / 无硬编码后端地址）

## 一、部署包内容

| 文件 | 说明 |
|------|------|
| `habitforge-backend.jar` | Spring Boot 可执行 fat jar（42.9 MB，Java 17，含 prod 配置，7 个单元测试全部通过） |
| `dist/` | 前端生产构建（Vite，44 个文件，入口 JS/CSS gzip 后约 75 KB） |
| `dist.zip` | dist 的压缩包（便于传输） |
| `nginx.conf` | nginx 站点配置（SPA history 路由 fallback + `/api/` 反代到后端） |
| `Dockerfile.backend` | 精简版：直接 COPY 预构建 JAR，不在服务器上跑 Maven |
| `Dockerfile.frontend` | 精简版：直接 COPY dist 到 nginx，不在服务器上跑 npm |
| `docker-compose.yml` | 部署专用编排：**不含 mysql/redis 容器**（服务器上已有原生服务，避免 3306/6379 端口冲突） |
| `.env.example` | 环境变量模板 |

## 二、服务器前提

- [x] MySQL 8 运行于 3306（`habitforge` 库的 8 张表已于 2026-08-05 导入，无需再建表）
- [x] Redis 运行于 6379
- [ ] 已安装 Docker 与 compose 插件（`docker compose version` 验证）
- [ ] 服务器 **8081** 端口空闲且防火墙放行（80 已被服务器上现有 nginx 占用，故对外用 8081；如 8081 也被占用，改 compose 里的端口映射即可）

## 三、部署步骤

```bash
# 1) 本机上传（在 E:/study/code/habit-forge 下，Git Bash 执行）
scp -r deploy root@<SERVER_IP>:/opt/habitforge

# 2) 登录服务器
ssh root@<SERVER_IP>
cd /opt/habitforge

# 3) 配置环境变量
cp .env.example .env
# 生成强随机 JWT 密钥并填入 .env 的 JWT_SECRET：
openssl rand -hex 32
# 同时确认 .env 里的 MYSQL_PASSWORD 与服务器 MySQL root 密码一致

# 4) 构建并启动（用的是预构建产物，几秒即可完成，不占服务器编译资源）
docker compose up -d --build

# 5) 验证
docker compose ps                       # 两个容器都应 Up（backend 约 40 秒后变 healthy）
docker compose logs -f backend          # 看到 "Started HabitForgeApplication" 即成功
curl -s http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"nobody","password":"test123456"}'
# 期望返回 {"code":1002,"message":"用户名或密码错误",...} 说明 前端nginx → 后端 → MySQL 全链路正常
```

浏览器访问 **http://<SERVER_IP>:8081** 即可使用（先注册账号）。

## 四、后续版本更新

```bash
# 本机重新打包后上传覆盖 jar / dist，然后在服务器上：
docker compose up -d --build     # 自动重建并重启容器
```

## 五、⚠️ 安全事项（重要）

1. **MySQL/Redis 弱密码暴露公网**：该服务器 3306 已对外开放且 root 密码为 `123456`，数据库中出现过 `RECOVER_YOUR_DATA_info` 表（公网勒索脚本扫库的典型痕迹）。强烈建议：
   - 立即修改 MySQL root 为强密码（同步更新 `.env`）；
   - 防火墙/安全组将 3306、6379 限制为你的本机 IP 或全部关闭（应用走服务器内网访问即可）；
   - Redis 设置 `requirepass`（需要时后端通过 `SPRING_DATA_REDIS_PASSWORD` 环境变量传入）。
2. **JWT_SECRET 必须替换**：切勿使用 compose 仓库里的任何占位符；密钥泄露 = 任何人可伪造登录态。注意：更换密钥会使所有已登录用户的 token 失效。
3. 部署版已做收敛：后端 8080 不再映射到公网（仅容器网络内可达），所有流量统一走对外的 8081 端口（容器内 nginx 80）。

## 六、修复记录

**2026-08-05 · 登录 403 (Invalid CORS request)**
- 根因:CORS 白名单只允许 `localhost:*`;且 nginx 用 `$host` 转发时丢掉端口号,后端把浏览器带 `Origin: http://<SERVER_IP>:8081` 的同源 POST 误判为跨域并拒绝。
- 修复:① CORS 来源改为可配置(`app.cors.allowed-origin-patterns`,环境变量 `APP_CORS_ALLOWED_ORIGIN_PATTERNS` 可覆盖,prod 默认只放行精确来源 `http://<SERVER_IP>:8081`);② nginx 改用 `$http_host` 保留端口。
- 以后若更换访问域名/端口,记得同步更新 `APP_CORS_ALLOWED_ORIGIN_PATTERNS`(见 `.env.example`)。

**2026-08-05 · 复查后的补充修复(同批上线)**
- **时区**:后端容器加 `TZ=Asia/Shanghai`。容器默认 UTC,会导致每天北京时间 0–8 点打卡被"不能是未来的日子"拒绝、今日列表与连续天数边界错乱。
- **token 过期无法重新登录**:前端 401 时现在会同时清掉 pinia 持久化登录态(localStorage `habitforge-user`),修复 token 过期后 /login ↔ /home 无限重定向、只能手动清浏览器数据的问题。
- **发版白屏**:nginx 为 index.html 加 `no-cache`、为 /assets/ 加一年长缓存,避免用户缓存旧入口导致白屏。
- **CORS 收窄**:生产白名单从 `IP:全端口 + localhost` 收紧为精确来源;关闭 `allowCredentials`(项目用 Bearer token,不依赖 Cookie)。

## 七、常见问题

1. **backend 容器反复重启**：`docker compose logs backend` 看报错。常见为 MySQL 密码错误（Access denied）或 JWT_SECRET 未设置（compose 会直接拒绝启动并提示）。
2. **页面打开 502**：后端尚未就绪，等待 healthcheck 变 healthy（首次约 30–60 秒）。
3. **8081 端口也被占用**：改 compose 的端口映射为其他空闲端口 `"XXXX:80"`；**同时**把环境变量 `APP_CORS_ALLOWED_ORIGIN_PATTERNS` 改为 `http://<SERVER_IP>:XXXX`（否则浏览器 POST 会被 CORS 拒绝）。若想复用服务器现有 nginx：把 `dist/` 拷给它，在其站点配置中加上 `nginx.conf` 里的缓存规则、SPA fallback 与 `/api/` 反代（反代目标改为 `http://127.0.0.1:8080` 时需同时把 compose 里 backend 的 `expose` 改为 `ports: ["127.0.0.1:8080:8080"]`）。
4. **容器访问不到宿主机 MySQL**：确认 Docker 版本 ≥ 20.10（`host-gateway` 特性），或在 compose 中把 `host.docker.internal` 替换为服务器内网 IP。
