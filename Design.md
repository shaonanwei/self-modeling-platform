# Self-Service Modeling Platform 项目设计文档

## 1. 项目概述

**项目名称**：自助建模平台（Self-Service Modeling Platform）  
**版本**：1.0.0  
**定位**：面向业务人员的可视化 SQL 建模平台，支持通过画布拖拽方式构建数据查询模型，自动生成 SQL，并提供元数据浏览、多数据源管理、智能推荐等能力。

---

## 2. 技术架构总览

```
┌─────────────────────────────────────────────────────────┐
│                     前端 (Vue 3 + Vite)                  │
│  Vue Router · Pinia · Element Plus · Vue Flow · Monaco   │
│  Axios · VueUse · vuedraggable · TypeScript              │
├─────────────────────────────────────────────────────────┤
│              Vite Dev Proxy (/api → :8080)               │
├─────────────────────────────────────────────────────────┤
│                  后端 (Spring Boot 2.7.18)                │
│  Spring Security · JWT · MyBatis-Plus · JSqlParser       │
│  Hutool · Spring DevTools · Validation                   │
├──────────────┬──────────────┬────────────────────────────┤
│   SQLite     │  PostgreSQL  │    Hive (可选)              │
│  (主数据源)   │  (业务数据源) │   (大数据数据源)            │
└──────────────┴──────────────┴────────────────────────────┘
```

---

## 3. 后端技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **框架** | Spring Boot | 2.7.18 | 核心框架 |
| **安全** | Spring Security | 5.7.x | 认证与授权 |
| **安全** | JJWT | 0.11.5 | JWT Token 生成与解析 |
| **ORM** | MyBatis-Plus | 3.5.5 | 持久层框架，分页插件 |
| **SQL解析** | JSqlParser | 4.2 | SQL 语法解析与生成 |
| **工具库** | Hutool | 5.8.23 | 通用工具集 |
| **数据库** | SQLite JDBC | 3.44.1.0 | 主数据源（系统元数据） |
| **数据库** | PostgreSQL JDBC | 42.6.0 | 业务数据源 |
| **数据库** | Hive JDBC | 3.1.3（可选） | 大数据数据源 |
| **校验** | Spring Validation | - | 参数校验 |
| **热部署** | Spring DevTools | - | 开发热重载 |
| **构建** | Maven | - | 项目构建管理 |

### 后端包结构

```
com.selfmodeling/
├── config/                    # 配置层
│   ├── CorsConfig.java        # CORS 跨域配置
│   ├── DataSourceConfig.java  # 多数据源配置（SQLite/PostgreSQL/Hive）
│   ├── MyBatisPlusConfig.java # MyBatis-Plus 分页+自动填充
│   └── SecurityConfig.java    # Spring Security + JWT 过滤器链
├── controller/                # 控制层（REST API）
│   ├── AuthController.java    # 认证接口
│   ├── MetadataController.java# 元数据接口
│   ├── ModelController.java   # 模型管理接口
│   └── SqlController.java     # SQL 操作接口
├── dto/                       # 数据传输对象
│   ├── Result.java            # 统一响应封装
│   ├── PageResult.java        # 分页结果
│   ├── QueryConfig.java       # 查询配置（含画布配置）
│   ├── CanvasTableConfig.java # 画布表节点配置
│   ├── CanvasJoinConfig.java  # 画布关联配置
│   ├── WhereCondition.java    # WHERE 条件结构
│   ├── SmartRecommendResult.java # 智能推荐结果
│   ├── StepTreeResult.java    # 步骤树结构
│   ├── TableMeta/DTO.java     # 表/字段元信息
│   ├── DataSourceInfo.java    # 数据源信息
│   └── ...
├── entity/                    # 实体层（数据库映射）
│   ├── ModelInfo.java         # 模型信息
│   ├── ModelStep.java         # 模型步骤
│   └── SysUser.java           # 系统用户
├── exception/                 # 异常处理
│   └── GlobalExceptionHandler.java # 全局异常处理器
├── filter/                    # 过滤器
│   └── JwtAuthenticationFilter.java # JWT 认证过滤器
├── mapper/                    # MyBatis Mapper
│   ├── ModelInfoMapper.java
│   ├── ModelStepMapper.java
│   └── SysUserMapper.java
├── request/                   # 请求对象
│   ├── LoginRequest.java
│   └── InsertStepRequest.java
├── service/                   # 服务层
│   ├── AuthService.java / impl/
│   ├── MetadataService.java / impl/
│   ├── ModelService.java / impl/
│   └── SqlService.java / impl/
├── utils/                     # 工具类
│   └── JwtUtils.java          # JWT 工具
└── SelfModelingApplication.java # 启动类
```

