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

修改 `backend/src/main/resources/datasource.yml`：

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/self_modeling
          username: root
          password: your_password
```

### 3. 启动后端

```bash
cd backend
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
