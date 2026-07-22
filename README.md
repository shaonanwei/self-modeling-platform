# Self-Service Modeling Platform（自助建模平台）

可视化业务流程建模工具，支持用户通过图形化界面创建、配置和管理业务流程模型，自动生成 SQL 并执行。

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| 前端 | Vue 3 + Vite + TypeScript + Element Plus + Vue Flow |
| 后端 | Spring Boot 4.x + MyBatis + Sa-Token |
| 数据库 | MySQL（主）+ SQLite + PostgreSQL |
| 认证 | Sa-Token（会话认证） |
| 分页 | PageHelper |

## 快速开始

### 前置条件

- JDK 21+
- Maven 3.6+
- Node.js 18+
- MySQL 8.0+

### 1. 数据库初始化

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE self_modeling DEFAULT CHARACTER SET utf8mb4;"

# 执行建表脚本
mysql -u root -p self_modeling < backend/src/main/resources/schema-mysql.sql
```

### 2. 后端配置

`backend/config/datasource-local.yml` 在克隆仓库后默认不存在；它是仅供本机使用、被 Git 忽略的数据源覆盖文件。先在项目根目录执行：

```powershell
Copy-Item backend/config/datasource-local.example.yml backend/config/datasource-local.yml
```

然后只在本机填写数据库用户名和密码。不要提交该文件，也不要把凭证发送到聊天、终端日志或提交消息中。后端从 `backend` 目录启动时，才会正确加载 `./config/datasource-local.yml`。当前 MySQL 和 PostgreSQL 账号仅用于本地测试，不要求轮换；生产环境必须另建最小权限应用账号，不要使用数据库所有者或管理员账号执行用户 SQL。

### 3. 启动后端

```powershell
Set-Location backend
mvn spring-boot:run
```

后端默认仅监听 `http://127.0.0.1:8080`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`

### 5. 管理员账号

全新本地数据库执行 `schema-mysql.sql` 后，可以使用以下开发账号登录：

- 用户名：`admin`
- 初始密码：`admin123`

初始化脚本保存的是 BCrypt 哈希，不是明文密码。该固定账号仅供本地开发；生产环境必须单独创建管理员并使用独立强密码。`INSERT IGNORE` 不会覆盖已有数据库中的管理员记录。

## Windows 前后端重启

以下命令均使用 PowerShell，并从项目根目录开始执行。重启前请确保 MySQL、PostgreSQL 等外部数据库服务已经启动。

### 1. 检查运行环境

确认 Java、Maven、Node.js 和 npm 均已加入 `PATH`：

```powershell
java -version
mvn -version
node --version
npm --version
```

其中 `java -version` 以及 `mvn -version` 中显示的 Java 版本应为 21 或更高版本。

### 2. 停止旧服务

优先在原来的前端和后端终端中分别按 `Ctrl+C`，等待进程退出。

如果原终端已经关闭或服务在后台运行，可在项目根目录执行以下命令。脚本只会停止已确认属于当前项目的 8080/5173 监听进程；其他程序即使占用了相同端口也不会被停止。Spring Boot 的 Java 进程有时会把 classpath 放在临时 `.argfile` 中，下面的脚本会额外检查该文件，避免误把本项目进程识别为未知进程。

