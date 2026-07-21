-- MySQL Schema for Self-Service Modeling Platform

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username        VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password        VARCHAR(255) NOT NULL COMMENT '密码',
    nickname        VARCHAR(50) COMMENT '昵称',
    email           VARCHAR(100) COMMENT '邮箱',
    phone           VARCHAR(20) COMMENT '手机号',
    avatar          VARCHAR(255) COMMENT '头像URL',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    creator         VARCHAR(50) COMMENT '创建人',
    create_time     DATETIME NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updater         VARCHAR(50) COMMENT '更新人',
    update_time     DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',
    deleted         TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    INDEX idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS model_info (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    model_code    VARCHAR(50) NOT NULL UNIQUE COMMENT '模型编码',
    model_name    VARCHAR(100) NOT NULL COMMENT '模型名称',
    model_desc    TEXT COMMENT '模型描述',
    model_type    VARCHAR(50) COMMENT '模型类型',
    data_source   VARCHAR(50) NOT NULL DEFAULT 'master' COMMENT '数据源标识',
    status        TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    version       INT NOT NULL DEFAULT 1 COMMENT '版本号',
    creator       VARCHAR(50) COMMENT '创建人',
    create_time   DATETIME NOT NULL DEFAULT NOW() COMMENT '创建时间',
    updater       VARCHAR(50) COMMENT '更新人',
    update_time   DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',
    deleted       TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    INDEX idx_model_info_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型信息表';

CREATE TABLE IF NOT EXISTS model_step (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    model_id          BIGINT NOT NULL COMMENT '模型ID',
    step_code         VARCHAR(50) NOT NULL UNIQUE COMMENT '步骤编码',
    step_name         VARCHAR(100) NOT NULL COMMENT '步骤名称',
    step_desc         TEXT COMMENT '步骤描述',
    step_type         VARCHAR(50) NOT NULL COMMENT '步骤类型：start-开始，end-结束，task-任务，gateway-网关，subprocess-子流程',
    sort_order        INT NOT NULL DEFAULT 0 COMMENT '排序号',
    step_config       TEXT COMMENT '步骤配置（JSON格式）',
    sql_statement     TEXT COMMENT 'SQL语句',
    result_table_name VARCHAR(100) COMMENT '结果表名',
    execute_status      VARCHAR(20) COMMENT '执行状态：pending-待执行，running-执行中，success-成功，failed-失败',
    execute_start_time DATETIME COMMENT '执行开始时间',
    execute_end_time   DATETIME COMMENT '执行结束时间',
    execute_log        TEXT COMMENT '执行日志',
    create_time       DATETIME NOT NULL DEFAULT NOW() COMMENT '创建时间',
    update_time       DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW() COMMENT '更新时间',
    INDEX idx_model_step_model_id (model_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型步骤表';

-- 仅用于本地开发的默认用户 (用户名: admin, 初始密码: admin123，数据库中仅保存 BCrypt 哈希)
INSERT IGNORE INTO sys_user (id, username, password, nickname, status, creator)
VALUES (1, 'admin', '$2a$10$WDGGTRXM3dkEaJH.q99DwuFVmOx4n7UjdRXq2/2X/Ue../h1hwzvm', '管理员', 1, 'system');
