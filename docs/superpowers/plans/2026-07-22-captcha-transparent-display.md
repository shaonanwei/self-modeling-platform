# 登录验证码透明展示实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 移除登录验证码的白色容器背景和悬停“换一个”提示，同时保留点击刷新、加载状态与键盘焦点反馈。

**Architecture:** 保持现有验证码请求和 `refreshCaptcha` 数据流不变，只收敛 `LoginPage.vue` 的模板、图标导入和验证码按钮样式。使用 Node.js 内置测试对组件源文件建立最小回归约束，再通过浏览器验证真实渲染与刷新交互。

**Tech Stack:** Vue 3、TypeScript、Element Plus、Vite、Node.js `node:test`

## Global Constraints

- 不增加 npm 依赖。
- 不修改验证码接口、登录校验或表单提交逻辑。
- 保留点击验证码刷新、加载状态、固定尺寸、移动端尺寸和键盘焦点轮廓。
- 验证码按钮不得显示背景、边框、阴影、内边距或悬停“换一个”提示。

---

### Task 1: 登录验证码透明展示

**Files:**
- Modify: `frontend/tests/login-captcha-ui.test.mjs`
- Modify: `frontend/src/pages/login/LoginPage.vue:66-84`
- Modify: `frontend/src/pages/login/LoginPage.vue:121`
- Modify: `frontend/src/pages/login/LoginPage.vue:366-447`

**Interfaces:**
- Consumes: `refreshCaptcha(): Promise<void>`、`captchaImage: Ref<string>` 和现有 `.captcha-image` 按钮。
- Produces: 仍可点击和键盘聚焦的透明验证码按钮；不新增应用接口。

- [ ] **Step 1: 写入失败的静态回归测试**

```javascript
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const loginPagePath = fileURLToPath(
  new URL('../src/pages/login/LoginPage.vue', import.meta.url)
)
const loginPage = readFileSync(loginPagePath, 'utf8')

test('captcha refresh control is a keyboard-accessible button', () => {
  assert.match(
    loginPage,
    /<button[\s\S]*?type="button"[\s\S]*?class="captcha-image"[\s\S]*?aria-label="刷新验证码"/
  )
})

test('captcha refresh control keeps loading feedback without hover text', () => {
  assert.match(loginPage, /class="captcha-loading-icon"/)
  assert.doesNotMatch(loginPage, /captcha-refresh-hint/)
  assert.doesNotMatch(loginPage, /RefreshRight/)
  assert.doesNotMatch(loginPage, /title="看不清？换一张"/)
})

test('captcha card aligns with the current input height', () => {
  assert.match(loginPage, /\.captcha-image\s*{[\s\S]*?height:\s*62px;/)
})

test('captcha image remains fully visible inside the card', () => {
  assert.match(loginPage, /\.captcha-image img\s*{[\s\S]*?object-fit:\s*contain;/)
})

test('captcha button uses transparent chrome', () => {
  const block = loginPage.match(
    /\.captcha-image\s*\{([\s\S]*?)\n\}/
  )?.[1]

  assert.ok(block, 'missing .captcha-image styles')
  assert.match(block, /padding:\s*0;/)
  assert.match(block, /border:\s*0;/)
  assert.match(block, /background:\s*transparent;/)
  assert.match(block, /box-shadow:\s*none;/)
  assert.doesNotMatch(loginPage, /\.captcha-image:hover\s*\{/)
})
```

- [ ] **Step 2: 运行测试并确认按预期失败**

Run: `node --test frontend/tests/login-captcha-ui.test.mjs`

Expected: FAIL；现有组件仍包含 `captcha-refresh-hint`、`RefreshRight` 和白色渐变背景。

- [ ] **Step 3: 实现最小模板和样式修改**

模板删除以下节点：

```vue
<span v-if="captchaImage" class="captcha-refresh-hint" aria-hidden="true">
  <el-icon><RefreshRight /></el-icon>
  <span>换一个</span>
</span>
```

图标导入调整为：

```typescript
import { User, Lock, Key, Loading } from '@element-plus/icons-vue'
```

验证码按钮基础样式调整为：

```css
.captcha-image {
  appearance: none;
  width: 144px;
  height: 62px;
  padding: 0;
  flex-shrink: 0;
  cursor: pointer;
  border: 0;
  border-radius: 10px;
  overflow: hidden;
  background: transparent;
  box-shadow: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.captcha-image:focus-visible {
  outline: 3px solid rgba(102, 126, 234, 0.2);
  outline-offset: 2px;
}
```

删除 `.captcha-image:hover`、`.captcha-image:active`、`.captcha-refresh-hint` 及其悬停/聚焦显示规则。

- [ ] **Step 4: 运行测试并确认通过**

Run: `node --test frontend/tests/login-captcha-ui.test.mjs`

Expected: 5 tests PASS，0 tests FAIL。

- [ ] **Step 5: 运行前端构建**

Run: `npm run build`（工作目录：`frontend`）

Expected: `vue-tsc` 与 `vite build` 均成功退出，无 TypeScript 或 Vue 模板错误。

- [ ] **Step 6: 提交实现**

```powershell
git add frontend/src/pages/login/LoginPage.vue frontend/tests/login-captcha-ui.test.mjs
git commit -m "ui: simplify captcha display"
```

### Task 2: 登录页浏览器验证

**Files:**
- Verify only: `frontend/src/pages/login/LoginPage.vue`

**Interfaces:**
- Consumes: `http://127.0.0.1:5173/login` 和现有验证码 API。
- Produces: 登录页渲染与验证码刷新交互的验证证据。

- [ ] **Step 1: 验证页面基础状态**

打开 `http://127.0.0.1:5173/login`，确认 URL、标题、登录表单和验证码图片均存在，且无 Vite 错误覆盖层。

- [ ] **Step 2: 验证视觉结果**

在桌面视口确认验证码图片周围没有白色卡片背景、边框或阴影；鼠标悬停后不出现“换一个”文字或刷新图标。

- [ ] **Step 3: 验证刷新交互**

记录验证码图片 `src`，点击验证码，等待请求完成并确认 `src` 已变化；检查控制台没有相关错误。

- [ ] **Step 4: 检查最终差异**

Run: `git status --short && git diff --check`

Expected: 工作树没有未提交的实现文件，`git diff --check` 无输出。
