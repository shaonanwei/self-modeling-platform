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
│                  后端 (Spring Boot 4.0.6)                │
│  Sa-Token · MyBatis · PageHelper · Druid                 │
│  Dynamic DataSource · Validation                         │
├──────────────┬──────────────┬────────────────────────────┤
│   MySQL      │  SQLite      │    PostgreSQL              │
│  (主数据源)   │  (辅助数据源) │   (业务数据源)             │
└──────────────┴──────────────┴────────────────────────────┘
```

---

## 3. 后端技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **框架** | Spring Boot | 4.0.6 | 核心框架 |
| **安全** | Sa-Token | 1.45.0 | 轻权认证框架 |
| **ORM** | MyBatis | 4.0.1 | 持久层框架 |
| **分页** | PageHelper | 2.1.1 | MyBatis 分页插件 |
| **连接池** | Druid | 1.2.28 | 数据库连接池 |
| **多数据源** | Dynamic DataSource | 4.5.0 | 动态数据源切换 |
| **数据库** | MySQL | 8.0+ | 主数据源 |
| **数据库** | SQLite | 3.45.2 | 辅助数据源 |
| **数据库** | PostgreSQL | 42.7.3 | 业务数据源 |
| **工具库** | Hutool | 5.8.26 | 通用工具集 |
| **验证码** | Kaptcha | 2.3.3 | 图形验证码 |
| **校验** | Spring Validation | - | 参数校验 |
| **热部署** | Spring DevTools | - | 开发热重载 |
| **构建** | Maven | - | 项目构建管理 |

### 后端包结构

```
com.selfmodeling/
├── config/                    # 配置层
│   ├── CorsConfig.java        # CORS 跨域配置
│   ├── DataSourceConfig.java  # 多数据源配置
│   ├── MyBatisConfig.java     # MyBatis 配置
│   ├── SaTokenConfig.java     # Sa-Token 配置
│   └── KaptchaConfig.java     # 验证码配置
├── controller/                # 控制层（REST API）
│   ├── AuthController.java    # 认证接口
│   ├── MetadataController.java# 元数据接口
│   ├── ModelController.java   # 模型管理接口
│   └── SqlController.java     # SQL 操作接口
├── dto/                       # 数据传输对象
│   ├── Result.java            # 统一响应封装
│   ├── PageResult.java        # 分页结果
│   ├── LoginResponse.java     # 登录响应
│   ├── CaptchaResponse.java   # 验证码响应
│   ├── QueryConfig.java       # 查询配置
│   └── ...
├── entity/                    # 实体层（数据库映射）
│   ├── ModelInfo.java         # 模型信息
│   ├── ModelStep.java         # 模型步骤
│   └── SysUser.java           # 系统用户
├── exception/                 # 异常处理
│   └── GlobalExceptionHandler.java # 全局异常处理器
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
└── SelfModelingApplication.java # 启动类
```

---

## 4. 前端技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **框架** | Vue | 3.4.31 | 渐进式前端框架 |
| **构建** | Vite | 5.3.3 | 前端构建工具 |
| **语言** | TypeScript | 5.5.3 | 类型安全 |
| **路由** | Vue Router | 4.4.0 | SPA 路由管理 |
| **状态管理** | Pinia | 2.1.7 | 响应式状态管理 |
| **UI组件** | Element Plus | 2.7.6 | UI 组件库 |
| **图标** | @element-plus/icons-vue | 2.5.6 | 图标库 |
| **流程图** | @vue-flow/core | 1.41.2 | 画布/流程图渲染 |
| **流程图扩展** | background, controls, minimap | - | 画布背景/控件/小地图 |
| **代码编辑** | Monaco Editor | 0.50.0 | SQL 编辑器 |
| **HTTP** | Axios | 1.7.2 | HTTP 请求 |
| **工具** | @vueuse/core | 10.11.0 | 组合式工具集 |
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
│   │   └── ...
│   └── queryEditor/           # 查询编辑器组件
│       ├── QueryEditor.vue    # 查询编辑器主组件
│       ├── CanvasArea.vue     # 画布区域
│       ├── TableNode.vue      # 表节点
│       └── ...
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

### 5.1 主数据源 - MySQL

**sys_user（系统用户表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| username | VARCHAR(50) UNIQUE | 用户名 |
| password | VARCHAR(100) | 密码（BCrypt 加密） |
| nickname | VARCHAR(50) | 昵称 |
| email | VARCHAR(100) | 邮箱 |
| phone | VARCHAR(20) | 手机号 |
| status | TINYINT | 状态（1-启用） |
| create_time / update_time | DATETIME | 创建/更新时间 |

**model_info（模型信息表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| model_code | VARCHAR(50) UNIQUE | 模型编码 |
| model_name | VARCHAR(100) | 模型名称 |
| model_desc | VARCHAR(500) | 模型描述 |
| model_type | VARCHAR(20) | 模型类型 |
| data_source | VARCHAR(20) | 数据源标识 |
| status | TINYINT | 状态 |
| create_time / update_time | DATETIME | 创建/更新时间 |

**model_step（模型步骤表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| model_id | BIGINT | 所属模型ID |
| step_code | VARCHAR(50) UNIQUE | 步骤编码 |
| step_name | VARCHAR(100) | 步骤名称 |
| step_desc | VARCHAR(500) | 步骤描述 |
| step_type | VARCHAR(20) | 步骤类型 |
| sort_order | INT | 排序号 |
| step_config | TEXT | 步骤配置（JSON） |
| sql_statement | TEXT | SQL 语句 |
| result_table_name | VARCHAR(100) | 结果表名 |
| execute_status | VARCHAR(20) | 执行状态 |
| execute_start_time | DATETIME | 执行开始时间 |
| execute_end_time | DATETIME | 执行结束时间 |
| execute_log | TEXT | 执行日志 |
| create_time / update_time | DATETIME | 创建/更新时间 |

### 5.2 多数据源架构

| 数据源 | 用途 | 连接方式 |
|--------|------|----------|
| **MySQL** | 系统元数据（主数据源） | `jdbc:mysql://localhost:3306/self_modeling` |
| **SQLite** | 辅助数据源 | `jdbc:sqlite:selfmodeling.db` |
| **PostgreSQL** | 业务数据查询 | `jdbc:postgresql://localhost:5432/test` |

