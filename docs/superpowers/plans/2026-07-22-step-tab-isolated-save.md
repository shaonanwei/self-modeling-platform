# 编辑步骤 Tab 隔离保存实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复步骤 Tab 相互覆盖以及“下一步”不保存基本信息的问题，并确保新增、插入模式在 SQL 配置页只更新已经创建的步骤。

**Architecture:** 保留现有步骤创建、插入和更新 API。弹窗维护一个已持久化步骤 ID：编辑模式使用原步骤 ID；新增和插入模式在点击“下一步”时创建记录并保存返回 ID。SQL 配置页始终只通过该 ID 更新 `stepConfig`。

**Tech Stack:** Vue 3、TypeScript、Node.js 内置测试运行器、Spring Boot、JUnit 5、Mockito、Maven。

## Global Constraints

- 基本信息 Tab 只更新 `stepName` 和 `stepDesc`。
- SQL 配置 Tab 只更新 `stepConfig` 及由其解析得到的 `sqlStatement`。
- 请求字段为 `null` 时保留数据库原值；空字符串仍是明确更新值。
- 编辑、新增和插入模式点击“下一步”时必须先持久化基本信息。
- 新增和插入模式进入 SQL 配置后只更新已创建的步骤，不重复创建。
- SQL 更新继续执行现有单条只读 `SELECT` 校验。
- 不修改或提交当前工作树中无关的 `README.md`。

---

### Task 1: 后端保留更新请求中未提供的字段

**Files:**
- Modify: `backend/src/test/java/com/selfmodeling/service/impl/ModelServiceImplSqlSafetyTest.java`
- Modify: `backend/src/main/java/com/selfmodeling/service/impl/ModelServiceImpl.java:296-304`

**Interfaces:**
- Consumes: `ModelService.updateStep(Long modelId, Long stepId, ModelStep step)` 与现有 `ModelStepMapper.updateStepById(...)`。
- Produces: 更新请求字段为 `null` 时保留现有步骤字段的合并语义；API 和 Mapper 签名不变。

- [ ] **Step 1: 编写基本信息更新不清空 SQL 的失败测试**

在 `ModelServiceImplSqlSafetyTest` 中增加：

```java
@Test
void preservesExistingSqlWhenOnlyBasicInfoChanges() {
    ModelStep existing = existingStep("SELECT 1");
    existing.setStepName("old name");
    existing.setStepType("task");
    existing.setStepDesc("old description");
    existing.setStepConfig("{\"queryConfig\":{\"tables\":[]}}");
    when(stepMapper.selectById(STEP_ID)).thenReturn(existing);

    ModelStep basicInfoUpdate = new ModelStep();
    basicInfoUpdate.setStepName("new name");
    basicInfoUpdate.setStepDesc("");

    modelService.updateStep(MODEL_ID, STEP_ID, basicInfoUpdate);

    verify(stepMapper).updateStepById(
            eq(STEP_ID), eq("new name"), eq("task"), eq(""),
            eq("{\"queryConfig\":{\"tables\":[]}}"), eq("SELECT 1"));
}
```

- [ ] **Step 2: 编写 SQL 更新不覆盖基本信息的失败测试**

在同一测试类中增加：

```java
@Test
void preservesExistingBasicInfoWhenOnlySqlChanges() {
    ModelStep existing = existingStep("SELECT 1");
    existing.setStepName("existing name");
    existing.setStepType("task");
    existing.setStepDesc("existing description");
    existing.setStepConfig("{}");
    when(stepMapper.selectById(STEP_ID)).thenReturn(existing);

    ModelStep sqlUpdate = new ModelStep();
    sqlUpdate.setStepConfig("{\"configType\":\"SQL\",\"sqlStatement\":\"SELECT 2\"}");

    modelService.updateStep(MODEL_ID, STEP_ID, sqlUpdate);

    verify(stepMapper).updateStepById(
            eq(STEP_ID), eq("existing name"), eq("task"), eq("existing description"),
            eq("{}"), eq("SELECT 2"));
}
```

- [ ] **Step 3: 运行测试并确认它因字段被覆盖而失败**

在 `backend` 目录执行：