---

## 4. 前端技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **框架** | Vue | 3.4.0 | 渐进式前端框架 |
| **构建** | Vite | 5.0.8 | 前端构建工具 |
| **语言** | TypeScript | 5.3.3 | 类型安全 |
| **路由** | Vue Router | 4.2.5 | SPA 路由管理 |
| **状态管理** | Pinia | 2.1.7 | 响应式状态管理 |
| **UI组件** | Element Plus | 2.4.4 | UI 组件库 |
| **图标** | @element-plus/icons-vue | 2.3.1 | 图标库 |
| **流程图** | @vue-flow/core | 1.34.0 | 画布/流程图渲染 |
| **流程图扩展** | @vue-flow/background, controls, minimap | - | 画布背景/控件/小地图 |
| **代码编辑** | Monaco Editor | 0.45.0 | SQL 编辑器 |
| **HTTP** | Axios | 1.6.2 | HTTP 请求 |
| **工具** | @vueuse/core | 14.3.0 | 组合式工具集 |
| **拖拽** | vuedraggable | 4.1.0 | 拖拽排序 |

### 前端目录结构

```
frontend/src/
├── api/                       # API 请求封装
│   ├── authApi.ts             # 认证 API
│   ├── metadataApi.ts         # 元数据 API
│   ├── modelApi.ts            # 模型 API
│   └── sqlApi.ts              # SQL API
├── components/                # 组件
│   ├── metadata/
│   │   └── MetadataViewer.vue # 元数据浏览器
│   ├── model/                 # 模型相关组件
│   │   ├── ModelList.vue      # 模型列表
│   │   ├── ModelEditor.vue    # 模型编辑器
│   │   ├── ModelFlow.vue      # 模型流程图
│   │   ├── ModelDialog.vue    # 模型弹窗
│   │   ├── StepCard.vue       # 步骤卡片
│   │   ├── StepEditDialog.vue # 步骤编辑弹窗
│   │   ├── InsertIndicator.vue# 插入指示器
│   │   ├── JoinEditor.vue     # JOIN 编辑器
│   │   ├── ConditionEditor.vue# 条件编辑器
│   │   ├── TableFieldSelector.vue # 表字段选择器
│   │   ├── SqlPreviewDialog.vue   # SQL 预览弹窗
│   │   └── FlowChart.vue      # 流程图
│   └── queryEditor/           # 查询编辑器组件
│       ├── QueryEditor.vue    # 查询编辑器主组件
│       ├── CanvasArea.vue     # 画布区域
│       ├── TableNode.vue      # 表节点
│       ├── MetadataPanel.vue  # 元数据面板
│       ├── PropertyPanel.vue  # 属性面板
│       ├── SqlEditor.vue      # SQL 编辑器
│       ├── SqlResultViewer.vue# SQL 结果查看器
│       ├── WhereConditionEditor.vue # WHERE 条件编辑器
│       ├── GroupByPanel.vue   # GROUP BY 面板
│       └── OrderByPanel.vue   # ORDER BY 面板
├── composables/               # 组合式函数
│   ├── useCopy.ts             # 复制功能
│   └── useStepTypes.ts        # 步骤类型
├── constants/
│   └── stepTypes.ts           # 步骤类型常量
├── layouts/
│   └── MainLayout.vue         # 主布局
├── pages/login/
│   └── LoginPage.vue          # 登录页
├── router/
│   └── index.ts               # 路由配置
├── stores/
│   ├── authStore.ts           # 认证状态
│   └── queryEditorStore.ts    # 查询编辑器状态
├── styles/
│   └── theme.css              # 主题样式
├── types/
│   ├── model.ts               # 模型类型定义
│   ├── metadata.ts            # 元数据类型
│   └── queryEditor.ts         # 查询编辑器类型
├── utils/
│   ├── auth.ts                # Token 管理
│   ├── request.ts             # Axios 封装
│   ├── formatters.ts          # 格式化工具
│   └── sqlConverter.ts        # SQL 转换工具
├── App.vue
└── main.ts
```

---

## 5. 数据库设计