---

## 6. 核心业务模块

### 6.1 认证模块（Auth）

- **认证方式**：Sa-Token 会话认证
- **Token 有效期**：30 天
- **活跃超时**：30 分钟无操作自动过期
- **密码加密**：BCrypt
- **验证码**：Kaptcha 图形验证码

### 6.2 模型管理模块（Model）

- 模型 CRUD + 分页查询
- 模型步骤 CRUD + 排序/插入/交换
- 步骤树结构（支持嵌套子步骤）
- 模型复制、状态管理
- 步骤执行（SQL 执行）

**步骤类型**：

| 类型 | 说明 |
|------|------|
| start | 开始节点 |
| end | 结束节点 |
| task | 任务节点 |
| gateway | 网关节点 |
| subprocess | 子流程节点 |

**执行状态**：

| 状态 | 说明 |
|------|------|
| pending | 待执行 |
| running | 执行中 |
| success | 执行成功 |
| failed | 执行失败 |

### 6.3 元数据模块（Metadata）

- 多数据源元数据浏览（表/字段）
- 表搜索（按表名/字段名模糊匹配）
- 数据源连接状态检查
- 表数据预览与行数统计

### 6.4 SQL 模块（Sql）

- **SQL 校验**：仅允许 SELECT 语句
- **SQL 执行**：只读执行，限行返回
- **SQL ↔ 画布双向转换**

---

## 7. API 接口设计

### 7.1 认证接口 `/api/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/login` | 用户登录 |
| POST | `/logout` | 用户登出 |
| GET | `/captcha` | 获取验证码 |
| GET | `/userinfo` | 获取当前用户信息 |

### 7.2 模型接口 `/api/models`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 分页查询模型列表 |
| GET | `/{id}` | 获取模型详情 |
| POST | `/` | 创建模型 |
| PUT | `/{id}` | 更新模型 |
| DELETE | `/{id}` | 删除模型 |
| POST | `/{id}/copy` | 复制模型 |

### 7.3 步骤接口 `/api/models/{modelId}/steps`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取步骤列表 |
| POST | `/` | 添加步骤 |
| PUT | `/{stepId}` | 更新步骤 |
| DELETE | `/{stepId}` | 删除步骤 |
| POST | `/execute/{stepId}` | 执行步骤 |

### 7.4 元数据接口 `/api/metadata`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/datasources` | 获取数据源列表 |
| GET | `/tables` | 获取表列表 |
| GET | `/tables/{tableName}/columns` | 获取字段列表 |

---

## 8. 安全设计

| 层面 | 方案 |
|------|------|
| **认证** | Sa-Token 会话认证，Token 有效期 30 天 |
| **密码** | BCrypt 加密存储 |
| **验证码** | Kaptcha 图形验证码 |
| **CORS** | CorsConfig 允许跨域 |
| **SQL注入** | SQL 校验仅允许 SELECT；参数化查询 |
| **异常处理** | GlobalExceptionHandler 统一捕获 |

---

## 9. 前端路由设计

| 路径 | 组件 | 说明 | 需认证 |
|------|------|------|--------|
| `/login` | LoginPage | 登录页 | 否 |
| `/models` | ModelList | 模型列表 | 是 |
| `/models/:id/edit` | ModelEditor | 模型编辑 | 是 |

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
| 500 | 服务端异常 |

---

## 11. 关键设计决策

1. **Sa-Token 认证**：轻量级权限框架，支持会话认证、多端登录
2. **多数据源**：Dynamic DataSource 支持运行时切换数据源
3. **PageHelper 分页**：MyBatis 分页插件，自动拦截分页 SQL
4. **步骤执行**：支持异步执行，状态实时更新
5. **SQL 配置保存时重置状态**：确保修改 SQL 后需要重新执行
