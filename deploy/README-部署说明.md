# HabitForge 部署说明（目标服务器：<SERVER_IP>）

构建日期：2026-08-05 · 构建产物已通过静态验证（JAR 结构 / dist 完整性 / 无硬编码后端地址）

> ⚠️ **2026-09-13 追加：本目录里的预构建产物已过期，不要直接部署。**
> `habitforge-backend.jar` 与 `dist/`（以及给传输用的 `dist.zip`）仍是 **2026-08-05 / 08-06** 的构建，
> **不含**随后完成的日记/心得、学习模块（P0/P1）与 AI 今日安排（P2）。`Dockerfile.backend` 是直接
> `COPY habitforge-backend.jar`，前端同理 `COPY dist`——**在服务器上不会重新编译**，
> 照本目录现有产物部署只会得到一个没有新功能的旧版本。
> 部署前必须先在本机重新构建，见第三节第 0 步；`dist.zip` 是冗余的手工副本，建议直接删掉，
> 以后只保留 `dist/` 一个来源。

## 一、部署包内容

| 文件 | 说明 |
|------|------|
| `habitforge-backend.jar` | Spring Boot 可执行 fat jar（Java 17，含 prod 配置）。**需本机 `mvn package` 重新生成**，仓库里那份是 2026-08-05 的旧包 |
| `dist/` | 前端生产构建（Vite）。**需本机 `npm run build` 重新生成**，仓库里那份是 2026-08-06 的旧构建 |
| `upgrade_2026-08_journal.sql`<br>`upgrade_2026-09_study.sql`<br>`upgrade_2026-10_ai_plan.sql` | 增量脚本（日记 4 张 + 学习 8 张 + AI 4 张），**不在本目录里**——只在 `habitforge-backend/src/main/resources/db/` 维护一份，按第三节第 1 步单独上传 |
| `nginx.conf` | nginx 站点配置（SPA history 路由 fallback + `/api/` 反代到后端） |
| `Dockerfile.backend` | 精简版：直接 COPY 预构建 JAR，不在服务器上跑 Maven |
| `Dockerfile.frontend` | 精简版：直接 COPY dist 到 nginx，不在服务器上跑 npm |
| `docker-compose.yml` | 部署专用编排：**不含 mysql/redis 容器**。服务器上的 MySQL 是独立运行的 `mysql8` 容器（在 `workspace_default` 网络），本编排不重复启动，避免 3306/6379 端口与数据目录冲突 |
| `.env.example` | 环境变量模板（对应本目录的 docker-compose.yml，变量名与根目录那份**不同**） |
| `dist.zip` | 2026-08-05 的冗余副本，已过期，建议删除（部署只读 `dist/`） |

## 二、服务器前提

- [x] MySQL 8 运行于 3306（独立容器 `mysql8`，非本编排管理；数据库升级见第三节 3.1）
- [x] Redis 运行于 6379
- [ ] 已安装 Docker 与 compose 插件（`docker compose version` 验证）
- [ ] 服务器 **8081** 端口空闲且防火墙放行（80 已被服务器上现有 nginx 占用，故对外用 8081；如 8081 也被占用，改 compose 里的端口映射即可）

## 三、部署步骤

