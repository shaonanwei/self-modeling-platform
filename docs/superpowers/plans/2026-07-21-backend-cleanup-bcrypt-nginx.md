# 后端清理、BCrypt 初始化与 Nginx 同域部署实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 删除不再使用的 Python/Node 后端，实现可登录的 BCrypt 开发管理员，并将浏览器访问统一为 Nginx/Vite 同域代理。

**Architecture:** Spring Boot 是唯一后端；开发环境由 Vite 将相对 `/api` 请求代理到本机 8080，生产环境由 Nginx 同域提供 SPA 并代理 `/api`。Spring Boot 不再发送 CORS 响应头，默认仅监听回环地址。

**Tech Stack:** Spring Boot 4、Hutool BCrypt、JUnit 5、Vue 3/Vite、Nginx

## Global Constraints

- 保留当前 `README.md` 未提交修改并在其基础上更新。
- `frontend` 的 Node.js/Vite 工程必须保留；只删除 `backend` 下的旧 Node/Python 替代后端。
- MySQL、PostgreSQL 当前均为测试账号，本轮不轮换；生产环境仍必须使用独立最小权限账号。
- 只执行针对性测试、必要编译和静态检查；完整回归测试延期到后续功能稳定后。
- 不提交、不推送，由用户在检查差异后决定 Git 操作。

---

### Task 1: BCrypt 初始化管理员

**Files:**
- Create: `backend/src/test/java/com/selfmodeling/SchemaMysqlAdminPasswordTest.java`
- Modify: `backend/src/main/resources/schema-mysql.sql`
- Delete: `backend/src/test/java/com/selfmodeling/GeneratePassword.java`
- Delete: `backend/src/test/java/com/selfmodeling/PasswordGenerator.java`

- [x] 添加测试，从 `schema-mysql.sql` 提取 admin 密码值，断言不是明文、符合 BCrypt 格式且 `BCrypt.checkpw("admin123", hash)` 为真。
- [x] 运行独立断言确认当前明文值失败；Maven 首次运行因依赖下载未进入测试阶段。
- [x] 将初始化 SQL 中的密码替换为固定 BCrypt 哈希，保留 `INSERT IGNORE`，并标注仅供本地开发。
- [x] 运行 `mvn -Dtest=SchemaMysqlAdminPasswordTest test`，确认 1 项测试通过。
- [x] 删除两个会输出密码且功能重复的哈希生成工具。

### Task 2: 删除旧替代后端和生成残留

**Files:**
- Delete: `backend/server.js`
- Delete: `backend/package.json`
- Delete: `backend/package-lock.json`
- Delete: `backend/server.py`
- Delete: `backend/requirements.txt`
- Delete: `backend/restore_db.py`
- Delete: `backend/effective-pom`
- Modify: `.gitignore`

- [x] 删除未被当前 Spring Boot/Vue 工程引用的 Node/Express、Python/Flask 和旧 SQLite 恢复脚本及其依赖清单。
- [x] 删除 Maven 生成的 `effective-pom`，并加入忽略规则防止再次误提交。
- [x] 搜索已删除入口和依赖名称，确认没有活跃引用。

### Task 3: Nginx 同域代理与后端 CORS 移除

**Files:**
- Delete: `backend/src/main/java/com/selfmodeling/config/CorsConfig.java`
- Delete: `backend/src/main/java/com/selfmodeling/config/CorsProperties.java`
- Delete: `backend/src/test/java/com/selfmodeling/config/CorsConfigTest.java`
- Modify: `backend/src/main/resources/application.yml`
- Create: `deploy/nginx.conf.example`

- [x] 移除 Spring CORS 过滤器、类型化属性和对应旧测试。
- [x] 删除 `app.cors.allowed-origins`，并将 Spring Boot 默认监听地址设为 `${SERVER_ADDRESS:127.0.0.1}`。
- [x] 新增 Nginx 示例：同域提供 `frontend/dist`，SPA 路由回退到 `index.html`，保留原 `/api` 路径反向代理至 `127.0.0.1:8080`，不添加 CORS 响应头。

### Task 4: 文档与最小验证

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-20-p0-security-hardening.md`

- [x] README 记录开发默认账号仅限本地、Nginx 同域部署、Spring CORS 已移除和测试账号无需轮换的当前决策。
- [x] P0 文档把凭证轮换从本地测试阻断项改为生产环境隔离要求，并记录 CORS 架构变更。
- [x] 运行 BCrypt 针对性测试与 `mvn -DskipTests compile`。
- [x] 运行 `git diff --check`、引用搜索和 `git status --short`，确认没有修改本地忽略的凭证文件。
