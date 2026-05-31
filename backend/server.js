import express from 'express';
import cors from 'cors';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import Database from 'better-sqlite3';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const PORT = 8080;
const JWT_SECRET = 'self-modeling-platform-jwt-secret-key-256bits';

app.use(cors());
app.use(express.json());

// Initialize SQLite database
const db = new Database(path.join(__dirname, 'selfmodeling.db'));
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

// Create tables
db.exec(`
  CREATE TABLE IF NOT EXISTS sys_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    nickname TEXT,
    email TEXT,
    phone TEXT,
    avatar TEXT,
    status INTEGER NOT NULL DEFAULT 1,
    creator TEXT,
    create_time TEXT NOT NULL DEFAULT (datetime('now')),
    updater TEXT,
    update_time TEXT NOT NULL DEFAULT (datetime('now')),
    deleted INTEGER NOT NULL DEFAULT 0
  );

  CREATE TABLE IF NOT EXISTS model_info (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    model_code TEXT NOT NULL UNIQUE,
    model_name TEXT NOT NULL,
    model_desc TEXT,
    model_type TEXT,
    status INTEGER NOT NULL DEFAULT 1,
    version INTEGER NOT NULL DEFAULT 1,
    creator TEXT,
    create_time TEXT NOT NULL DEFAULT (datetime('now')),
    updater TEXT,
    update_time TEXT NOT NULL DEFAULT (datetime('now')),
    deleted INTEGER NOT NULL DEFAULT 0
  );

  CREATE TABLE IF NOT EXISTS model_step (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    model_id INTEGER NOT NULL,
    step_code TEXT NOT NULL UNIQUE,
    step_name TEXT NOT NULL,
    step_desc TEXT,
    step_type TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    step_config TEXT,
    parent_step_id INTEGER,
    condition_expr TEXT,
    timeout_seconds INTEGER,
    retry_count INTEGER DEFAULT 0,
    creator TEXT,
    create_time TEXT NOT NULL DEFAULT (datetime('now')),
    updater TEXT,
    update_time TEXT NOT NULL DEFAULT (datetime('now')),
    deleted INTEGER NOT NULL DEFAULT 0
  );

  CREATE INDEX IF NOT EXISTS idx_model_step_model_id ON model_step(model_id, sort_order);
`);

// Seed default admin user
const adminExists = db.prepare("SELECT id FROM sys_user WHERE username = ?").get('admin');
if (!adminExists) {
  const hashed = bcrypt.hashSync('admin123', 10);
  db.prepare("INSERT INTO sys_user (username, password, nickname, status) VALUES (?, ?, ?, ?)")
    .run('admin', hashed, '系统管理员', 1);
}

// ====== Helpers ======

function success(data, message = 'success') {
  return { code: 200, message, data, timestamp: Date.now() };
}

function error(code, message) {
  return { code, message, data: null, timestamp: Date.now() };
}

function authMiddleware(req, res, next) {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    return res.status(401).json(error(401, '未授权'));
  }
  try {
    const token = header.slice(7);
    const decoded = jwt.verify(token, JWT_SECRET);
    req.user = decoded;
    next();
  } catch {
    return res.status(401).json(error(401, 'Token 无效或已过期'));
  }
}

/** Convert snake_case DB row to camelCase for frontend */
function toCamel(row) {
  if (!row) return null;
  if (Array.isArray(row)) return row.map(toCamel);
  const result = {};
  for (const [key, value] of Object.entries(row)) {
    result[key.replace(/_([a-z])/g, (_, c) => c.toUpperCase())] = value;
  }
  return result;
}

/** Generate model code from Chinese/English name */
function generateModelCode(name) {
  if (!name) return 'MODEL_' + Date.now().toString(36).toUpperCase();
  // Extract letters and numbers, replace rest
  const clean = name.replace(/[^a-zA-Z0-9\u4e00-\u9fa5]/g, '');
  // Convert Chinese chars to pinyin initials (simple: use unicode-based fallback)
  const code = clean
    .split('')
    .map(c => /[\u4e00-\u9fa5]/.test(c) ? c : c.toUpperCase())
    .join('')
    .slice(0, 10);
  return (code || 'MODEL').toUpperCase() + '_' + Date.now().toString(36).toUpperCase();
}

function generateStepCode(modelId) {
  return 'STEP_' + modelId + '_' + Date.now().toString(36).toUpperCase();
}

// ==================== AUTH APIs ====================