```bash
# 0) 本机重新构建产物（在 E:/study/code/habit-forge 下执行）—— 必做，见第一节顶部警告
cd habitforge-backend && mvn -q clean package -DskipTests && cd ..
# 产物：habitforge-backend/target/habitforge-backend.jar
cd habitforge-frontend && npm run build && cd ..
# 产物：habitforge-frontend/dist
# 然后把两份产物拷进 deploy/（deploy_journal.py 会自动做这一步；手工部署则手动覆盖）：
cp habitforge-backend/target/habitforge-backend.jar deploy/habitforge-backend.jar
rm -rf deploy/dist && cp -r habitforge-frontend/dist deploy/dist

# 1) 本机上传（在 E:/study/code/habit-forge 下，Git Bash 执行）
scp -r deploy root@<SERVER_IP>:/opt/habitforge
# 增量 SQL 只在 habitforge-backend/src/main/resources/db/ 一处维护（deploy/ 里不放副本，
# 免得两处各改一份而漂移），所以单独传上去供 3.1 步引用：
scp habitforge-backend/src/main/resources/db/upgrade_*.sql root@<SERVER_IP>:/opt/habitforge/deploy/

# 2) 登录服务器（部署目录是 /opt/habitforge/deploy，.env 与 compose 都在这一层）
ssh root@<SERVER_IP>
cd /opt/habitforge/deploy
ls docker-compose.yml .env.example        # 确认传上来了

# 3) 配置环境变量
cp .env.example .env
# 生成强随机 JWT 密钥并填入 .env 的 JWT_SECRET：
openssl rand -hex 32
# 同时确认 .env 里的 MYSQL_PASSWORD 与服务器 MySQL 密码一致
# 以及 APP_CORS_ALLOWED_ORIGIN_PATTERNS 指向真实访问地址、MINIO_SECRET_KEY 已填

# 3.1) 数据库升级 —— 存量库不会自动跑 initdb 脚本，必须手工执行。
#      按文件名顺序把三个都跑一遍即可：每个脚本的 CREATE TABLE 都带 IF NOT EXISTS，
#      已经建过的表会自动跳过，重复执行安全。**顺序不能颠倒**——AI 那 4 张表的外键
#      引用 subjects/chapters，得先跑 study 脚本才有这两张表。
#      先把 .env 读进当前 shell —— 必须在这条之前，否则 $MYSQL_PASSWORD 是空的：
set -a && . ./.env && set +a
#      看一眼现状，便于对照升级结果：
docker exec mysql8 mysql -u"${MYSQL_USER:-root}" -p"$MYSQL_PASSWORD" habitforge -e 'SHOW TABLES;'
for f in upgrade_2026-08_journal.sql upgrade_2026-09_study.sql upgrade_2026-10_ai_plan.sql; do
  echo ">>> $f"
  docker exec -i mysql8 mysql -u"${MYSQL_USER:-root}" -p"$MYSQL_PASSWORD" habitforge < "$f" || exit 1
done
# 校验：升级后应能看到 journal 系列 4 张 + study 8 张 + plan 4 张（累计 24 张）
docker exec mysql8 mysql -u"${MYSQL_USER:-root}" -p"$MYSQL_PASSWORD" habitforge \
  -e 'SHOW TABLES;'

# 4) 构建并启动（用的是预构建产物，几秒即可完成，不占服务器编译资源）
docker compose up -d --build

# 5) 验证
docker compose ps                       # 两个容器都应 Up（backend 约 40 秒后变 healthy）
docker compose logs -f backend          # 看到 "Started HabitForgeApplication" 即成功
curl -s http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"nobody","password":"test123456"}'
# 期望返回 {"code":1002,"message":"用户名或密码错误",...} 说明 前端nginx → 后端 → MySQL 全链路正常
# 再验新模块路由已生效（未带 token 应为 401，而不是 404）：
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/api/v1/study/overview
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/api/v1/plans/today
```

### 部署后确认 AI 是否真的接上了

`AI_API_KEY` 只在 `.env` 里，**不进任何提交**。确认它已被容器拿到：

```bash
docker exec habitforge-backend printenv AI_ENABLED AI_MODEL   # 应打印 true / deepseek-v4-flash
# 不要在终端回显 key 本身。只确认非空：
docker exec habitforge-backend sh -c 'test -n "$AI_API_KEY" && echo AI_API_KEY=SET || echo AI_API_KEY=EMPTY'
```

页面上的表现：设了 key → 「AI 生成今日安排」正常出块；key 为空 → 返回 **6001「AI 服务未启用」**；
key 无效/欠费 → **6004「AI 服务调用失败」**。两者含义不同，别混。

浏览器访问 **http://<SERVER_IP>:8081** 即可使用（先注册账号）。

## 四、后续版本更新

```bash
# 本机重新打包（mvn package / npm run build）→ 拷进 deploy/ → 上传覆盖 jar / dist，然后在服务器上：
docker compose up -d --build     # 自动重建并重启容器
```

若本次更新带了新的增量 SQL（`upgrade_*.sql`），务必**先执行 SQL 再重建容器**：旧 jar 遇到新表只是多出来的表，
不影响运行；反过来新 jar 先上、表还没建，新接口会直接报错。顺序与第三节 3.1 一致。

