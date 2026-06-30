CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  name VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  permission_scope VARCHAR(200) NOT NULL DEFAULT 'ALL',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'permission_scope');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE sys_user ADD COLUMN permission_scope VARCHAR(200) NOT NULL DEFAULT ''ALL''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE sys_user SET permission_scope = 'ALL' WHERE permission_scope IS NULL OR permission_scope = '';

-- password for all demo users: 123456
INSERT INTO sys_user (account, password_hash, name, role, status, permission_scope) VALUES
('student001', '$2a$10$iL5V9mcmYjGSsL7EEm5Fn.9Ix8cXeLNzauCfiXcFlirkimFUvIMYC', '学生一', 'STUDENT', 'ENABLED', 'ALL'),
('teacher001', '$2a$10$iL5V9mcmYjGSsL7EEm5Fn.9Ix8cXeLNzauCfiXcFlirkimFUvIMYC', '教师一', 'TEACHER', 'ENABLED', 'ALL'),
('admin001', '$2a$10$iL5V9mcmYjGSsL7EEm5Fn.9Ix8cXeLNzauCfiXcFlirkimFUvIMYC', '教务老师一', 'EDU_ADMIN', 'ENABLED', 'ALL')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  role = VALUES(role),
  status = VALUES(status),
  permission_scope = VALUES(permission_scope);