app.post('/api/v1/auth/login', (req, res) => {
  const { username, password } = req.body;
  if (!username || !password) return res.status(400).json(error(400, '用户名和密码不能为空'));

  const user = db.prepare("SELECT * FROM sys_user WHERE username = ? AND deleted = 0").get(username);
  if (!user || !bcrypt.compareSync(password, user.password)) {
    return res.status(400).json(error(400, '用户名或密码错误'));
  }
  if (user.status !== 1) return res.status(400).json(error(400, '账号已被禁用'));

  const accessToken = jwt.sign({ userId: user.id, username: user.username }, JWT_SECRET, { expiresIn: '30m' });
  const refreshToken = jwt.sign({ userId: user.id }, JWT_SECRET, { expiresIn: '7d' });

  res.json(success({
    accessToken, refreshToken, expiresIn: 1800, tokenType: 'Bearer'
  }, '登录成功'));
});

app.post('/api/v1/auth/logout', authMiddleware, (_req, res) => {
  res.json(success(null, '登出成功'));
});

app.post('/api/v1/auth/refresh', (req, res) => {
  const { refreshToken } = req.body;
  if (!refreshToken) return res.status(400).json(error(400, '缺少 refreshToken'));
  try {
    const decoded = jwt.verify(refreshToken, JWT_SECRET);
    const user = db.prepare("SELECT * FROM sys_user WHERE id = ? AND deleted = 0").get(decoded.userId);
    if (!user || user.status !== 1) return res.status(400).json(error(400, '用户不存在或已被禁用'));
    const newAccessToken = jwt.sign({ userId: user.id, username: user.username }, JWT_SECRET, { expiresIn: '30m' });
    const newRefreshToken = jwt.sign({ userId: user.id }, JWT_SECRET, { expiresIn: '7d' });
    res.json(success({ accessToken: newAccessToken, refreshToken: newRefreshToken, expiresIn: 1800, tokenType: 'Bearer' }));
  } catch {
    return res.status(400).json(error(400, 'Token 刷新失败'));
  }
});

app.get('/api/v1/auth/userinfo', authMiddleware, (req, res) => {
  const user = db.prepare("SELECT id, username, nickname, email, phone, avatar, status FROM sys_user WHERE id = ?").get(req.user.userId);
  if (!user) return res.status(404).json(error(404, '用户不存在'));
  res.json(success(toCamel(user)));
});

// ==================== MODEL APIs ====================

app.get('/api/v1/models', authMiddleware, (req, res) => {
  const { pageNum = 1, pageSize = 10, modelName, status } = req.query;
  let sql = "SELECT * FROM model_info WHERE deleted = 0";
  let countSql = "SELECT COUNT(*) as total FROM model_info WHERE deleted = 0";
  const params = [];

  if (modelName) {
    sql += " AND model_name LIKE ?";
    countSql += " AND model_name LIKE ?";
    params.push(`%${modelName}%`);
  }
  if (status !== undefined && status !== '') {
    sql += " AND status = ?";
    countSql += " AND status = ?";
    params.push(Number(status));
  }

  sql += " ORDER BY create_time DESC LIMIT ? OFFSET ?";
  params.push(Number(pageSize), (Number(pageNum) - 1) * Number(pageSize));

  const total = db.prepare(countSql).get(...params.slice(0, params.length - 2)).total;
  const list = db.prepare(sql).all(...params);

  res.json(success({ total, list: toCamel(list) }));
});

app.get('/api/v1/models/:id', authMiddleware, (req, res) => {
  const model = db.prepare("SELECT * FROM model_info WHERE id = ? AND deleted = 0").get(req.params.id);
  if (!model) return res.status(404).json(error(404, '建模不存在'));
  res.json(success(toCamel(model)));
});

app.post('/api/v1/models', authMiddleware, (req, res) => {
  const { modelCode, modelName, modelDesc, modelType, status = 1 } = req.body;
  if (!modelName) return res.status(400).json(error(400, '模型名称不能为空'));

  const code = modelCode || generateModelCode(modelName);
  const creator = req.user.username || 'admin';

  const result = db.prepare(
    `INSERT INTO model_info (model_code, model_name, model_desc, model_type, status, version, creator, updater)
     VALUES (?, ?, ?, ?, ?, 1, ?, ?)`
  ).run(code, modelName, modelDesc || '', modelType || '', status, creator, creator);

  const model = db.prepare("SELECT * FROM model_info WHERE id = ?").get(result.lastInsertRowid);
  res.json(success(toCamel(model), '创建成功'));
});

