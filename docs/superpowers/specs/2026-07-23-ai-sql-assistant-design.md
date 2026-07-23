# AI SQL 助手设计

## 1. 背景

当前步骤编辑界面的 SQL 配置区由 `StepEditDialog` 承载，内部使用 `QueryEditor`、`SqlEditor` 和 Monaco Editor。系统已有元数据查询、SQL 解析、只读校验和预览执行能力，但没有 AI 模型接入。

本功能在 SQL 编辑界面增加一个通义千问对话助手。用户可用自然语言描述查询需求，AI 根据当前数据源元数据和当前编辑器 SQL 生成或修改查询语句。AI 不直接执行或保存 SQL，用户必须主动将通过安全校验的 SQL 应用到编辑器。

`dodo-agentx` 仅作为通义千问配置、提示词、元数据工具和安全控制的参考实现，不纳入当前项目构建，也不复制其 AgentX、Redis、PgVector、MinIO、MCP 或 Skills 体系。

## 2. 目标

- 在 SQL 编辑器中提供右侧抽屉式 AI 对话界面。
- 接入通义千问 OpenAI 兼容接口。
- 支持多轮对话和 SSE 流式回答。
- 支持生成新 SQL，以及解释、优化和修正当前 SQL。
- 允许 AI 按需读取当前数据源的表名、字段、类型、注释、主键和关联关系。
- 不向 AI 提供任何业务数据行。
- 仅允许安全的单条只读 `SELECT` 或 `WITH ... SELECT` 应用到编辑器。
- 不改变现有步骤保存、SQL 校验、SQL 执行和画布转换语义。

## 3. 非目标

- 不持久化 AI 对话历史。
- 不允许 AI 自动执行、自动保存或自动转换画布。
- 不提供图表、数据分析报告、文件分析或联网搜索。
- 不引入 AgentX、Spring AI、Spring AI Alibaba、Redis、PgVector、MCP 或向量检索。
- 不提供后台模型配置页面或多模型切换。
- 不将 `dodo-agentx` 目录纳入 Maven 构建或功能提交。

## 4. 总体架构

### 4.1 前端

在 `QueryEditor` 工具栏增加“AI 生成 SQL”按钮。按钮打开新的 `AiSqlDrawer` 组件。该组件负责：

- 展示临时多轮对话；
- 发起带认证头的流式 POST 请求；
- 解析 SSE 事件；
- 显示流式文本、状态和安全 SQL 代码卡片；
- 停止生成、重新生成和清空当前对话；
- 将用户主动选择的安全 SQL 应用到 Monaco Editor。

对话状态只存在于当前 `StepEditDialog` 组件生命周期。关闭 AI 抽屉不清空消息；关闭整个步骤编辑窗口后组件销毁，对话随之清空。

### 4.2 后端

新增以下边界：

- `AiSqlController`：认证后的 SSE 接口入口；
- `AiSqlService`：构造上下文、驱动有限轮工具调用、输出安全事件；
- `QwenClient`：封装通义千问 OpenAI 兼容协议和流式响应解析；
- `AiSqlMetadataTools`：封装允许模型调用的只读元数据工具；
- `AiSqlProperties`：类型化配置；
- AI 请求、消息、SSE 事件和工具参数 DTO。

后端复用现有 `MetadataService` 和 `ReadOnlySqlGuard`，但不调用 `previewTableData`、`getTableRowCount` 或 SQL 执行接口。

## 5. 前端交互

### 5.1 抽屉布局

- 从 SQL 编辑器右侧覆盖展开；
- 默认宽度为 `440px`；
- 不永久压缩画布；
- 顶部包含标题、清空对话和关闭按钮；
- 中间为消息列表；
- 底部为多行输入框和发送/停止按钮。

初始状态提供三个快捷问题：

- 根据描述生成 SQL；
- 优化当前 SQL；
- 解释并修正当前 SQL。

### 5.2 流式交互

- 同一抽屉同一时间只允许一个请求；
- 请求进行时逐步追加 AI 文本；
- 工具调用过程只展示“正在查找相关表”“正在读取表结构”“正在校验 SQL”等状态，不展示工具参数和完整元数据；
- 用户点击“停止生成”时，通过 `AbortController` 终止浏览器请求；
- 网络异常时保留已收到内容，并显示“重新生成”操作。

### 5.3 应用 SQL

后端发送 `valid=true` 的 `sql` 事件后，前端为对应 SQL 代码卡片显示“应用到编辑器”按钮。

点击后：