```powershell
$projectRoot = (Resolve-Path .).Path
$ports = 8080, 5173
$connections = Get-NetTCPConnection -State Listen -ErrorAction Stop |
    Where-Object { $_.LocalPort -in $ports }
$processIds = $connections |
    Select-Object -ExpandProperty OwningProcess -Unique

function Test-ProjectProcess {
    param($Process, [string]$ProjectRoot)

    if (-not $Process.CommandLine) {
        return $false
    }

    if ($Process.CommandLine.IndexOf(
        $ProjectRoot,
        [System.StringComparison]::OrdinalIgnoreCase
    ) -ge 0) {
        return $true
    }

    # Spring Boot 可能使用 @C:\...\spring-boot-*.argfile 隐藏完整 classpath。
    $argFileMatch = [regex]::Match(
        $Process.CommandLine,
        '@(?<path>\S+\.argfile)'
    )
    if (-not $argFileMatch.Success) {
        return $false
    }

    $argumentFile = $argFileMatch.Groups['path'].Value
    if (-not (Test-Path -LiteralPath $argumentFile)) {
        return $false
    }

    $argumentText = Get-Content -Raw -LiteralPath $argumentFile
    $normalizedArgumentText = $argumentText -replace '\\\\', '\'
    return $normalizedArgumentText.IndexOf(
        $ProjectRoot,
        [System.StringComparison]::OrdinalIgnoreCase
    ) -ge 0
}

foreach ($processId in $processIds) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId"
    $belongsToProject = Test-ProjectProcess $process $projectRoot

    if ($belongsToProject) {
        Stop-Process -Id $processId -Force
        Write-Host "已停止项目进程 $processId"
    } else {
        Write-Warning "未停止进程 $processId：命令行不属于当前项目"
    }
}
```

如果查询进程时提示权限不足，请使用管理员 PowerShell 重新执行上述兜底命令。不要直接终止未经命令行归属确认的端口进程。

### 3. 启动后端

打开一个新的 PowerShell 终端，在项目根目录执行：

```powershell
Set-Location backend
mvn spring-boot:run
```

日志出现 `Started SelfModelingApplication` 表示后端已经就绪，默认地址为 `http://127.0.0.1:8080`。

如果刚执行过 `git pull`，或本次修改删除、移动了 Java 类（尤其是配置类），先清理旧的 `target` 输出再启动：

```powershell
Set-Location backend
mvn clean spring-boot:run
```

这是为避免 Maven 保留已删除类的 `.class` 文件，导致启动时报已删除类的 `ClassNotFoundException`。普通重启且没有删除或移动 Java 类时，继续使用较快的 `mvn spring-boot:run` 即可。

### 4. 启动前端

再打开一个新的 PowerShell 终端，在项目根目录执行：

```powershell
Set-Location frontend
npm run dev -- --host 127.0.0.1
```

日志出现 `VITE ... ready` 表示前端已经就绪，访问地址为 `http://127.0.0.1:5173/`。正常重启不需要重复执行 `npm install`；只有首次安装或依赖发生变化时才需要重新安装依赖。若后端提示“8080 已被占用”，先再次运行上面的停止脚本，确认旧后端已退出后再启动；不要直接终止未确认归属的 Java 进程。

### 5. 健康检查

两个服务均显示就绪后，在另一个 PowerShell 终端执行：

```powershell
$frontend = Invoke-WebRequest `
    -Uri 'http://127.0.0.1:5173/' `
    -UseBasicParsing `
    -TimeoutSec 15
$backend = Invoke-WebRequest `
    -Uri 'http://127.0.0.1:8080/api/v1/auth/captcha' `
    -UseBasicParsing `
    -TimeoutSec 15

[pscustomobject]@{
    FrontendStatus = $frontend.StatusCode
    FrontendContentType = $frontend.Headers['Content-Type']
    BackendStatus = $backend.StatusCode
    BackendContentType = $backend.Headers['Content-Type']
}
```

预期前端和后端的状态码均为 `200`；前端内容类型为 HTML，后端验证码接口内容类型为 JSON。

## Nginx 同域部署

生产环境使用同一个域名提供前端页面和 `/api`，由 Nginx 将 `/api` 反向代理到 Spring Boot。浏览器始终访问相对路径 `/api/v1/...`，因此不需要 CORS 响应头，后端也不再维护来源白名单。

```powershell
Set-Location frontend
npm ci
npm run build
```

将 `frontend/dist` 的内容部署到 Nginx 站点目录，并以 `deploy/nginx.conf.example` 为模板配置站点。示例默认把 `/api` 代理到同机的 `127.0.0.1:8080`，同时为 Vue Router 提供 `index.html` 回退。

