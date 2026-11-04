-- 七安备忘后端初始化脚本
-- 由 docker-compose 在 MySQL 首次启动时自动执行

USE qian_memo_db;

CREATE TABLE IF NOT EXISTS users (
  id           INT          NOT NULL AUTO_INCREMENT,
  email        VARCHAR(191) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at   BIGINT       NOT NULL,
  token_version INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uniq_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 后台管理员账户（独立于软件注册用户）
CREATE TABLE IF NOT EXISTS admins (
  id           INT          NOT NULL AUTO_INCREMENT,
  username     VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at   BIGINT       NOT NULL,
  token_version INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uniq_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 备忘录（与 App MemoEntity 对齐，追加 user_id / updated_at 服务端版本控制）
CREATE TABLE IF NOT EXISTS memos (
  id          VARCHAR(64) NOT NULL,
  user_id     INT         NOT NULL,
  title       TEXT        NOT NULL,
  content     MEDIUMTEXT,
  is_completed TINYINT(1) DEFAULT 0,
  is_favorite  TINYINT(1) DEFAULT 0,
  is_pinned    TINYINT(1) DEFAULT 0,
  tag_ids      TEXT,
  created_at   BIGINT      NOT NULL,
  updated_at   BIGINT      NOT NULL,
  deleted_at   BIGINT      DEFAULT NULL,
  PRIMARY KEY (id, user_id),
  KEY idx_memos_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 购物清单（与 App ShoppingItemEntity 对齐）
CREATE TABLE IF NOT EXISTS shopping_items (
  id           VARCHAR(64) NOT NULL,
  user_id      INT         NOT NULL,
  name         TEXT        NOT NULL,
  quantity     INT         DEFAULT 1,
  unit         VARCHAR(32) DEFAULT '个',
  tag_ids      TEXT,
  is_completed TINYINT(1)  DEFAULT 0,
  created_at   BIGINT      NOT NULL,
  updated_at   BIGINT      NOT NULL,
  deleted_at   BIGINT      DEFAULT NULL,
  PRIMARY KEY (id, user_id),
  KEY idx_shop_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 标签（备忘录 / 购物清单共用）
CREATE TABLE IF NOT EXISTS tags (
  id          VARCHAR(64) NOT NULL,
  user_id     INT         NOT NULL,
  name        VARCHAR(191) NOT NULL,
  color       INT         DEFAULT 0,
  sort_order  INT         DEFAULT 0,
  created_at  BIGINT      NOT NULL,
  updated_at  BIGINT      NOT NULL,
  deleted_at  BIGINT      DEFAULT NULL,
  PRIMARY KEY (id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 备忘录内清单项
CREATE TABLE IF NOT EXISTS memo_checklist_items (
  id          VARCHAR(64) NOT NULL,
  user_id     INT         NOT NULL,
  memo_id     VARCHAR(64) NOT NULL,
  content     TEXT,
  is_checked  TINYINT(1)  DEFAULT 0,
  position    INT         DEFAULT 0,
  updated_at  BIGINT      NOT NULL,
  deleted_at  BIGINT      DEFAULT NULL,
  PRIMARY KEY (id, user_id),
  KEY idx_checklist_memo (memo_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 同步日志（后台统计用：每次 push/pull 记录一条）
CREATE TABLE IF NOT EXISTS sync_logs (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     INT          NOT NULL,
  user_email  VARCHAR(191) NOT NULL,
  action      ENUM('push','pull') NOT NULL,
  n_memos     INT          DEFAULT 0,
  n_shopping  INT          DEFAULT 0,
  n_tags      INT          DEFAULT 0,
  n_checklist INT          DEFAULT 0,
  ip          VARCHAR(45)  DEFAULT '',
  created_at  BIGINT       NOT NULL,
  PRIMARY KEY (id),
  KEY idx_sync_user (user_id),
  KEY idx_sync_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 回收站：删除账户前的全量数据快照，便于运营恢复（软删替代方案）
CREATE TABLE IF NOT EXISTS deleted_users_backup (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  original_user_id  INT         NOT NULL,
  email             VARCHAR(191) NOT NULL,
  admin             VARCHAR(64) NOT NULL DEFAULT 'unknown',
  data              MEDIUMTEXT  NOT NULL,
  created_at        BIGINT      NOT NULL,
  PRIMARY KEY (id),
  KEY idx_backup_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
