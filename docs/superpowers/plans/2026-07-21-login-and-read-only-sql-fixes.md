# 登录校验与只读 SQL 编辑修复实施计划

> **供执行代理使用：** 必须使用 `superpowers:executing-plans` 按任务实施，并使用 `superpowers:test-driven-development` 完成每个红绿循环。步骤使用复选框跟踪。

**目标：** 空验证码只显示字段校验提示；模型步骤允许输入和保存 SQL，但保存与执行只接受单条只读 `SELECT`。

**架构：** 前端负责及时反馈，后端复用 `ReadOnlySqlGuard` 作为不可绕过的保存和执行边界。步骤草稿可保存空 SQL；非空 SQL 在持久化和执行前均规范化并校验。

**技术栈：** Vue 3、Element Plus、Monaco Editor、TypeScript、Spring Boot 4、JUnit 5、Mockito、JSqlParser。

## 全局约束

- 不修改 `frontend/package.json` 或 `frontend/package-lock.json`。
- 不合并、不推送、不创建 Git 提交。
- 保留 `Design.md` 和前端依赖文件的既有未提交状态。
- 用户 SQL 只允许一条可解析的只读 `SELECT`；系统内部结果表 DDL 保持现状。

---

### 任务 1：修复空验证码的登录提示

**文件：**
- 修改：`frontend/src/pages/login/LoginPage.vue:142-163`

**接口：**
- 消费：Element Plus `FormInstance.validate(): Promise<boolean>`。
- 产出：`handleLogin()` 在本地表单校验失败时直接返回，不调用登录错误处理或 `refreshCaptcha()`。

- [ ] **步骤 1：记录浏览器失败行为**

在 `http://127.0.0.1:5173/login` 输入非空用户名、至少 6 位密码，验证码留空并点击“登录”。

预期红测：页面同时出现字段消息“请输入验证码”和错误 toast“登录失败，请检查用户名和密码”。该失败已在实施前复现。

- [ ] **步骤 2：分离本地校验和接口错误处理**

将 `handleLogin` 改为先独立校验：

```ts
const handleLogin = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    loading.value = true
    await authStore.login(/* 保持现有参数 */)
    // 保持现有成功提示和跳转
  } catch (error: any) {
    ElMessage.error(error.message || '登录失败，请检查用户名和密码')
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
```

- [ ] **步骤 3：浏览器验证绿测**

重复步骤 1。

预期：只显示“请输入验证码”；不存在登录失败 toast；验证码图片 URL/内容不因本地校验而刷新。

---

### 任务 2：在步骤保存入口实施只读 SQL 防护

**文件：**
- 创建：`backend/src/test/java/com/selfmodeling/service/impl/ModelServiceImplSqlSafetyTest.java`
- 修改：`backend/src/main/java/com/selfmodeling/service/impl/ModelServiceImpl.java`
- 修改：`frontend/src/components/model/StepEditDialog.vue`

**接口：**
- 消费：`String ReadOnlySqlGuard.validate(String sql)`。
- 产出：步骤新增、插入和更新对非空 SQL 保存规范化的单条 `SELECT`；`handleSubmit()` 在调用步骤接口前完成远程校验。

- [ ] **步骤 1：编写后端保存失败测试**

在 `ModelServiceImplSqlSafetyTest` 中使用 Mockito 注入 `ModelStepMapper`、`ModelInfoMapper` 和真实 `ReadOnlySqlGuard`，覆盖：

```java
@Test
void updateStepRejectsMutationBeforePersistence() {
    ModelStep existing = existingStep(10L, 20L);
    when(stepMapper.selectById(20L)).thenReturn(existing);

    ModelStep update = updateWithSql("DELETE FROM sys_user");

    assertThrows(IllegalArgumentException.class,
            () -> modelService.updateStep(10L, 20L, update));
    verify(stepMapper, never()).updateStepById(
            anyLong(), any(), any(), any(), any(), any());
}

@Test
void updateStepNormalizesSafeSelectBeforePersistence() {
    when(stepMapper.selectById(20L)).thenReturn(existingStep(10L, 20L));

    modelService.updateStep(10L, 20L, updateWithSql(" SELECT 1; "));

    verify(stepMapper).updateStepById(
            eq(20L), any(), any(), any(), any(), eq("SELECT 1"));
}
```

- [ ] **步骤 2：运行保存测试确认失败**

```powershell
& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' -Dtest=ModelServiceImplSqlSafetyTest test
```

预期：修改型 SQL 未抛出异常，安全 SQL 仍以原始带分号文本传给 Mapper，因此测试失败。

- [ ] **步骤 3：在配置提取后调用 SQL 守卫**

向 `ModelServiceImpl` 注入：

```java
@Autowired
private ReadOnlySqlGuard readOnlySqlGuard;
```

在 `extractAndSetConfigFields` 读取 `sqlStatement` 后使用：