- 替换当前 Monaco Editor 的全部 SQL；
- 同步更新 `queryEditorStore.sqlText`；
- 不保存步骤；
- 不执行 SQL；
- 不转换画布；
- 不关闭 AI 抽屉。

`SqlEditor` 新增受控全文替换方法，使用 Monaco 编辑操作和撤销停止点写入，避免直接 `setValue` 清空撤销栈。用户可使用 `Ctrl+Z` 恢复应用前的 SQL。

## 6. API 与 SSE 协议

### 6.1 请求

```http
POST /api/v1/ai/sql/chat
Content-Type: application/json
Accept: text/event-stream
Authorization: <current token>
```

请求示例：

```json
{
  "dataSourceId": "master",
  "currentSql": "SELECT * FROM orders",
  "messages": [
    {
      "role": "user",
      "content": "按客户统计订单总额"
    }
  ]
}
```

请求约束：

- `dataSourceId` 必填；
- `currentSql` 最长 20,000 个字符；
- 最多携带 20 条消息；
- 单条消息最长 4,000 个字符；
- 消息总长度最多 24,000 个字符；
- 角色只允许 `user` 和 `assistant`；
- 当前用户身份从 Sa-Token 会话获取，不接受客户端用户 ID。

前端不能使用原生 `EventSource`，因为该接口需要 POST 请求和 `Authorization` 头。新增 API 方法使用 `fetch`、`getAccessToken()` 和 `ReadableStream` 解析 SSE。

### 6.2 事件

状态事件：

```text
event: status
data: {"message":"正在读取表结构"}
```

文本增量：

```text
event: delta
data: {"content":"SELECT"}
```

安全 SQL：

```text
event: sql
data: {"sql":"SELECT ...","valid":true,"message":"校验通过"}
```

完成事件：

```text
event: done
data: {"finishReason":"stop"}
```

错误事件：

```text
event: error
data: {"code":"QWEN_RATE_LIMIT","message":"AI 服务繁忙，请稍后重试","retryable":true}
```

前端只根据 `sql` 事件决定是否显示“应用到编辑器”，不能根据 AI 文本中的代码围栏自行放行。

## 7. 通义千问配置

```yaml
app:
  ai:
    sql:
      enabled: ${AI_SQL_ENABLED:false}
      api-key: ${QWEN_API_KEY:}
      base-url: ${QWEN_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
      model: ${QWEN_MODEL:qwen-plus}
      temperature: 0.1
      timeout-seconds: 60
      max-tool-rounds: 4
      max-concurrent-requests-per-user: 1
```

约束：

- API Key 只能来自环境变量或 Git 忽略的本地配置；
- `enabled=false` 或 API Key 为空时，接口返回服务未配置，不影响其他接口启动；
- Base URL 可切换为与 API Key 地域一致的业务空间专属域名；
- 不在日志中输出 API Key、请求头、完整提示词、完整 SQL 或完整元数据。

## 8. 模型提示词与上下文

系统提示词明确模型职责：

- 只帮助生成、解释、优化和修正查询 SQL；
- 只能生成单条只读 `SELECT` 或 `WITH ... SELECT`；
- 不得生成 INSERT、UPDATE、DELETE、MERGE、DDL、CALL、事务控制、文件访问或延时函数；
- 需要真实表结构时必须调用元数据工具，不得编造表名和字段名；
- 不得要求或推测业务数据内容；
- 最终 SQL 必须放在 `sql` 代码围栏中；
- 当前 SQL 只作为待修改上下文，不代表已通过安全校验。

每次请求包含：

- 固定系统提示词；
- 当前数据源标识；
- 当前编辑器 SQL；
- 当前临时会话历史；
- 安全元数据工具定义。

元数据工具输出采用结构化 JSON，并使用明确边界标记。表注释和字段注释始终视为数据而非指令。

## 9. 工具调用

### 9.1 `list_tables`

输入：

```json
{"keyword":"order"}
```

行为：

- 调用 `MetadataService.getAllTables` 或 `searchMetadata`；
- 只返回表名、Schema、表类型和表注释；
- 最多返回 50 张表；
- 不返回行数、样例数据或建表 SQL。

### 9.2 `describe_tables`

输入：

```json
{"tableNames":["orders","customers"]}
```

行为：

- 一次最多允许 5 张表；
- 返回字段名、类型、是否可空、主键、默认值、字段注释和已有索引信息；
- 可复用现有表关联查询能力补充关联关系；
- 不访问业务数据行。

### 9.3 `validate_read_only_sql`

输入：