`deploy_journal.py` 已把「拷产物 → 传 SQL → 整目录同步 → 重建 → 冒烟」串成一条命令，
但它把 SQL 文件名写死为 `upgrade_2026-08_journal.sql`，跑新版本前需要改 `SQL_NAME`（或另建脚本）。

## 五、⚠️ 安全事项（重要）

1. **MySQL/Redis 弱密码暴露公网**：该服务器 3306 曾对外开放，数据库中出现过 `RECOVER_YOUR_DATA_info` 表（公网勒索脚本扫库的典型痕迹）。强烈建议：
   - 用强密码（密码只写进服务器上的 `.env`，**不要写进本文件或任何提交**——此处原先记过明文密码，已删除）；
   - 防火墙/安全组将 3306、6379 限制为你的本机 IP 或全部关闭（应用走服务器内网访问即可）；
   - Redis 设置 `requirepass` 后，把密码填进服务器 `.env` 的 `SPRING_DATA_REDIS_PASSWORD`（compose 已转发该变量，见 `deploy/docker-compose.yml`）。
2. **JWT_SECRET 必须替换**：切勿使用 compose 仓库里的任何占位符；密钥泄露 = 任何人可伪造登录态。注意：更换密钥会使所有已登录用户的 token 失效。
3. **AI_API_KEY 同样是密钥**：只放服务器 `.env`，不要贴进聊天/工单/提交。若曾以明文外泄过，去 DeepSeek 控制台轮换一把。它泄露的后果是按你的账单向调用方计费。
4. 部署版已做收敛：后端 8080 不再映射到公网（仅容器网络内可达），所有流量统一走对外的 8081 端口（容器内 nginx 80）。

## 六、修复记录

**2026-09-13 · 接入 AI 今日安排（DeepSeek）并修好部署链路**
- **供应商切换**：AI 从阿里云百炼 TokenPlan（`qwen3.8-flash`）切到 **DeepSeek 的 Anthropic 兼容端点**
  （`https://api.deepseek.com/anthropic`，模型 `deepseek-v4-flash`）——TokenPlan 周配额耗尽。
  两者配置形状一致，换回只需改 `AI_BASE_URL` / `AI_MODEL`。
- **必须关闭思考**：DeepSeek v4 flash 默认输出 thinking 块，而 spring-ai 1.1.8 的 `AnthropicChatModel`
  会为每个 content 块各生成一个 `Generation`、`getResult()` 只取第 0 个；thinking 块排在 text 之前，
  于是取到的是**思考文本**，JSON 反序列化必然失败 → 每次生成都误报 6005。
  且思考 token 计入 `max_tokens`（实测重上下文可吃尽额度、正文被截断），故 `thinking.type=disabled`
  并把 `max-tokens` 提到 4096。判据：**响应应当只有 1 个 Generation**。
- **本编排原先完全没有 AI 通道**：backend 的 `environment` 里没有任何 `AI_*`，key 根本进不了容器。
  已补 `AI_API_KEY` / `AI_ENABLED` / `AI_BASE_URL` / `AI_MODEL` 四项转发。
  其中后两项原先若只写在 `.env` 里会**静默无效**（compose 未转发），现已真的透传。
- **凭证缺失的语义**：`AI_API_KEY` 用 `"${AI_API_KEY:-}"`（引号不能省，否则插值成空值后 YAML 解析为 null，
  而 compose 对 null 的处理是"从宿主机环境取同名变量"，行为不确定）。留空 = 返 6001「AI 服务未启用」，
  填错 = 返 6004「AI 服务调用失败」，不再出现无声的 401。因此 `AI_ENABLED=false` 是真正可用的紧急开关。
- **`.env.example` 与 compose 对齐**：模板原先按**根目录**那份 compose 写（`MYSQL_ROOT_PASSWORD`），
  而本目录的 compose 要的是 `MYSQL_USER`/`MYSQL_PASSWORD` 且硬要求 `APP_CORS_ALLOWED_ORIGIN_PATTERNS`，
  照抄模板部署必起不来。已改为与本目录 compose 一致。

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