app.put('/api/v1/models/:id', authMiddleware, (req, res) => {
  const existing = db.prepare("SELECT * FROM model_info WHERE id = ? AND deleted = 0").get(req.params.id);
  if (!existing) return res.status(404).json(error(404, '建模不存在'));

  const { modelName, modelDesc, modelType, status } = req.body;
  db.prepare(
    `UPDATE model_info SET model_name = ?, model_desc = ?, model_type = ?, status = ?, version = version + 1,
     updater = ?, update_time = datetime('now') WHERE id = ?`
  ).run(modelName || existing.model_name, modelDesc !== undefined ? modelDesc : existing.model_desc,
        modelType || existing.model_type, status !== undefined ? status : existing.status,
        req.user.username, req.params.id);

  const updated = db.prepare("SELECT * FROM model_info WHERE id = ?").get(req.params.id);
  res.json(success(toCamel(updated), '更新成功'));
});

app.delete('/api/v1/models/:id', authMiddleware, (req, res) => {
  const existing = db.prepare("SELECT * FROM model_info WHERE id = ? AND deleted = 0").get(req.params.id);
  if (!existing) return res.status(404).json(error(404, '建模不存在'));

  db.prepare("UPDATE model_info SET deleted = 1, update_time = datetime('now') WHERE id = ?").run(req.params.id);
  db.prepare("UPDATE model_step SET deleted = 1 WHERE model_id = ?").run(req.params.id);
  res.json(success(null, '删除成功'));
});

app.patch('/api/v1/models/:id/status', authMiddleware, (req, res) => {
  const { status } = req.body;
  db.prepare("UPDATE model_info SET status = ?, update_time = datetime('now') WHERE id = ?").run(status, req.params.id);
  res.json(success(null, '状态更新成功'));
});