```powershell
$env:JAVA_HOME = 'D:\SoftWare\jdk-21.0.4'
& 'D:\apache-maven\bin\mvn.cmd' -Dtest=ModelServiceImplSqlSafetyTest test
```

预期：新增的两个测试失败；Mapper 实际收到的 SQL 字段或基本信息字段为 `null`。

- [ ] **Step 4: 实现最小字段合并逻辑**

将 `ModelServiceImpl.updateStep` 改为：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void updateStep(Long modelId, Long stepId, ModelStep step) {
    ModelStep existingStep = getStepDetail(modelId, stepId);

    if (step.getStepName() == null) {
        step.setStepName(existingStep.getStepName());
    }
    if (step.getStepType() == null) {
        step.setStepType(existingStep.getStepType());
    }
    if (step.getStepDesc() == null) {
        step.setStepDesc(existingStep.getStepDesc());
    }

    if (step.getStepConfig() == null) {
        step.setStepConfig(existingStep.getStepConfig());
        step.setSqlStatement(existingStep.getSqlStatement());
    } else {
        extractAndSetConfigFields(step);
    }

    stepMapper.updateStepById(
        stepId, step.getStepName(), step.getStepType(), step.getStepDesc(),
        step.getStepConfig(), step.getSqlStatement()
    );

    log.info("更新步骤成功：modelId={}, stepId={}", modelId, stepId);
}
```

- [ ] **Step 5: 运行目标测试并确认通过**

```powershell
$env:JAVA_HOME = 'D:\SoftWare\jdk-21.0.4'
& 'D:\apache-maven\bin\mvn.cmd' -Dtest=ModelServiceImplSqlSafetyTest test
```

预期：`ModelServiceImplSqlSafetyTest` 全部通过，危险 SQL 测试仍然拒绝执行。

- [ ] **Step 6: 提交后端修复**

```powershell
git add -- backend/src/test/java/com/selfmodeling/service/impl/ModelServiceImplSqlSafetyTest.java backend/src/main/java/com/selfmodeling/service/impl/ModelServiceImpl.java
git commit -m "fix: preserve omitted step fields"
```

---

### Task 2: 前端只提交当前 Tab 的字段

**Files:**
- Create: `frontend/tests/step-tab-isolated-save.test.mjs`
- Modify: `frontend/src/components/model/StepEditDialog.vue:313-442`

**Interfaces:**
- Consumes: `modelApi.updateStep(modelId, stepId, Partial<ModelStep>)`。
- Produces: 编辑模式下基本信息请求只含 `stepName`、`stepDesc`；SQL 请求只含 `stepConfig`。

- [ ] **Step 1: 编写前端请求字段失败测试**

创建 `frontend/tests/step-tab-isolated-save.test.mjs`：

```javascript
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const dialogPath = fileURLToPath(
  new URL('../src/components/model/StepEditDialog.vue', import.meta.url)
)
const dialogSource = readFileSync(dialogPath, 'utf8')