```json
{"sql":"SELECT ..."}
```

行为：

- 只调用 `ReadOnlySqlGuard`；
- 不调用 `EXPLAIN`；
- 不连接数据库执行 SQL；
- 返回是否通过及有限错误原因。

### 9.4 调用限制

- 每个用户最多一个进行中的 AI SQL 请求；
- 单次请求最多 4 轮工具调用；
- 工具名称使用服务端白名单匹配；
- 未知工具、非法 JSON、越权数据源或超长参数立即终止；
- 工具输出在发送给模型前进行字段筛选和长度限制。

## 10. 最终 SQL 安全门禁

模型结束回答后，后端从最终内容中提取 SQL 代码块，对每个候选 SQL 再次执行 `ReadOnlySqlGuard`。

- 通过校验：发送 `sql` 事件且 `valid=true`；
- 未通过校验：可发送 `sql` 事件且 `valid=false`，但前端不显示应用按钮；
- 没有 SQL 代码块：只返回普通对话文本；
- 多个安全 SQL 代码块：分别发送事件，用户选择其中一个应用；
- 模型调用过 `validate_read_only_sql` 不替代最终门禁。

模型提示词和 Function Calling 都不是安全边界，最终 `ReadOnlySqlGuard` 是唯一的应用放行依据。

## 11. 错误处理

- AI 未启用或 Key 缺失：返回 503 和“AI SQL 助手未配置”；
- 未认证：沿用全局 Sa-Token 401 行为；
- 单用户已有请求：返回 429；
- 千问 401：返回“AI 服务认证失败”，不透传服务端响应详情；
- 千问 429：返回可重试的“AI 服务繁忙”；
- 千问连接或读取超时：返回可重试的超时事件；
- 数据源不存在或不可用：返回有限的元数据错误；
- 客户端断开：取消上游 HTTP 请求并清理用户并发占位；
- 工具轮次超限：终止并提示用户缩小问题范围；
- 服务端异常：记录错误类型和请求标识，不记录敏感正文。

AI 消息按纯文本和受控 SQL 卡片渲染，不直接使用未经清洗的 `v-html`。

## 12. 测试策略

### 12.1 后端

- 匿名访问 `/api/v1/ai/sql/chat` 返回 401；
- 功能关闭或 Key 缺失时安全失败；
- 使用本地 Stub HTTP 服务验证千问 SSE 分片和工具参数聚合，不调用真实模型；
- 只允许三个白名单工具；
- 元数据工具不调用数据预览、行数统计或 JDBC 执行；
- 单次请求最多 4 轮工具调用；
- 客户端取消后释放连接和并发占位；
- 合法 `SELECT` 产生 `valid=true` SQL 事件；
- DELETE、DDL、堆叠语句、文件读取和危险函数不能产生可应用事件；
- 日志测试确保 API Key、完整消息和完整 SQL 不被记录。

### 12.2 前端

引入最小化的 Vitest、Vue Test Utils 和 DOM 测试环境，仅覆盖本功能关键行为：

- 抽屉打开、关闭和临时消息生命周期；
- 请求携带当前数据源、当前 SQL、消息和认证头；
- SSE 状态、增量、SQL、完成和错误事件解析；
- 停止生成会取消请求；
- 只有 `valid=true` 显示应用按钮；
- 应用 SQL 更新编辑器但不触发保存、执行或画布转换；
- Monaco 全文替换保留撤销操作；
- 关闭整个步骤窗口后清空对话。

### 12.3 发布验证

- 运行全部后端测试和 Maven 打包；
- 运行 AI SQL 前端定向测试和前端生产构建；
- 使用本地 Stub 模型验证工具调用流程；
- 在配置真实 `QWEN_API_KEY` 的本地环境完成一次人工冒烟：
  - 生成简单查询；
  - 按需读取表结构；
  - 多轮修正；
  - 停止生成；
  - 应用安全 SQL；
  - 拒绝修改类 SQL；
- 确认 Git 提交不包含 API Key、`dodo-agentx` 或 `.superpowers` 视觉伴侣文件。

## 13. 关键实现原则

- 保持 AI 模块与现有 SQL 执行服务隔离；
- 元数据工具能力最小化，不复用包含预览数据的方法；
- 对话状态由前端当前组件持有，后端保持无会话持久化；
- 所有外部响应先转换为项目内部事件，不直接透传供应商对象；
- 先写失败测试，再实现接口、工具、安全门禁和前端行为；
- 不修改现有步骤基本信息与 SQL 配置的 Tab 隔离保存语义。
