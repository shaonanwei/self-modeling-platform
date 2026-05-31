#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Self-Service Modeling Platform - Python Backend
Flask + SQLite + JWT
"""

import sqlite3
import json
import re
import hashlib
import secrets
from datetime import datetime, timedelta
from functools import wraps
from flask import Flask, request, jsonify, g
from flask_cors import CORS
import jwt
import bcrypt

app = Flask(__name__)
CORS(app)

# Configuration
DATABASE = 'selfmodeling_python.db'
JWT_SECRET = 'self-modeling-platform-jwt-secret-key-256bits'
JWT_ALGORITHM = 'HS256'
ACCESS_TOKEN_EXPIRY = timedelta(minutes=30)
REFRESH_TOKEN_EXPIRY = timedelta(days=7)

# ====== Database ======

def get_db():
    if 'db' not in g:
        g.db = sqlite3.connect(DATABASE)
        g.db.row_factory = sqlite3.Row
    return g.db

@app.teardown_appcontext
def close_db(exception):
    db = g.pop('db', None)
    if db is not None:
        db.close()

def init_db():
    db = sqlite3.connect(DATABASE)
    db.executescript('''
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
    ''')
    
    # Seed default admin user
    cursor = db.execute("SELECT id FROM sys_user WHERE username = ?", ('admin',))
    if not cursor.fetchone():
        hashed = bcrypt.hashpw('admin123'.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
        db.execute(
            "INSERT INTO sys_user (username, password, nickname, status) VALUES (?, ?, ?, ?)",
            ('admin', hashed, '系统管理员', 1)
        )
    
    db.commit()
    db.close()

# ====== Helpers ======

def success(data, message='success'):
    return {
        'code': 200,
        'message': message,
        'data': data,
        'timestamp': int(datetime.now().timestamp() * 1000)
    }

def error(code, message):
    return {
        'code': code,
        'message': message,
        'data': None,
        'timestamp': int(datetime.now().timestamp() * 1000)
    }

def to_camel(snake_str):
    """Convert snake_case to camelCase"""
    components = snake_str.split('_')
    return components[0] + ''.join(x.title() for x in components[1:])

def row_to_dict(row):
    """Convert sqlite3.Row to dict with camelCase keys"""
    if row is None:
        return None
    return {to_camel(key): value for key, value in dict(row).items()}

def rows_to_list(rows):
    """Convert list of sqlite3.Row to list of dicts with camelCase keys"""
    return [row_to_dict(row) for row in rows]

def generate_model_code(name):
    """Generate model code from name"""
    if not name:
        return f'MODEL_{datetime.now().strftime("%Y%m%d%H%M%S")}'
    # Remove special characters, keep Chinese and alphanumeric
    clean = re.sub(r'[^\w\u4e00-\u9fa5]', '', name)
    code = clean[:10].upper()
    return f'{code}_{datetime.now().strftime("%Y%m%d%H%M%S")}'

def generate_step_code(model_id):
    """Generate step code"""
    return f'STEP_{model_id}_{datetime.now().strftime("%Y%m%d%H%M%S")}'

# ====== Auth ======

def auth_required(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        auth_header = request.headers.get('Authorization')
        if not auth_header or not auth_header.startswith('Bearer '):
            return jsonify(error(401, '未授权')), 401
        
        try:
            token = auth_header.split(' ')[1]
            payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
            g.current_user = payload
        except jwt.ExpiredSignatureError:
            return jsonify(error(401, 'Token 已过期')), 401
        except jwt.InvalidTokenError:
            return jsonify(error(401, 'Token 无效')), 401
        
        return f(*args, **kwargs)
    return decorated_function

# ====== Auth APIs ======

@app.route('/api/v1/auth/login', methods=['POST'])
def login():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    
    if not username or not password:
        return jsonify(error(400, '用户名和密码不能为空')), 400
    
    db = get_db()
    user = db.execute(
        "SELECT * FROM sys_user WHERE username = ? AND deleted = 0",
        (username,)
    ).fetchone()
    
    if not user or not bcrypt.checkpw(password.encode('utf-8'), user['password'].encode('utf-8')):
        return jsonify(error(400, '用户名或密码错误')), 400
    
    if user['status'] != 1:
        return jsonify(error(400, '账号已被禁用')), 400
    
    # Generate tokens
    access_token = jwt.encode({
        'userId': user['id'],
        'username': user['username'],
        'exp': datetime.now() + ACCESS_TOKEN_EXPIRY
    }, JWT_SECRET, algorithm=JWT_ALGORITHM)
    
    refresh_token = jwt.encode({
        'userId': user['id'],
        'exp': datetime.now() + REFRESH_TOKEN_EXPIRY
    }, JWT_SECRET, algorithm=JWT_ALGORITHM)
    
    return jsonify(success({
        'accessToken': access_token,
        'refreshToken': refresh_token,
        'expiresIn': 1800,
        'tokenType': 'Bearer'
    }, '登录成功'))

@app.route('/api/v1/auth/logout', methods=['POST'])
@auth_required
def logout():
    return jsonify(success(None, '登出成功'))

@app.route('/api/v1/auth/refresh', methods=['POST'])
def refresh():
    data = request.get_json()
    refresh_token = data.get('refreshToken')
    
    if not refresh_token:
        return jsonify(error(400, '缺少 refreshToken')), 400
    
    try:
        payload = jwt.decode(refresh_token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        user_id = payload['userId']
        
        db = get_db()
        user = db.execute(
            "SELECT * FROM sys_user WHERE id = ? AND deleted = 0",
            (user_id,)
        ).fetchone()
        
        if not user or user['status'] != 1:
            return jsonify(error(400, '用户不存在或已被禁用')), 400
        
        new_access = jwt.encode({
            'userId': user['id'],
            'username': user['username'],
            'exp': datetime.now() + ACCESS_TOKEN_EXPIRY
        }, JWT_SECRET, algorithm=JWT_ALGORITHM)
        
        new_refresh = jwt.encode({
            'userId': user['id'],
            'exp': datetime.now() + REFRESH_TOKEN_EXPIRY
        }, JWT_SECRET, algorithm=JWT_ALGORITHM)
        
        return jsonify(success({
            'accessToken': new_access,
            'refreshToken': new_refresh,
            'expiresIn': 1800,
            'tokenType': 'Bearer'
        }, '刷新成功'))
    except jwt.ExpiredSignatureError:
        return jsonify(error(400, 'Token 已过期')), 400
    except jwt.InvalidTokenError:
        return jsonify(error(400, 'Token 无效')), 400

@app.route('/api/v1/auth/userinfo', methods=['GET'])
@auth_required
def userinfo():
    db = get_db()
    user = db.execute(
        "SELECT id, username, nickname, email, phone, avatar, status FROM sys_user WHERE id = ?",
        (g.current_user['userId'],)
    ).fetchone()
    
    if not user:
        return jsonify(error(404, '用户不存在')), 404
    
    return jsonify(success(row_to_dict(user)))

# ====== Model APIs ======

@app.route('/api/v1/models', methods=['GET'])
@auth_required
def get_models():
    page_num = request.args.get('pageNum', 1, type=int)
    page_size = request.args.get('pageSize', 10, type=int)
    model_name = request.args.get('modelName', '')
    status = request.args.get('status', type=int)
    
    db = get_db()
    
    # Build query
    where_clauses = ["deleted = 0"]
    params = []
    
    if model_name:
        where_clauses.append("model_name LIKE ?")
        params.append(f'%{model_name}%')
    
    if status is not None:
        where_clauses.append("status = ?")
        params.append(status)
    
    where_sql = " AND ".join(where_clauses)
    
    # Count total
    total = db.execute(
        f"SELECT COUNT(*) as total FROM model_info WHERE {where_sql}",
        params
    ).fetchone()['total']
    
    # Get list
    offset = (page_num - 1) * page_size
    rows = db.execute(
        f"SELECT * FROM model_info WHERE {where_sql} ORDER BY create_time DESC LIMIT ? OFFSET ?",
        params + [page_size, offset]
    ).fetchall()
    
    return jsonify(success({
        'total': total,
        'list': rows_to_list(rows)
    }))

@app.route('/api/v1/models/<int:model_id>', methods=['GET'])
@auth_required
def get_model(model_id):
    db = get_db()
    model = db.execute(
        "SELECT * FROM model_info WHERE id = ? AND deleted = 0",
        (model_id,)
    ).fetchone()
    
    if not model:
        return jsonify(error(404, '建模不存在')), 404
    
    return jsonify(success(row_to_dict(model)))

@app.route('/api/v1/models', methods=['POST'])
@auth_required
def create_model():
    data = request.get_json()
    model_code = data.get('modelCode', '')
    model_name = data.get('modelName')
    model_desc = data.get('modelDesc', '')
    model_type = data.get('modelType', '')
    status = data.get('status', 1)
    
    if not model_name:
        return jsonify(error(400, '模型名称不能为空')), 400
    
    if not model_code:
        model_code = generate_model_code(model_name)
    
    creator = g.current_user.get('username', 'admin')
    
    db = get_db()
    cursor = db.execute(
        """INSERT INTO model_info 
           (model_code, model_name, model_desc, model_type, status, version, creator, updater)
           VALUES (?, ?, ?, ?, ?, 1, ?, ?)""",
        (model_code, model_name, model_desc, model_type, status, creator, creator)
    )
    db.commit()
    
    model = db.execute("SELECT * FROM model_info WHERE id = ?", (cursor.lastrowid,)).fetchone()
    return jsonify(success(row_to_dict(model), '创建成功'))

@app.route('/api/v1/models/<int:model_id>', methods=['PUT'])
@auth_required
def update_model(model_id):
    data = request.get_json()
    
    db = get_db()
    existing = db.execute(
        "SELECT * FROM model_info WHERE id = ? AND deleted = 0",
        (model_id,)
    ).fetchone()
    
    if not existing:
        return jsonify(error(404, '建模不存在')), 404
    
    model_name = data.get('modelName', existing['model_name'])
    model_desc = data.get('modelDesc', existing['model_desc'])
    model_type = data.get('modelType', existing['model_type'])
    status = data.get('status', existing['status'])
    updater = g.current_user.get('username', 'admin')
    
    db.execute(
        """UPDATE model_info 
           SET model_name = ?, model_desc = ?, model_type = ?, status = ?, 
               version = version + 1, updater = ?, update_time = datetime('now')
           WHERE id = ?""",
        (model_name, model_desc, model_type, status, updater, model_id)
    )
    db.commit()
    
    updated = db.execute("SELECT * FROM model_info WHERE id = ?", (model_id,)).fetchone()
    return jsonify(success(row_to_dict(updated), '更新成功'))

@app.route('/api/v1/models/<int:model_id>', methods=['DELETE'])
@auth_required
def delete_model(model_id):
    db = get_db()
    existing = db.execute(
        "SELECT * FROM model_info WHERE id = ? AND deleted = 0",
        (model_id,)
    ).fetchone()
    
    if not existing:
        return jsonify(error(404, '建模不存在')), 404
    
    db.execute("UPDATE model_info SET deleted = 1, update_time = datetime('now') WHERE id = ?", (model_id,))
    db.execute("UPDATE model_step SET deleted = 1 WHERE model_id = ?", (model_id,))
    db.commit()
    
    return jsonify(success(None, '删除成功'))

@app.route('/api/v1/models/<int:model_id>/status', methods=['PATCH'])
@auth_required
def update_model_status(model_id):
    data = request.get_json()
    status = data.get('status')
    
    db = get_db()
    db.execute(
        "UPDATE model_info SET status = ?, update_time = datetime('now') WHERE id = ?",
        (status, model_id)
    )
    db.commit()
    
    return jsonify(success(None, '状态更新成功'))

@app.route('/api/v1/models/<int:model_id>/copy', methods=['POST'])
@auth_required
def copy_model(model_id):
    db = get_db()
    original = db.execute(
        "SELECT * FROM model_info WHERE id = ? AND deleted = 0",
        (model_id,)
    ).fetchone()
    
    if not original:
        return jsonify(error(404, '建模不存在')), 404
    
    model_code = f"{original['model_code']}_COPY_{datetime.now().strftime('%Y%m%d%H%M%S')}"
    creator = g.current_user.get('username', 'admin')
    
    cursor = db.execute(
        """INSERT INTO model_info 
           (model_code, model_name, model_desc, model_type, status, creator, updater)
           VALUES (?, ?, ?, ?, 0, ?, ?)""",
        (model_code, f"{original['model_name']} (副本)", original['model_desc'], 
         original['model_type'], creator, creator)
    )
    new_id = cursor.lastrowid
    
    # Copy steps
    steps = db.execute(
        "SELECT * FROM model_step WHERE model_id = ? AND deleted = 0 ORDER BY sort_order",
        (model_id,)
    ).fetchall()
    
    for step in steps:
        db.execute(
            """INSERT INTO model_step 
               (model_id, step_code, step_name, step_desc, step_type, sort_order, step_config,
                parent_step_id, condition_expr, timeout_seconds, retry_count, creator, updater)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (new_id, generate_step_code(new_id), step['step_name'], step['step_desc'],
             step['step_type'], step['sort_order'], step['step_config'], step['parent_step_id'],
             step['condition_expr'], step['timeout_seconds'], step['retry_count'], creator, creator)
        )
    
    db.commit()
    model = db.execute("SELECT * FROM model_info WHERE id = ?", (new_id,)).fetchone()
    return jsonify(success(row_to_dict(model), '复制成功'))

