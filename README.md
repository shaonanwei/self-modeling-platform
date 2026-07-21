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

不要把数据库账号或密码写入受 Git 跟踪的配置。建议在启动后端的同一个 PowerShell 终端中，通过环境变量提供本地应用账号：

```powershell
$mysqlCredential = Get-Credential -Message 'Local MySQL application account'
$postgresCredential = Get-Credential -Message 'Local PostgreSQL application account'

$env:MYSQL_URL = 'jdbc:mysql://localhost:3306/self_modeling'
$env:MYSQL_USERNAME = $mysqlCredential.UserName
$env:MYSQL_PASSWORD = $mysqlCredential.GetNetworkCredential().Password
$env:POSTGRES_URL = 'jdbc:postgresql://localhost:5432/test?currentSchema=public'
$env:POSTGRES_USERNAME = $postgresCredential.UserName
$env:POSTGRES_PASSWORD = $postgresCredential.GetNetworkCredential().Password
```

也可以复制 `backend/config/datasource-local.example.yml` 为 `backend/config/datasource-local.yml`，再仅在本机填写配置。该本地文件已被 Git 忽略，不得提交。数据库账号应使用最小权限的应用账号，不要使用数据库所有者或管理员账号执行用户 SQL。

曾经写入仓库或 Git 历史的数据库凭证必须单独轮换。当前代码加固不会自动完成凭证轮换，生产部署前仍需确认旧凭证已经失效。

### 3. 启动后端

```powershell
Set-Location backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`

### 5. 默认账号

- 用户名: `admin`
- 密码: `admin123`

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

如果原终端已经关闭或服务在后台运行，可在项目根目录执行以下命令。脚本只会停止命令行中包含当前项目根目录的 8080/5173 监听进程；其他程序即使占用了相同端口也不会被停止。

```powershell
$projectRoot = (Resolve-Path .).Path
$ports = 8080, 5173
$connections = Get-NetTCPConnection -State Listen -ErrorAction Stop |
    Where-Object { $_.LocalPort -in $ports }
$processIds = $connections |
    Select-Object -ExpandProperty OwningProcess -Unique

foreach ($processId in $processIds) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId"
    $belongsToProject = $process.CommandLine -and
        $process.CommandLine.IndexOf(
            $projectRoot,
            [System.StringComparison]::OrdinalIgnoreCase
        ) -ge 0

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

### 4. 启动前端

再打开一个新的 PowerShell 终端，在项目根目录执行：

```powershell
Set-Location frontend
npm run dev -- --host 127.0.0.1
```

日志出现 `VITE ... ready` 表示前端已经就绪，访问地址为 `http://127.0.0.1:5173/`。正常重启不需要重复执行 `npm install`；只有首次安装或依赖发生变化时才需要重新安装依赖。

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

## 项目结构

```
self-modeling-platform/
├── backend/                          # Spring Boot 后端
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

### 认证 `/api/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/login` | 用户登录 |
| POST | `/logout` | 用户登出 |
| GET | `/captcha` | 获取验证码 |
| GET | `/userinfo` | 获取当前用户信息 |

### 模型 `/api/models`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 分页查询模型列表 |
| GET | `/{id}` | 获取模型详情 |
| POST | `/` | 创建模型 |
| PUT | `/{id}` | 更新模型 |
| DELETE | `/{id}` | 删除模型 |
| POST | `/{id}/copy` | 复制模型 |

### 步骤 `/api/models/{modelId}/steps`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取步骤列表 |
| POST | `/` | 添加步骤 |
| PUT | `/{stepId}` | 更新步骤 |
| DELETE | `/{stepId}` | 删除步骤 |
| POST | `/execute/{stepId}` | 执行步骤 |

### 元数据 `/api/metadata`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/datasources` | 获取数据源列表 |
| GET | `/tables` | 获取表列表 |
| GET | `/tables/{tableName}/columns` | 获取字段列表 |

## 核心功能

### 步骤类型

| 类型 | 说明 |
|------|------|
| start | 开始节点 |
| end | 结束节点 |
| task | 任务节点 |
| gateway | 网关节点（条件分支） |
| subprocess | 子流程节点 |

### Token 配置

- Token 有效期：30 天
- 活跃超时：30 分钟无操作自动过期
- 支持多端同时登录

## License

MIT
