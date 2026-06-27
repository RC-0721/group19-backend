CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  name VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- password for all demo users: 123456
INSERT INTO sys_user (account, password_hash, name, role, status) VALUES
('student001', '$2a$10$iL5V9mcmYjGSsL7EEm5Fn.9Ix8cXeLNzauCfiXcFlirkimFUvIMYC', '学生一', 'STUDENT', 'ENABLED'),
('teacher001', '$2a$10$iL5V9mcmYjGSsL7EEm5Fn.9Ix8cXeLNzauCfiXcFlirkimFUvIMYC', '教师一', 'TEACHER', 'ENABLED'),
('admin001', '$2a$10$iL5V9mcmYjGSsL7EEm5Fn.9Ix8cXeLNzauCfiXcFlirkimFUvIMYC', '教务老师一', 'EDU_ADMIN', 'ENABLED')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  role = VALUES(role),
  status = VALUES(status);