Spring Boot 默认只监听回环地址。如果 Nginx 与后端不在同一主机，可通过 `SERVER_ADDRESS` 改为受保护的内网地址，但不要把 8080 直接暴露到公网。开发环境继续使用 Vite 内置的 `/api` 代理，无需 Nginx。

## 项目结构

```
self-modeling-platform/
├── deploy/
│   └── nginx.conf.example            # Nginx 同域代理模板
├── backend/                          # Spring Boot 后端
│   ├── config/
│   │   └── datasource-local.example.yml # 本地数据源覆盖模板
│   ├── src/main/java/com/selfmodeling/
│   │   ├── config/                   # 配置类
│   │   ├── controller/               # REST API 控制器
│   │   ├── dto/                      # 数据传输对象
│   │   ├── entity/                   # 数据库实体
│   │   ├── exception/                # 全局异常处理
│   │   ├── mapper/                   # MyBatis Mapper
│   │   ├── request/                  # 请求体 DTO
│   │   └── service/impl/             # 业务逻辑实现
│   └── src/main/resources/
│       ├── application.yml           # 应用配置
│       ├── datasource.yml            # 数据源配置
│       ├── schema-mysql.sql          # 建表 SQL
│       └── mapper/                   # MyBatis XML
│
└── frontend/                         # Vue 3 前端
    ├── src/
    │   ├── api/                      # API 封装
    │   ├── components/               # 组件
    │   ├── layouts/                  # 布局
    │   ├── pages/                    # 页面
    │   ├── router/                   # 路由配置
    │   ├── stores/                   # Pinia 状态管理
    │   ├── types/                    # TypeScript 类型
    │   └── utils/                    # 工具函数
    └── package.json
```

## API 文档

所有模型、步骤、元数据和 SQL 数据接口均需要有效的 `Authorization` 令牌；验证码、登录和刷新令牌入口除外。

### 认证 `/api/v1/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/login` | 用户登录 |
| POST | `/api/v1/auth/logout` | 用户登出 |
| GET | `/api/v1/auth/captcha` | 获取验证码 |
| POST | `/api/v1/auth/refresh` | 刷新令牌（接口已预留，当前尚未实现） |
| GET | `/api/v1/auth/userinfo` | 获取当前用户信息 |

### 模型 `/api/v1/models`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/models` | 分页查询模型列表 |
| GET | `/api/v1/models/{id}` | 获取模型详情 |
| POST | `/api/v1/models` | 创建模型 |
| PUT | `/api/v1/models/{id}` | 更新模型 |
| DELETE | `/api/v1/models/{id}` | 删除模型 |
| PATCH | `/api/v1/models/{id}/status` | 更新模型状态 |
| POST | `/api/v1/models/{id}/copy` | 复制模型 |

### 步骤 `/api/v1/models/{modelId}/steps`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/models/{modelId}/steps` | 获取步骤列表 |
| GET | `/api/v1/models/{modelId}/steps/{stepId}` | 获取步骤详情 |
| POST | `/api/v1/models/{modelId}/steps` | 添加步骤 |
| POST | `/api/v1/models/{modelId}/steps/insert` | 在指定步骤后插入步骤 |
| PUT | `/api/v1/models/{modelId}/steps/{stepId}` | 更新步骤 |
| DELETE | `/api/v1/models/{modelId}/steps/{stepId}` | 删除步骤 |
| PATCH | `/api/v1/models/{modelId}/steps/{stepId}/reorder` | 重排步骤 |
| PATCH | `/api/v1/models/{modelId}/steps/{stepId}/swap` | 交换两个步骤 |
| GET | `/api/v1/models/{modelId}/steps/tree` | 获取步骤树 |
| POST | `/api/v1/models/{modelId}/steps/{stepId}/execute` | 异步执行步骤 |
| GET | `/api/v1/models/{modelId}/steps/{stepId}/result` | 分页读取步骤结果 |

