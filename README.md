# Self-Service Modeling Platform（自助建模平台）

面向数据建模与 SQL 探索的全栈应用，支持模型及步骤管理、数据源元数据浏览、可视化 SQL 构建、只读查询预览和 AI SQL 辅助生成。

AI SQL 生成的内容必须经过后端只读安全校验，并由用户手动应用到编辑器；系统不会自动执行或保存 AI 生成的 SQL。

## 主要功能

- 模型管理：创建、编辑、复制、启停和删除模型。
- 步骤编排：管理开始、任务、网关、子流程和结束节点。
- 元数据浏览：查看 MySQL、PostgreSQL、SQLite 数据源中的表、字段和关联信息。
- SQL 编辑：支持 Monaco Editor、画布配置与 SQL 双向转换。
- 安全预览：只允许单条只读 `SELECT`，限制结果行数和查询时间。
- AI SQL 助手：通过通义千问流式生成 SQL，支持安全 Markdown 展示和人工确认应用。

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| 前端 | Vue 3、TypeScript、Vite 5、Element Plus、Vue Flow、Monaco Editor、Pinia |
| 后端 | Java 21、Spring Boot 4、MyBatis、Sa-Token、Dynamic Datasource |
| 数据库 | MySQL（主数据源）、PostgreSQL、SQLite |
| AI | 通义千问 OpenAI 兼容接口、SSE 流式响应、Markdown-it |

## 快速开始

### 1. 环境要求

- JDK 21+
- Maven 3.6.3+
- Node.js 20.19+
- MySQL 8.0+
- PostgreSQL 和 SQLite 按需使用

### 2. 初始化 MySQL

在项目根目录执行：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS self_modeling DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p self_modeling < backend/src/main/resources/schema-mysql.sql
```

第二条命令适用于 Bash 或 Windows Command Prompt。也可以在 MySQL 客户端中选择 `self_modeling` 后执行 `schema-mysql.sql`。

### 3. 配置数据源

复制本地配置模板：

```powershell
Copy-Item backend/config/datasource-local.example.yml backend/config/datasource-local.yml
```

编辑 `backend/config/datasource-local.yml`，至少设置主数据源地址和本机凭证：

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/self_modeling
          username: your_mysql_username
          password: your_mysql_password
```

如需 PostgreSQL，在同一文件中配置 `postgres.url`、`postgres.username` 和 `postgres.password`。不使用模板中的 PostgreSQL 覆盖项时，应将该块删除，不要保留未解析的 `${POSTGRES_*}` 占位符。

`datasource-local.yml` 已被 Git 忽略，仅供本机使用。不要提交该文件，也不要把数据库凭证写入日志、Issue 或提交消息。

### 4. 配置 AI SQL 助手（可选）

AI SQL 默认关闭。复制本地配置模板：

```powershell
Copy-Item backend/config/ai-local.example.yml backend/config/ai-local.yml
```

编辑 `backend/config/ai-local.yml`：

```yaml
app:
  ai:
    sql:
      enabled: true
      api-key: your_qwen_api_key
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model: qwen-plus
```

该文件同样已被 Git 忽略。生产环境应通过密钥管理服务或 `QWEN_API_KEY` 环境变量注入密钥，不要将真实 API Key 提交到仓库。

API Key 所属地域必须与 DashScope 接口地域一致。当前 Qwen 客户端未配置应用层代理，默认直接访问上述地址。修改配置后需要重启后端。

### 5. 启动后端

必须从 `backend` 目录启动，应用才能加载 `./config/*.yml` 本地覆盖文件：

```powershell
Set-Location backend
mvn spring-boot:run
```

后端默认仅监听 `http://127.0.0.1:8080`。

### 6. 启动前端

新开一个终端，在项目根目录执行：

```powershell
Set-Location frontend
npm ci
npm run dev
```

前端默认运行在 `http://localhost:5173`，Vite 会将 `/api` 代理到 `http://localhost:8080`。

### 7. 本地开发账号

全新数据库执行 `schema-mysql.sql` 后，可使用以下账号登录：

- 用户名：`admin`
- 初始密码：`admin123`

初始化脚本仅保存 BCrypt 哈希。该账号只用于本地开发，生产环境必须创建独立管理员并修改默认凭证。

## 配置参考

