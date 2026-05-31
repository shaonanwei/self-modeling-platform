# Self-Service Modeling Platform（自助建模平台）

可视化业务流程建模工具，支持用户通过图形化界面创建、配置和管理业务流程模型。

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| 前端 | Vue 3 + Vite + Element Plus + @vue-flow |
| 后端 | Spring Boot 3.x + MyBatis-Plus + Spring Security |
| 数据库 | PostgreSQL 16 |
| 认证 | JWT (双 Token 机制) |

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.6+
- Node.js 18+
- PostgreSQL 16

### 1. 数据库初始化

```bash
# 创建数据库
createdb self_modeling

# 执行建表脚本（Spring Boot 启动时会自动执行 schema.sql 和 data.sql）
psql -d self_modeling -f backend/src/main/resources/schema.sql
psql -d self_modeling -f backend/src/main/resources/data.sql
```

### 2. 后端启动

```bash
cd backend

# 修改数据库配置
# src/main/resources/application.yml

# 编译运行
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`

### 3. 前端启动

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端默认运行在 `http://localhost:5173`，已配置代理转发到后端。

### 4. 默认账号

- 用户名: `admin`
- 密码: `admin123`

> 注意：首次登录后请立即修改默认密码。

## 项目结构

```
self-modeling-platform/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/selfmodeling/
│   │   ├── config/                   # 配置类（Security、CORS、MyBatis-Plus）
│   │   ├── controller/               # REST API 控制器
│   │   ├── dto/                      # 数据传输对象
│   │   ├── entity/                   # 数据库实体
│   │   ├── exception/                # 全局异常处理
│   │   ├── filter/                   # JWT 认证过滤器
│   │   ├── mapper/                   # MyBatis-Plus Mapper
│   │   ├── request/                  # 请求体 DTO
│   │   ├── service/impl/             # 业务逻辑实现
│   │   └── utils/                    # 工具类（JWT）
│   └── src/main/resources/
│       ├── application.yml           # 应用配置
│       ├── schema.sql                # 建表 SQL
│       └── data.sql                  # 初始化数据
│
└── frontend/                         # Vue 3 前端
    ├── src/
    │   ├── api/                      # API 封装
    │   ├── components/model/         # 建模相关组件
    │   ├── layouts/                  # 布局组件
    │   ├── pages/login/              # 登录页
    │   ├── router/                   # 路由配置
    │   ├── stores/                   # Pinia 状态管理
    │   ├── types/                    # TypeScript 类型定义
    │   └── utils/                    # 工具函数
    ├── package.json
    ├── vite.config.ts
    └── tsconfig.json
```

## API 文档

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/login` | 用户登录 |
| POST | `/api/v1/auth/logout` | 用户登出 |
| POST | `/api/v1/auth/refresh` | 刷新 Token |
| GET | `/api/v1/auth/userinfo` | 获取当前用户信息 |

### 建模

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/models` | 分页查询建模列表 |
| GET | `/api/v1/models/{id}` | 获取建模详情 |
| POST | `/api/v1/models` | 新增建模 |
| PUT | `/api/v1/models/{id}` | 更新建模 |
| DELETE | `/api/v1/models/{id}` | 删除建模 |
| PATCH | `/api/v1/models/{id}/status` | 更新状态 |
| POST | `/api/v1/models/{id}/copy` | 复制建模 |

### 步骤

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/models/{modelId}/steps` | 获取步骤列表 |
| POST | `/api/v1/models/{modelId}/steps` | 末尾添加步骤 |
| POST | `/api/v1/models/{modelId}/steps/insert` | 指定位置插入步骤 |
| PUT | `/api/v1/models/{modelId}/steps/{stepId}` | 更新步骤 |
| DELETE | `/api/v1/models/{modelId}/steps/{stepId}` | 删除步骤（自动重排） |
| PATCH | `/api/v1/models/{modelId}/steps/{stepId}/reorder` | 调整顺序 |
| PATCH | `/api/v1/models/{modelId}/steps/{stepId}/swap` | 交换顺序 |
| GET | `/api/v1/models/{modelId}/steps/tree` | 获取流程树 |

## 核心功能

### 排序策略

- 初始步骤 `sort_order = 1000`，相邻间隔 1000
- 中间插入：新 `sort_order = (前 + 后) / 2`，若为小数则触发批量重排
- 删除后重排：后续步骤 `sort_order -= 1000`

### 步骤类型

| 类型 | 说明 | 颜色 |
|------|------|------|
| start | 开始节点 | 绿色圆形 |
| end | 结束节点 | 红色圆形 |
| task | 任务节点 | 蓝色圆角矩形 |
| gateway | 网关节点（条件分支） | 橙色菱形 |
| subprocess | 子流程节点 | 灰色双线矩形 |

### Token 机制

- Access Token 有效期：30 分钟
- Refresh Token 有效期：7 天
- 请求头格式：`Authorization: Bearer <access_token>`

## 扩展功能建议

- [ ] 步骤版本管理
- [ ] 模型发布/下线流程
- [ ] 导入/导出模型（JSON 格式）
- [ ] 步骤权限控制
- [ ] 模型执行模拟
- [ ] 用户角色权限管理
- [ ] 操作日志审计

## 注意事项

1. 用户密码使用 BCrypt 加密存储
2. 所有删除操作使用逻辑删除
3. 步骤排序操作使用事务保证一致性
4. Token 中不存储敏感信息

## License

MIT