# ====== Step APIs ======

@app.route('/api/v1/models/<int:model_id>/steps', methods=['GET'])
@auth_required
def get_steps(model_id):
    db = get_db()
    steps = db.execute(
        "SELECT * FROM model_step WHERE model_id = ? AND deleted = 0 ORDER BY sort_order",
        (model_id,)
    ).fetchall()
    
    result = rows_to_list(steps)
    for step in result:
        if step.get('stepConfig'):
            try:
                step['stepConfig'] = json.loads(step['stepConfig'])
            except:
                pass
    
    return jsonify(success(result))

@app.route('/api/v1/models/<int:model_id>/steps/<int:step_id>', methods=['GET'])
@auth_required
def get_step(model_id, step_id):
    db = get_db()
    step = db.execute(
        "SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0",
        (step_id, model_id)
    ).fetchone()
    
    if not step:
        return jsonify(error(404, '步骤不存在')), 404
    
    result = row_to_dict(step)
    if result.get('stepConfig'):
        try:
            result['stepConfig'] = json.loads(result['stepConfig'])
        except:
            pass
    
    return jsonify(success(result))

@app.route('/api/v1/models/<int:model_id>/steps', methods=['POST'])
@auth_required
def add_step(model_id):
    data = request.get_json()
    step_name = data.get('stepName')
    step_type = data.get('stepType')
    step_desc = data.get('stepDesc', '')
    step_config = data.get('stepConfig')
    parent_step_id = data.get('parentStepId')
    condition_expr = data.get('conditionExpr')
    timeout_seconds = data.get('timeoutSeconds', 0)
    retry_count = data.get('retryCount', 0)
    
    if not step_name or not step_type:
        return jsonify(error(400, '步骤名称和类型不能为空')), 400
    
    db = get_db()
    last_step = db.execute(
        "SELECT sort_order FROM model_step WHERE model_id = ? AND deleted = 0 ORDER BY sort_order DESC LIMIT 1",
        (model_id,)
    ).fetchone()
    
    sort_order = (last_step['sort_order'] + 1000) if last_step else 1000
    step_code = generate_step_code(model_id)
    creator = g.current_user.get('username', 'admin')
    config_json = json.dumps(step_config) if step_config else None
    
    cursor = db.execute(
        """INSERT INTO model_step 
           (model_id, step_code, step_name, step_desc, step_type, sort_order, step_config,
            parent_step_id, condition_expr, timeout_seconds, retry_count, creator, updater)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (model_id, step_code, step_name, step_desc, step_type, sort_order, config_json,
         parent_step_id, condition_expr, timeout_seconds, retry_count, creator, creator)
    )
    db.commit()
    
    step = db.execute("SELECT * FROM model_step WHERE id = ?", (cursor.lastrowid,)).fetchone()
    return jsonify(success(row_to_dict(step), '添加成功'))

@app.route('/api/v1/models/<int:model_id>/steps/insert', methods=['POST'])
@auth_required
def insert_step(model_id):
    data = request.get_json()
    after_step_id = data.get('afterStepId')
    step_name = data.get('stepName')
    step_type = data.get('stepType')
    step_desc = data.get('stepDesc', '')
    step_config = data.get('stepConfig')
    parent_step_id = data.get('parentStepId')
    condition_expr = data.get('conditionExpr')
    timeout_seconds = data.get('timeoutSeconds', 0)
    retry_count = data.get('retryCount', 0)
    
    db = get_db()
    after_step = db.execute(
        "SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0",
        (after_step_id, model_id)
    ).fetchone()
    
    if not after_step:
        return jsonify(error(400, '插入位置的前一步骤不存在')), 400
    
    before_step = db.execute(
        "SELECT * FROM model_step WHERE model_id = ? AND deleted = 0 AND sort_order > ? ORDER BY sort_order LIMIT 1",
        (model_id, after_step['sort_order'])
    ).fetchone()
    
    if not before_step:
        new_sort_order = after_step['sort_order'] + 1000
    else:
        new_sort_order = (after_step['sort_order'] + before_step['sort_order']) // 2
        if (after_step['sort_order'] + before_step['sort_order']) % 2 != 0:
            # Reorder
            db.execute(
                "UPDATE model_step SET sort_order = sort_order + 1000 WHERE model_id = ? AND deleted = 0 AND sort_order > ?",
                (model_id, after_step['sort_order'])
            )
            new_sort_order = after_step['sort_order'] + 1000
    
    step_code = generate_step_code(model_id)
    creator = g.current_user.get('username', 'admin')
    config_json = json.dumps(step_config) if step_config else None
    
    cursor = db.execute(
        """INSERT INTO model_step 
           (model_id, step_code, step_name, step_desc, step_type, sort_order, step_config,
            parent_step_id, condition_expr, timeout_seconds, retry_count, creator, updater)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (model_id, step_code, step_name, step_desc, step_type, new_sort_order, config_json,
         parent_step_id, condition_expr, timeout_seconds, retry_count, creator, creator)
    )
    db.commit()
    
    step = db.execute("SELECT * FROM model_step WHERE id = ?", (cursor.lastrowid,)).fetchone()
    return jsonify(success(row_to_dict(step), '插入成功'))