本地开发可以使用 `backend/config/*.yml` 覆盖文件；部署环境建议使用环境变量。

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `SERVER_ADDRESS` | `127.0.0.1` | 后端监听地址 |
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/test` | MySQL JDBC 地址；按快速开始应改为 `self_modeling` |
| `MYSQL_USERNAME` | 空 | MySQL 用户名 |
| `MYSQL_PASSWORD` | 空 | MySQL 密码 |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/test?currentSchema=public` | PostgreSQL JDBC 地址 |
| `POSTGRES_USERNAME` | 空 | PostgreSQL 用户名 |
| `POSTGRES_PASSWORD` | 空 | PostgreSQL 密码 |
| `AI_SQL_ENABLED` | `false` | 是否启用 AI SQL 助手 |
| `QWEN_API_KEY` | 空 | 通义千问 API Key |
| `QWEN_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | OpenAI 兼容接口基础地址 |
| `QWEN_MODEL` | `qwen-plus` | 使用的模型 |

## AI SQL 工作方式与安全边界

- AI SQL 接口需要有效的 `Authorization` 令牌。
- 同一用户默认只允许一个并发请求，请求超时为 60 秒。
- 模型最多进行 4 轮元数据工具调用，超出后会结束本次生成。
- 工具只提供经过授权的表名、字段、类型、注释、主键和关联信息，不向模型提供业务数据行。
- 模型没有 SQL 执行、保存或数据修改工具。
- 最终 SQL 必须通过后端只读门禁，仅接受一条可解析的 `SELECT`，包括 `WITH ... SELECT`。
- 修改型 DML、DDL、堆叠语句、文件读取、延时及其他危险函数会被拒绝。
- 用户点击应用后只会更新编辑器内容，不会自动保存或执行 SQL。
- AI 回复支持 Markdown，原始 HTML 默认禁用。

## SQL 预览限制

- 同步预览默认返回 50 行，后端最多返回 1000 行。
- 查询超时为 60 秒，数据库连接会设置为只读。
- 校验使用数据库 `EXPLAIN` 或 SQLite `EXPLAIN QUERY PLAN`。
- 生产环境执行用户 SQL 的数据库账号必须为最小权限只读账号，不得使用数据库所有者或管理员账号。

## API 概览

除验证码、登录和预留的刷新入口外，业务接口都需要有效的 `Authorization` 令牌。

| 模块 | 基础路径 | 主要能力 |
|------|----------|----------|
| 认证 | `/api/v1/auth` | 验证码、登录、登出、当前用户信息；`POST /refresh` 当前尚未实现 |
| 模型 | `/api/v1/models` | 模型增删改查、复制、状态变更 |
| 步骤 | `/api/v1/models/{modelId}/steps` | 步骤编排、排序、执行和结果读取 |
| 元数据 | `/api/v1/metadata` | 数据源检查、表和字段查询、数据预览 |
| SQL | `/api/v1/sql` | SQL 校验、预览、解析、生成和关联推荐 |
| AI SQL | `/api/v1/ai/sql` | `POST /chat`，返回 `text/event-stream` 流式事件 |

## 项目结构

```text
self-modeling-platform/
├── backend/
│   ├── config/
│   │   ├── datasource-local.example.yml  # 本地数据源模板
│   │   └── ai-local.example.yml          # 本地 AI 配置模板
│   ├── src/main/java/com/selfmodeling/
│   │   ├── config/                       # Spring 与 AI 配置
│   │   ├── controller/                   # REST 和 SSE 控制器
│   │   ├── dto/                          # 数据传输对象
│   │   ├── entity/                       # 数据库实体
│   │   ├── mapper/                       # MyBatis Mapper
│   │   └── service/                      # 建模、SQL、元数据和 AI 服务
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── datasource.yml
│   │   ├── schema-mysql.sql
│   │   └── mapper/
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── api/                          # API 与 SSE 解码
│   │   ├── components/                   # 建模、查询编辑器和 AI 抽屉
│   │   ├── pages/                        # 页面
│   │   ├── router/                       # 路由
│   │   ├── stores/                       # Pinia 状态
│   │   └── types/                        # TypeScript 类型
│   ├── tests/
│   └── package.json
├── deploy/
│   └── nginx.conf.example                # Nginx 同域代理模板
└── README.md
```

## 构建与验证

后端：

```powershell
Set-Location backend
mvn test
mvn clean package -DskipTests
```

前端：

```powershell
Set-Location frontend
npm ci
npm run test:run
npm run build
```

构建和单元测试通过不代表真实通义千问服务已经连通。启用 AI SQL 后，还需要使用有效 API Key 手工验证登录、元数据查询、流式回复和 SQL 应用流程。

## 部署说明

生产环境建议通过同一域名提供前端页面和 `/api`：

1. 执行 `npm run build` 生成 `frontend/dist`。
2. 以 `deploy/nginx.conf.example` 为模板部署静态资源。
3. 由 Nginx 将 `/api` 反向代理到 Spring Boot。
4. 保持后端监听回环地址或受保护的内网地址，不要将 8080 端口直接暴露到公网。
5. 使用独立的最小权限数据库账号和密钥管理服务，不复用本地开发凭证。

浏览器始终访问相对路径 `/api/v1/...`，因此同域部署不依赖 CORS 响应头。

## Windows 停止与重启

正常停止服务时，在对应终端按 `Ctrl+C`。重新启动前可检查端口：

```powershell
Get-NetTCPConnection -State Listen -LocalPort 5173,8080 -ErrorAction SilentlyContinue |
  Select-Object LocalAddress, LocalPort, OwningProcess
```

如果端口仍被占用，先通过 `Get-Process -Id <PID>` 确认进程归属，再决定是否停止；不要仅根据进程名称批量强制结束 Java 或 Node.js 进程。

确认端口释放后，分别按照“启动后端”和“启动前端”章节重新启动。