### 元数据 `/api/v1/metadata`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/metadata/datasources` | 获取数据源列表 |
| GET | `/api/v1/metadata/datasources/{dataSourceId}/check` | 检查数据源连接 |
| GET | `/api/v1/metadata/tables` | 获取表列表 |
| GET | `/api/v1/metadata/tables/{tableName}` | 获取表详情 |
| GET | `/api/v1/metadata/tables/{tableName}/columns` | 获取字段列表 |
| GET | `/api/v1/metadata/tables/{tableName}/count` | 获取表行数 |
| GET | `/api/v1/metadata/tables/{tableName}/preview` | 预览表数据 |
| GET | `/api/v1/metadata/search` | 搜索表或字段 |

### SQL `/api/v1/sql`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/sql/validate` | 校验只读 SQL |
| POST | `/api/v1/sql/execute` | 同步预览 SQL 结果 |
| POST | `/api/v1/sql/parse` | 将 SQL 解析为画布配置 |
| POST | `/api/v1/sql/generate` | 从画布配置生成 SQL |
| GET | `/api/v1/sql/smart-recommend` | 获取建模推荐 |
| GET | `/api/v1/sql/relations/{tableName}` | 获取表关联关系 |

## 核心功能

### 步骤类型

| 类型 | 说明 |
|------|------|
| start | 开始节点 |
| end | 结束节点 |
| task | 任务节点 |
| gateway | 网关节点（条件分支） |
| subprocess | 子流程节点 |

### SQL 编辑与预览限制

- SQL 编辑器支持输入、最终保存和重新编辑回显；“保存&关闭”允许保存空白草稿。
- 校验、最终保存和执行只接受一条可解析的只读 `SELECT`，包括 `WITH ... SELECT`。
- 修改型 DML、DDL、堆叠语句、文件读取和延时函数会被拒绝。
- 同步预览默认返回 50 行，后端最多返回 1000 行，查询超时为 60 秒。

### Token 配置

- Token 有效期：30 天
- 活跃超时：30 分钟无操作自动过期
- 支持多端同时登录

## 安全发布门禁

当前集中开发阶段可以只运行与改动直接相关的测试和必要编译；新增功能稳定后、准备正式发布时，再执行下面的完整门禁。

每次准备发布时，执行后端完整测试、后端打包和前端生产构建；任何命令失败都应阻止发布：

```powershell
Set-Location backend
mvn test
mvn package -DskipTests

Set-Location ../frontend
npm ci
npm run build
```

使用本地数据源配置或环境变量启动后端后，逐项完成以下 API 冒烟检查：

- [ ] 无令牌请求 `GET /api/v1/auth/captcha` 返回 HTTP 200 和 JSON。
- [ ] 无令牌请求 `POST /api/v1/sql/execute` 返回 HTTP 401。
- [ ] 无令牌请求 `GET /api/v1/metadata/datasources` 返回 HTTP 401。
- [ ] 使用有效账号和验证码请求 `POST /api/v1/auth/login`，业务码为 200，且日志不含密码或密码哈希。
- [ ] 使用有效令牌请求 `POST /api/v1/sql/execute` 执行 `SELECT 1`，业务码为 200。
- [ ] 使用有效令牌提交修改型或堆叠 SQL，返回业务错误且语句未执行。
- [ ] 通过 Nginx 同一域名访问前端和 `/api`，响应不依赖 `Access-Control-Allow-Origin`。
- [ ] 秘密扫描未发现受跟踪的数据库密码、认证密码日志或密码哈希日志。

生产环境必须通过 Nginx 同域代理访问 API，不支持浏览器跨域直连 Spring Boot。当前 MySQL 和 PostgreSQL 本地凭证属于测试账号，不作为轮换阻断项；生产部署必须使用与测试环境隔离的最小权限账号。执行用户 SQL 的数据库账号必须只读，不得使用数据库所有者或管理员账号。

## License

MIT