@app.route('/api/v1/models/<int:model_id>/steps/<int:step_id>', methods=['PUT'])
@auth_required
def update_step(model_id, step_id):
    data = request.get_json()
    
    db = get_db()
    existing = db.execute(
        "SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0",
        (step_id, model_id)
    ).fetchone()
    
    if not existing:
        return jsonify(error(404, '步骤不存在')), 404
    
    step_name = data.get('stepName', existing['step_name'])
    step_type = data.get('stepType', existing['step_type'])
    step_desc = data.get('stepDesc', existing['step_desc'])
    step_config = data.get('stepConfig')
    config_json = json.dumps(step_config) if step_config is not None else existing['step_config']
    condition_expr = data.get('conditionExpr', existing['condition_expr'])
    timeout_seconds = data.get('timeoutSeconds', existing['timeout_seconds'])
    retry_count = data.get('retryCount', existing['retry_count'])
    updater = g.current_user.get('username', 'admin')
    
    db.execute(
        """UPDATE model_step 
           SET step_name = ?, step_type = ?, step_desc = ?, step_config = ?,
               condition_expr = ?, timeout_seconds = ?, retry_count = ?,
               updater = ?, update_time = datetime('now')
           WHERE id = ?""",
        (step_name, step_type, step_desc, config_json, condition_expr, timeout_seconds,
         retry_count, updater, step_id)
    )
    db.commit()
    
    return jsonify(success(None, '更新成功'))