### 5.1 主数据源 - SQLite（系统元数据）

**sys_user（系统用户表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 自增主键 |
| username | TEXT UNIQUE | 用户名 |
| password | TEXT | 密码（BCrypt 加密） |
| nickname | TEXT | 昵称 |
| email | TEXT | 邮箱 |
| phone | TEXT | 手机号 |
| avatar | TEXT | 头像 |
| status | INTEGER | 状态（1-启用） |
| creator / updater | TEXT | 创建/更新人 |
| create_time / update_time | TEXT | 创建/更新时间 |
| deleted | INTEGER | 逻辑删除（0-未删，1-已删） |

**model_info（模型信息表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 自增主键 |
| model_code | TEXT UNIQUE | 模型编码 |
| model_name | TEXT | 模型名称 |
| model_desc | TEXT | 模型描述 |
| model_type | TEXT | 模型类型 |
| data_source | TEXT | 数据源标识（默认 sqlite） |
| status | INTEGER | 状态 |
| version | INTEGER | 版本号 |
| creator / updater | TEXT | 创建/更新人 |
| create_time / update_time | TEXT | 创建/更新时间 |
| deleted | INTEGER | 逻辑删除 |

**model_step（模型步骤表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 自增主键 |
| model_id | INTEGER | 所属模型ID |
| step_code | TEXT UNIQUE | 步骤编码 |
| step_name | TEXT | 步骤名称 |
| step_desc | TEXT | 步骤描述 |
| step_type | TEXT | 步骤类型（start/end/task/gateway/subprocess） |
| sort_order | INTEGER | 排序号 |
| step_config | TEXT | 步骤配置（JSON，含 QueryConfig） |
| parent_step_id | INTEGER | 父步骤ID（支持嵌套） |
| condition_expr | TEXT | 条件表达式 |
| timeout_seconds | INTEGER | 超时时间 |
| retry_count | INTEGER | 重试次数 |
| creator / updater | TEXT | 创建/更新人 |
| create_time / update_time | TEXT | 创建/更新时间 |
| deleted | INTEGER | 逻辑删除 |

### 5.2 多数据源架构

| 数据源 | 用途 | 连接方式 | 条件加载 |
|--------|------|----------|----------|
| **SQLite** | 系统元数据（主数据源） | `jdbc:sqlite:selfmodeling.db` | 始终加载（@Primary） |
| **PostgreSQL** | 业务数据查询 | `jdbc:postgresql://localhost:5432/test` | `@ConditionalOnProperty` 按需加载 |
| **Hive** | 大数据查询 | `jdbc:hive2://localhost:10000/default` | `@ConditionalOnProperty` 按需加载 |

每个数据源对应独立的 `JdbcTemplate`，通过 `@Qualifier` 注入到 Service 层。

---

## 6. 核心业务模块

### 6.1 认证模块（Auth）

- **认证方式**：JWT 无状态认证
- **Token 类型**：AccessToken（30分钟） + RefreshToken（7天）
- **密码加密**：BCrypt
- **过滤器链**：`JwtAuthenticationFilter` → 解析 Bearer Token → 设置 SecurityContext

### 6.2 模型管理模块（Model）

- 模型 CRUD + 分页查询
- 模型步骤 CRUD + 排序/插入/交换
- 步骤树结构（支持嵌套子步骤）
- 模型复制、状态管理

**步骤类型**：

| 类型 | 说明 | 颜色 |
|------|------|------|
| start | 开始节点 | 绿色 |
| end | 结束节点 | 红色 |
| task | 任务节点 | 蓝色 |
| gateway | 网关节点 | 橙色 |
| subprocess | 子流程节点 | 灰色 |

### 6.3 元数据模块（Metadata）

- 多数据源元数据浏览（表/字段/索引/主键）
- 表搜索（按表名/字段名模糊匹配）
- 数据源连接状态检查
- 表数据预览与行数统计

### 6.4 SQL 模块（Sql）

- **SQL 校验**：仅允许 SELECT 语句
- **SQL 执行**：只读执行，限行返回
- **SQL ↔ 画布双向转换**：
  - `parseSqlToCanvas`：SQL → 画布配置（JSqlParser 解析）
  - `generateSqlFromCanvas`：画布配置 → SQL 语句
- **智能推荐**：
  - 关联推荐（基于字段名/类型匹配）
  - 聚合推荐
  - 条件推荐
- **外键关联关系**查询