app.post('/api/v1/models/:id/copy', authMiddleware, (req, res) => {
  const original = db.prepare("SELECT * FROM model_info WHERE id = ? AND deleted = 0").get(req.params.id);
  if (!original) return res.status(404).json(error(404, '建模不存在'));

  const code = original.model_code + '_COPY_' + Date.now().toString(36).toUpperCase();
  const creator = req.user.username;

  const result = db.prepare(
    `INSERT INTO model_info (model_code, model_name, model_desc, model_type, status, creator, updater)
     VALUES (?, ?, ?, ?, 0, ?, ?)`
  ).run(code, `${original.model_name} (副本)`, original.model_desc, original.model_type, creator, creator);

  // Copy steps
  const steps = db.prepare("SELECT * FROM model_step WHERE model_id = ? AND deleted = 0 ORDER BY sort_order").all(req.params.id);
  for (const step of steps) {
    db.prepare(
      `INSERT INTO model_step (model_id, step_code, step_name, step_desc, step_type, sort_order, step_config,
       parent_step_id, condition_expr, timeout_seconds, retry_count, creator, updater)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).run(result.lastInsertRowid, generateStepCode(result.lastInsertRowid), step.step_name, step.step_desc, step.step_type,
          step.sort_order, step.step_config, step.parent_step_id, step.condition_expr,
          step.timeout_seconds, step.retry_count, creator, creator);
  }

  const model = db.prepare("SELECT * FROM model_info WHERE id = ?").get(result.lastInsertRowid);
  res.json(success(toCamel(model), '复制成功'));
});

// ==================== STEP APIs ====================

app.get('/api/v1/models/:modelId/steps', authMiddleware, (req, res) => {
  const steps = db.prepare(
    "SELECT * FROM model_step WHERE model_id = ? AND deleted = 0 ORDER BY sort_order"
  ).all(req.params.modelId);
  // Parse step_config JSON and convert to camelCase
  const result = toCamel(steps).map(s => {
    if (s.stepConfig && typeof s.stepConfig === 'string') {
      try { s.stepConfig = JSON.parse(s.stepConfig); } catch {}
    }
    return s;
  });
  res.json(success(result));
});

app.get('/api/v1/models/:modelId/steps/:stepId', authMiddleware, (req, res) => {
  const step = db.prepare("SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0")
    .get(req.params.stepId, req.params.modelId);
  if (!step) return res.status(404).json(error(404, '步骤不存在'));
  const result = toCamel(step);
  if (result.stepConfig && typeof result.stepConfig === 'string') {
    try { result.stepConfig = JSON.parse(result.stepConfig); } catch {}
  }
  res.json(success(result));
});

app.post('/api/v1/models/:modelId/steps', authMiddleware, (req, res) => {
  const { stepName, stepType, stepDesc, stepConfig, parentStepId, conditionExpr, timeoutSeconds, retryCount } = req.body;
  if (!stepName || !stepType) return res.status(400).json(error(400, '步骤名称和类型不能为空'));

  const lastStep = db.prepare(
    "SELECT sort_order FROM model_step WHERE model_id = ? AND deleted = 0 ORDER BY sort_order DESC LIMIT 1"
  ).get(req.params.modelId);

  const sortOrder = lastStep ? lastStep.sort_order + 1000 : 1000;
  const stepCode = generateStepCode(req.params.modelId);
  const creator = req.user.username;
  const configJson = stepConfig ? (typeof stepConfig === 'object' ? JSON.stringify(stepConfig) : stepConfig) : null;

  const result = db.prepare(
    `INSERT INTO model_step (model_id, step_code, step_name, step_desc, step_type, sort_order, step_config,
     parent_step_id, condition_expr, timeout_seconds, retry_count, creator, updater)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).run(req.params.modelId, stepCode, stepName, stepDesc || '', stepType, sortOrder,
        configJson, parentStepId ?? null, conditionExpr ?? null, timeoutSeconds ?? 0, retryCount ?? 0, creator, creator);

  const step = db.prepare("SELECT * FROM model_step WHERE id = ?").get(result.lastInsertRowid);
  res.json(success(toCamel(step), '添加成功'));
});

app.post('/api/v1/models/:modelId/steps/insert', authMiddleware, (req, res) => {
  const { afterStepId, stepName, stepType, stepDesc, stepConfig, parentStepId, conditionExpr, timeoutSeconds, retryCount } = req.body;

  const afterStepRaw = db.prepare("SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0")
    .get(afterStepId, req.params.modelId);
  if (!afterStepRaw) return res.status(400).json(error(400, '插入位置的前一步骤不存在'));
  const afterStep = toCamel(afterStepRaw);

  const beforeStepRaw = db.prepare(
    "SELECT * FROM model_step WHERE model_id = ? AND deleted = 0 AND sort_order > ? ORDER BY sort_order LIMIT 1"
  ).get(req.params.modelId, afterStep.sortOrder);
  const beforeStep = beforeStepRaw ? toCamel(beforeStepRaw) : null;

  let newSortOrder;
  if (!beforeStep) {
    newSortOrder = afterStep.sortOrder + 1000;
  } else {
    newSortOrder = Math.floor((afterStep.sortOrder + beforeStep.sortOrder) / 2);
    if ((afterStep.sortOrder + beforeStep.sortOrder) % 2 !== 0) {
      db.prepare(
        "UPDATE model_step SET sort_order = sort_order + 1000 WHERE model_id = ? AND deleted = 0 AND sort_order > ?"
      ).run(req.params.modelId, afterStep.sortOrder);
      newSortOrder = afterStep.sortOrder + 1000;
    }
  }

  const stepCode = generateStepCode(req.params.modelId);
  const creator = req.user.username;
  const configJson = stepConfig ? (typeof stepConfig === 'object' ? JSON.stringify(stepConfig) : stepConfig) : null;

  const result = db.prepare(
    `INSERT INTO model_step (model_id, step_code, step_name, step_desc, step_type, sort_order, step_config,
     parent_step_id, condition_expr, timeout_seconds, retry_count, creator, updater)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).run(req.params.modelId, stepCode, stepName, stepDesc || '', stepType, newSortOrder,
        configJson, parentStepId ?? null, conditionExpr ?? null, timeoutSeconds ?? 0, retryCount ?? 0, creator, creator);

  const step = db.prepare("SELECT * FROM model_step WHERE id = ?").get(result.lastInsertRowid);
  res.json(success(toCamel(step), '插入成功'));
});

app.put('/api/v1/models/:modelId/steps/:stepId', authMiddleware, (req, res) => {
  const existing = db.prepare("SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0")
    .get(req.params.stepId, req.params.modelId);
  if (!existing) return res.status(404).json(error(404, '步骤不存在'));

  const { stepName, stepType, stepDesc, stepConfig, conditionExpr, timeoutSeconds, retryCount } = req.body;
  const configJson = stepConfig ? (typeof stepConfig === 'object' ? JSON.stringify(stepConfig) : stepConfig) : existing.step_config;

  db.prepare(
    `UPDATE model_step SET step_name = ?, step_type = ?, step_desc = ?, step_config = ?,
     condition_expr = ?, timeout_seconds = ?, retry_count = ?,
     updater = ?, update_time = datetime('now') WHERE id = ?`
  ).run(stepName || existing.step_name, stepType || existing.step_type,
        stepDesc !== undefined ? stepDesc : existing.step_desc, configJson,
        conditionExpr !== undefined ? conditionExpr : existing.condition_expr,
        timeoutSeconds !== undefined ? timeoutSeconds : existing.timeout_seconds,
        retryCount !== undefined ? retryCount : existing.retry_count,
        req.user.username, req.params.stepId);

  res.json(success(null, '更新成功'));
});

app.delete('/api/v1/models/:modelId/steps/:stepId', authMiddleware, (req, res) => {
  const step = db.prepare("SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0")
    .get(req.params.stepId, req.params.modelId);
  if (!step) return res.status(404).json(error(404, '步骤不存在'));

  db.prepare("UPDATE model_step SET deleted = 1, update_time = datetime('now') WHERE id = ?").run(req.params.stepId);

  // 不调整 sort_order，避免批量偏移导致已有顺序错乱
  // INTEGER 范围 ±9e18 足够大，小间隔不会溢出

  res.json(success(null, '删除成功'));
});

app.patch('/api/v1/models/:modelId/steps/:stepId/reorder', authMiddleware, (req, res) => {
  const { targetAfterStepId } = req.body;
  const step = db.prepare("SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0")
    .get(req.params.stepId, req.params.modelId);
  if (!step) return res.status(404).json(error(404, '步骤不存在'));

  const targetAfterRaw = db.prepare("SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0")
    .get(targetAfterStepId, req.params.modelId);
  if (!targetAfterRaw) return res.status(400).json(error(400, '目标位置不存在'));
  const targetAfter = toCamel(targetAfterRaw);

  const targetBeforeRaw = db.prepare(
    "SELECT * FROM model_step WHERE model_id = ? AND deleted = 0 AND sort_order > ? ORDER BY sort_order LIMIT 1"
  ).get(req.params.modelId, targetAfter.sortOrder);
  const targetBefore = targetBeforeRaw ? toCamel(targetBeforeRaw) : null;

  let newSortOrder;
  if (!targetBefore) {
    newSortOrder = targetAfter.sortOrder + 1000;
  } else {
    newSortOrder = Math.floor((targetAfter.sortOrder + targetBefore.sortOrder) / 2);
  }

  db.prepare("UPDATE model_step SET sort_order = ?, update_time = datetime('now') WHERE id = ?").run(newSortOrder, req.params.stepId);
  res.json(success(null, '重排成功'));
});

app.patch('/api/v1/models/:modelId/steps/:stepId/swap', authMiddleware, (req, res) => {
  const { swapWithStepId } = req.body;
  const step1Raw = db.prepare("SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0")
    .get(req.params.stepId, req.params.modelId);
  const step2Raw = db.prepare("SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0")
    .get(swapWithStepId, req.params.modelId);

  if (!step1Raw || !step2Raw) return res.status(404).json(error(404, '步骤不存在'));

  // Get all steps ordered by current position, swap the two in the array, then renumber
  const allSteps = db.prepare(
    "SELECT * FROM model_step WHERE model_id = ? AND deleted = 0 ORDER BY sort_order, id"
  ).all(req.params.modelId);

  // Find and swap positions in array
  const idx1 = allSteps.findIndex(s => s.id === step1Raw.id);
  const idx2 = allSteps.findIndex(s => s.id === step2Raw.id);
  [allSteps[idx1], allSteps[idx2]] = [allSteps[idx2], allSteps[idx1]];

  // Renumber based on new array order
  allSteps.forEach((s, i) => {
    db.prepare("UPDATE model_step SET sort_order = ?, update_time = datetime('now') WHERE id = ?")
      .run(1000 + i * 1000, s.id);
  });

  res.json(success(null, '交换成功'));
});

app.get('/api/v1/models/:modelId/steps/tree', authMiddleware, (req, res) => {
  const steps = db.prepare(
    "SELECT * FROM model_step WHERE model_id = ? AND deleted = 0 ORDER BY sort_order"
  ).all(req.params.modelId);

  const nodes = steps.map((step, index) => {
    let config = null;
    if (step.step_config) {
      try { config = JSON.parse(step.step_config); } catch {}
    }
    return {
      id: step.id,
      stepName: step.step_name,
      stepType: step.step_type,
      x: 250,
      y: 50 + index * 100,
      config
    };
  });

  const edges = [];
  for (let i = 0; i < steps.length - 1; i++) {
    edges.push({
      source: steps[i].id,
      target: steps[i + 1].id,
      label: steps[i].condition_expr || '下一步'
    });
  }

  res.json(success({ nodes, edges }));
});

// Start server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Backend running on http://0.0.0.0:${PORT}`);
  console.log(`Default login: admin / admin123`);
});