```java
String sqlStatement = sqlStatementObj == null ? null : String.valueOf(sqlStatementObj);
step.setSqlStatement(StrUtil.isBlank(sqlStatement)
        ? null
        : readOnlySqlGuard.validate(sqlStatement));
```

`addStep`、`insertStep`、`updateStep` 已共同调用该提取方法，因此保持单一后端边界。

- [ ] **步骤 4：运行保存测试确认通过**

重复步骤 2，预期全部通过。

- [ ] **步骤 5：前端最终保存前调用校验接口**

在 `StepEditDialog.vue` 导入 `sqlApi`。`handleSubmit` 取得 `finalSql` 后：

```ts
if (!finalSql.trim()) {
  ElMessage.warning('请先输入 SQL 语句')
  return
}

const validation = await sqlApi.validate(finalSql, modelDataSource.value)
if (!validation.data.valid) {
  ElMessage.error(validation.data.message || '只允许保存单条只读 SELECT')
  return
}
```

保留步骤 0 的“保存&关闭”空 SQL 草稿行为。

---

### 任务 3：在步骤执行入口移除用户 DML/DDL 分支

**文件：**
- 修改：`backend/src/test/java/com/selfmodeling/service/impl/ModelServiceImplSqlSafetyTest.java`
- 修改：`backend/src/main/java/com/selfmodeling/service/impl/ModelServiceImpl.java:474-545`

**接口：**
- 消费：`ReadOnlySqlGuard.validate(String)`。
- 产出：`executeStep(Long modelId, Long stepId)` 在查找数据源和启动异步任务之前拒绝不安全 SQL，并仅物化安全查询。

- [ ] **步骤 1：编写执行失败测试**

```java
@Test
void executeStepRejectsMutationBeforeDataSourceLookup() {
    ModelInfo model = new ModelInfo();
    model.setId(10L);
    model.setDataSource("missing");
    when(modelInfoMapper.selectById(10L)).thenReturn(model);
    when(stepMapper.selectById(20L))
            .thenReturn(existingStepWithSql(10L, 20L, "TRUNCATE TABLE audit_log"));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> modelService.executeStep(10L, 20L));

    assertEquals("Exactly one SELECT statement is required", error.getMessage());
    verify(stepMapper, never()).updateExecuteStart(anyLong(), any());
}
```

- [ ] **步骤 2：运行执行测试确认失败**

运行任务 2 的 Maven 定向命令。

预期：旧实现先报告数据源不存在，未通过 SQL 守卫，因此测试失败。

- [ ] **步骤 3：校验后再查找数据源并删除非 SELECT 分支**

在 `executeStep` 中按以下顺序处理：模型存在、步骤存在、SQL 非空、`readOnlySqlGuard.validate`、数据源存在、记录执行开始。

将异步主体收敛为：

```java
JdbcTemplate asyncJdbcTemplate = DataSourceConfig.getJdbcTemplate(finalDataSourceName);
if (asyncJdbcTemplate == null) {
    throw new IllegalStateException("数据源 " + finalDataSourceName + " 不存在");
}
asyncJdbcTemplate.execute("DROP TABLE IF EXISTS " + finalResultTableName);
asyncJdbcTemplate.execute("CREATE TABLE " + finalResultTableName + " AS " + safeSql);
```

删除 `asyncJdbcTemplate.update(sql)` 的用户 DML/DDL 执行分支。

- [ ] **步骤 4：运行 SQL 安全测试和已有守卫回归**

```powershell
& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' '-Dtest=ModelServiceImplSqlSafetyTest,ReadOnlySqlGuardTest,SqlServiceImplExecutionTest' test
```

预期：全部通过。

---

### 任务 4：完成构建和浏览器回归

**文件：**
- 不新增生产文件。

**接口：**
- 验证任务 1 至 3 的端到端行为。

- [ ] **步骤 1：运行全部后端测试和打包**

```powershell
& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' test
& 'D:\apache-maven\bin\mvn.cmd' -s '..\..\.maven-settings.xml' package
```

- [ ] **步骤 2：运行前端构建**

```powershell
npm.cmd run build
```

- [ ] **步骤 3：浏览器验证登录**

验证码留空提交，确认只有“请输入验证码”，无登录失败 toast，验证码不刷新。

- [ ] **步骤 4：浏览器验证 SQL 编辑和保存**

在步骤 SQL 编辑器输入 `SELECT 1`，确认校验和保存路径成功；输入 `DELETE FROM sys_user` 和 `SELECT 1; DELETE FROM sys_user`，确认保存被阻止且未发送步骤持久化请求。

- [ ] **步骤 5：检查工作树边界**

```powershell
git diff --check
git status --short
```

确认没有 Git 提交，既有 `Design.md`、`frontend/package.json`、`frontend/package-lock.json` 修改仍保留。