---

## 7. API 接口设计

### 7.1 认证接口 `/api/v1/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/login` | 用户登录 |
| POST | `/logout` | 用户登出 |
| POST | `/refresh` | 刷新 Token |
| GET | `/userinfo` | 获取当前用户信息 |

### 7.2 模型接口 `/api/v1`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/models` | 分页查询模型列表 |
| GET | `/models/{id}` | 获取模型详情 |
| POST | `/models` | 创建模型 |
| PUT | `/models/{id}` | 更新模型 |
| DELETE | `/models/{id}` | 删除模型 |
| PATCH | `/models/{id}/status` | 更新模型状态 |
| POST | `/models/{id}/copy` | 复制模型 |
| GET | `/models/{modelId}/steps` | 获取步骤列表 |
| GET | `/models/{modelId}/steps/{stepId}` | 获取步骤详情 |
| POST | `/models/{modelId}/steps` | 添加步骤 |
| POST | `/models/{modelId}/steps/insert` | 插入步骤 |
| PUT | `/models/{modelId}/steps/{stepId}` | 更新步骤 |
| DELETE | `/models/{modelId}/steps/{stepId}` | 删除步骤 |
| PATCH | `/models/{modelId}/steps/{stepId}/reorder` | 重排步骤 |
| PATCH | `/models/{modelId}/steps/{stepId}/swap` | 交换步骤 |
| GET | `/models/{modelId}/steps/tree` | 获取步骤树 |

### 7.3 元数据接口 `/api/v1/metadata`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/datasources` | 获取数据源列表 |
| GET | `/datasources/{id}/check` | 检查数据源连接 |
| GET | `/tables` | 获取表列表 |
| GET | `/tables/{tableName}` | 获取表详情 |
| GET | `/tables/{tableName}/columns` | 获取字段列表 |
| GET | `/search` | 搜索元数据 |
| GET | `/tables/{tableName}/count` | 获取表行数 |

### 7.4 SQL 接口 `/api/v1/sql`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/validate` | 校验 SQL |
| POST | `/execute` | 执行 SQL 查询 |
| POST | `/parse` | SQL → 画布配置 |
| POST | `/generate` | 画布配置 → SQL |
| GET | `/smart-recommend` | 智能推荐 |
| GET | `/relations/{tableName}` | 获取表关联关系 |

---

## 8. 安全设计

| 层面 | 方案 |
|------|------|
| **认证** | JWT（HS256签名），AccessToken + RefreshToken 双 Token 机制 |
| **授权** | Spring Security 过滤器链，无状态会话 |
| **密码** | BCrypt 加密存储 |
| **CORS** | `CorsFilter` 允许跨域（开发环境 `*`，生产应限制域名） |
| **SQL注入** | SQL 校验仅允许 SELECT；JdbcTemplate 参数化查询 |
| **异常处理** | `GlobalExceptionHandler` 统一捕获，区分业务异常/认证异常/参数异常 |

---

## 9. 前端路由设计

| 路径 | 组件 | 说明 | 需认证 |
|------|------|------|--------|
| `/login` | LoginPage | 登录页 | 否 |
| `/models` | ModelList | 模型列表 | 是 |
| `/models/:id/edit` | ModelEditor | 模型编辑 | 是 |
| `/models/:id/view` | ModelFlow | 模型流程图 | 是 |
| `/metadata` | MetadataViewer | 元数据管理 | 是 |

路由守卫：未登录自动跳转 `/login`，登录后跳回原页面。

---

## 10. 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1780125847000
}
```

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 参数/业务异常 |
| 401 | 未认证/Token过期 |
| 403 | 无权限 |
| 500 | 服务端异常 |

---

## 11. 关键设计决策

1. **SQLite 作为主数据源**：零部署，适合轻量级场景；PostgreSQL/Hive 按需加载，不影响启动
2. **SQL ↔ 画布双向转换**：基于 JSqlParser 实现 SQL 解析，不支持的语法标记为 `customSqlFragment`
3. **步骤树结构**：`parent_step_id` 支持嵌套子步骤，`sort_order` 控制同级排序
4. **QueryConfig 双模式**：支持 `sql`（纯SQL模式）和 `canvas`（可视化模式），存储在 `step_config` JSON 字段中
5. **条件加载**：PostgreSQL/Hive 数据源使用 `@ConditionalOnProperty`，未配置时不创建 Bean