function getEditUpdateBranch(handlerStart, handlerEnd) {
  const start = dialogSource.indexOf(handlerStart)
  const end = dialogSource.indexOf(handlerEnd, start)
  assert.notEqual(start, -1, `missing handler: ${handlerStart}`)
  assert.notEqual(end, -1, `missing handler end: ${handlerEnd}`)

  const handler = dialogSource.slice(start, end)
  const match = handler.match(/else if \(props\.editStep\) \{([\s\S]*?)\n\s*\} else \{/)
  assert.ok(match, `missing edit branch in ${handlerStart}`)
  return match[1]
}

test('basic info save only submits basic info fields in edit mode', () => {
  const branch = getEditUpdateBranch('const handleSaveAndClose', 'const handlePrev')

  assert.match(branch, /stepName:\s*form\.stepName/)
  assert.match(branch, /stepDesc:\s*form\.stepDesc/)
  assert.doesNotMatch(branch, /stepConfig:/)
  assert.doesNotMatch(branch, /stepType:/)
})

test('SQL save only submits step config in edit mode', () => {
  const branch = getEditUpdateBranch('const handleSubmit', '</script>')

  assert.match(branch, /stepConfig:\s*stepConfigStr/)
  assert.doesNotMatch(branch, /stepName:/)
  assert.doesNotMatch(branch, /stepDesc:/)
  assert.doesNotMatch(branch, /stepType:/)
})
```

- [ ] **Step 2: 运行测试并确认旧代码失败**

在 `frontend` 目录执行：

```powershell
node --test tests/step-tab-isolated-save.test.mjs
```

预期：两个测试失败，因为两个编辑分支都提交了另一个 Tab 的字段。

- [ ] **Step 3: 限制基本信息编辑请求字段**

在 `handleSaveAndClose` 的 `props.editStep` 分支中，将更新对象改为：

```typescript
const updateData: Partial<ModelStep> = {
  stepName: form.stepName,
  stepDesc: form.stepDesc
}
await modelApi.updateStep(props.modelId, props.editStep.id, updateData)
```

保留新增和插入分支当前的空 SQL 初始化逻辑，因为这些分支需要创建新记录。

- [ ] **Step 4: 限制 SQL 编辑请求字段**

在 `handleSubmit` 的 `props.editStep` 分支中，将更新对象改为：

```typescript
const updateData: Partial<ModelStep> = {
  stepConfig: stepConfigStr
}
await modelApi.updateStep(props.modelId, props.editStep.id, updateData)
```

保留新增和插入分支的完整创建请求。

- [ ] **Step 5: 运行前端回归测试**

```powershell
node --test tests/step-tab-isolated-save.test.mjs tests/login-captcha-ui.test.mjs
```

预期：6 个测试全部通过。

- [ ] **Step 6: 运行前端生产构建**

```powershell
npm run build
```

预期：`vue-tsc` 和 Vite 构建成功；允许保留项目现有的大 chunk 警告。

- [ ] **Step 7: 提交前端修复**

```powershell
git add -- frontend/src/components/model/StepEditDialog.vue frontend/tests/step-tab-isolated-save.test.mjs
git commit -m "fix: isolate step tab save payloads"
```

---

### Task 3: 集成验证

**Files:**
- Verify only; no production file changes expected.

**Interfaces:**
- Consumes: Task 1 的后端字段合并语义与 Task 2 的前端最小请求体。
- Produces: 可发布的双向 Tab 隔离保存修复验证记录。

- [ ] **Step 1: 运行后端目标测试**

```powershell
Set-Location backend
$env:JAVA_HOME = 'D:\SoftWare\jdk-21.0.4'
& 'D:\apache-maven\bin\mvn.cmd' -Dtest=ModelServiceImplSqlSafetyTest test
```

预期：目标测试全部通过，失败数为 0。

- [ ] **Step 2: 运行后端编译打包**

```powershell
& 'D:\apache-maven\bin\mvn.cmd' -DskipTests package
```

预期：`BUILD SUCCESS`。

- [ ] **Step 3: 运行前端测试与构建**

```powershell
Set-Location ..\frontend
node --test tests/step-tab-isolated-save.test.mjs tests/login-captcha-ui.test.mjs
npm run build
```

预期：6 个前端测试通过，Vite 构建成功。

- [ ] **Step 4: 执行浏览器冒烟验证**

使用一个已配置 SQL 的测试步骤执行：

1. 记录原步骤名称、描述和 SQL。
2. 只修改名称或描述，点击“保存&关闭”。
3. 重新打开该步骤，确认 SQL 与查询配置未变化。
4. 在 SQL 配置 Tab 修改为另一条合法只读 `SELECT` 并保存。
5. 重新打开该步骤，确认名称和描述未变化，SQL 已更新。

预期：两次保存都只影响当前 Tab。

- [ ] **Step 5: 检查最终差异和工作树**

```powershell
git diff --check
git status --short
git log -3 --oneline
```

预期：没有空白错误；无关的 `README.md` 仍未提交；修复由两个精准提交组成。

## 执行记录

- 后端 RED：新增 2 个测试按预期失败，分别捕获 SQL 字段和基本信息字段被写成 `null`。
- 后端 GREEN：`ModelServiceImplSqlSafetyTest` 共 5 个测试通过。
- 前端 RED：新增 2 个测试按预期失败，捕获两个编辑分支提交了其他 Tab 字段。
- 前端 GREEN：隔离保存测试与验证码测试共 6 个测试通过。
- 构建：Maven 打包和 Vite 生产构建成功；Vite 保留项目现有的大 chunk 警告。
- 浏览器双向保存冒烟未自动执行，因为当前没有明确可修改的专用测试步骤，避免改动真实业务数据。

---

### Task 4: “下一步”持久化基本信息并复用步骤 ID

**Files:**
- Modify: `frontend/tests/step-tab-isolated-save.test.mjs`
- Modify: `frontend/src/components/model/StepEditDialog.vue`
- Modify: `docs/superpowers/specs/2026-07-22-step-tab-isolated-save-design.md`
- Modify: `docs/superpowers/plans/2026-07-22-step-tab-isolated-save.md`

**Interfaces:**
- Consumes: `modelApi.addStep(...)`、`modelApi.insertStep(...)` 返回的 `{ data: ModelStep }`，以及 `modelApi.updateStep(modelId, stepId, data)`。
- Produces: `persistedStepId: Ref<number | null>` 和 `saveBasicInfo(): Promise<void>`；SQL 保存只允许更新 `persistedStepId` 指向的记录。

- [x] **Step 1: 扩展前端失败测试**

在 `frontend/tests/step-tab-isolated-save.test.mjs` 中验证：

```javascript
test('next saves basic info before opening SQL configuration', () => {
  const handler = getSourceBlock('const handleNext', 'const handleSaveAndClose')
  assert.ok(handler.indexOf('await saveBasicInfo()') < handler.indexOf('currentStep.value = 1'))
})

test('new and inserted steps persist the returned id', () => {
  const helper = getSourceBlock('const saveBasicInfo', 'const handleNext')
  assert.match(helper, /persistedStepId\.value\s*=\s*response\.data\.id/)
})

test('SQL save only updates the persisted step', () => {
  const handler = getSourceBlock('const handleSubmit', '</script>')
  assert.match(handler, /modelApi\.updateStep\(props\.modelId, persistedStepId\.value, updateData\)/)
  assert.doesNotMatch(handler, /modelApi\.(addStep|insertStep)/)
})
```

- [x] **Step 2: 运行测试并确认 RED**

```powershell
Set-Location frontend
node --test tests/step-tab-isolated-save.test.mjs
```

预期：新测试失败，因为当前“下一步”只切换页面，且 SQL 保存仍会在新增和插入模式重复创建记录。

- [x] **Step 3: 实现统一基础信息持久化**

在 `StepEditDialog.vue` 中增加 `persistedStepId`。编辑模式打开时赋值为 `props.editStep.id`，新增和插入模式从创建接口响应中赋值。抽取 `saveBasicInfo()`：已有 ID 时仅更新 `stepName`、`stepDesc`；没有 ID 时根据新增或插入模式创建空 SQL 记录并保存返回 ID。

- [x] **Step 4: 让所有 SQL 入口先保存基本信息**

`handleNext` 在表单校验通过后调用 `await saveBasicInfo()`，成功后才把 `currentStep` 设为 `1`。步骤导航的“SQL配置”点击事件改为 `handleNext`，防止绕过保存；失败时提示错误并保持当前页面。

- [x] **Step 5: SQL 配置只更新已持久化步骤**

`handleSubmit` 先检查 `persistedStepId`，然后仅提交：

```typescript
const updateData: Partial<ModelStep> = { stepConfig: stepConfigStr }
await modelApi.updateStep(props.modelId, persistedStepId.value, updateData)
```

删除 SQL 保存中的 `addStep` 和 `insertStep` 分支。返回基本信息后再次保存时继续更新同一 ID。

- [x] **Step 6: 验证前端测试和构建**

```powershell
node --test tests/step-tab-isolated-save.test.mjs tests/login-captcha-ui.test.mjs
npm run build
```

预期：所有 Node 回归测试通过；`vue-tsc` 和 Vite 构建成功，允许保留现有大 chunk 警告。

- [x] **Step 7: 检查差异**

```powershell
git diff --check
git status --short
```

预期：仅本任务文件和用户原有的 `README.md` 显示修改；`README.md` 不被覆盖或暂存。

**执行结果：** RED 阶段新增 6 个测试全部失败；GREEN 阶段本文件与验证码界面测试共 10 个测试通过。`npm run build` 成功，保留现有大 chunk 警告。