@app.route('/api/v1/models/<int:model_id>/steps/<int:step_id>', methods=['DELETE'])
@auth_required
def delete_step(model_id, step_id):
    db = get_db()
    step = db.execute(
        "SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0",
        (step_id, model_id)
    ).fetchone()
    
    if not step:
        return jsonify(error(404, '步骤不存在')), 404
    
    deleted_sort_order = step['sort_order']
    
    db.execute("UPDATE model_step SET deleted = 1, update_time = datetime('now') WHERE id = ?", (step_id,))
    db.execute(
        "UPDATE model_step SET sort_order = sort_order - 1000, update_time = datetime('now') WHERE model_id = ? AND deleted = 0 AND sort_order > ?",
        (model_id, deleted_sort_order)
    )
    db.commit()
    
    return jsonify(success(None, '删除成功'))

@app.route('/api/v1/models/<int:model_id>/steps/<int:step_id>/swap', methods=['PATCH'])
@auth_required
def swap_steps(model_id, step_id):
    data = request.get_json()
    swap_with_step_id = data.get('swapWithStepId')
    
    db = get_db()
    step1 = db.execute(
        "SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0",
        (step_id, model_id)
    ).fetchone()
    step2 = db.execute(
        "SELECT * FROM model_step WHERE id = ? AND model_id = ? AND deleted = 0",
        (swap_with_step_id, model_id)
    ).fetchone()
    
    if not step1 or not step2:
        return jsonify(error(404, '步骤不存在')), 404
    
    # Get all steps ordered by current position, swap the two in the array, then renumber
    all_steps = db.execute(
        "SELECT * FROM model_step WHERE model_id = ? AND deleted = 0 ORDER BY sort_order, id",
        (model_id,)
    ).fetchall()
    
    # Convert to list of dicts
    all_steps = [dict(s) for s in all_steps]
    
    # Find and swap positions in array
    idx1 = next(i for i, s in enumerate(all_steps) if s['id'] == step_id)
    idx2 = next(i for i, s in enumerate(all_steps) if s['id'] == swap_with_step_id)
    all_steps[idx1], all_steps[idx2] = all_steps[idx2], all_steps[idx1]
    
    # Renumber based on new array order
    for i, s in enumerate(all_steps):
        db.execute(
            "UPDATE model_step SET sort_order = ?, update_time = datetime('now') WHERE id = ?",
            (1000 + i * 1000, s['id'])
        )
    
    db.commit()
    return jsonify(success(None, '交换成功'))

@app.route('/api/v1/models/<int:model_id>/steps/tree', methods=['GET'])
@auth_required
def get_step_tree(model_id):
    db = get_db()
    steps = db.execute(
        "SELECT * FROM model_step WHERE model_id = ? AND deleted = 0 ORDER BY sort_order",
        (model_id,)
    ).fetchall()
    
    nodes = []
    edges = []
    
    for i, step in enumerate(steps):
        config = None
        if step['step_config']:
            try:
                config = json.loads(step['step_config'])
            except:
                pass
        
        nodes.append({
            'id': step['id'],
            'stepName': step['step_name'],
            'stepType': step['step_type'],
            'x': 250,
            'y': 50 + i * 100,
            'config': config
        })
        
        if i < len(steps) - 1:
            edges.append({
                'source': step['id'],
                'target': steps[i + 1]['id'],
                'label': step['condition_expr'] or '下一步'
            })
    
    return jsonify(success({'nodes': nodes, 'edges': edges}))

if __name__ == '__main__':
    init_db()
    print("Backend running on http://0.0.0.0:8080")
    print("Default login: admin / admin123")
    app.run(host='0.0.0.0', port=8080, debug=False)
