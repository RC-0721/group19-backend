-- Business seed schema for course and question APIs.
-- Generated from simplified detailed design and JavaGuide minimum sample.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS data_source (
  source_id VARCHAR(64) PRIMARY KEY,
  source_name VARCHAR(100) NOT NULL,
  source_type VARCHAR(50) NOT NULL,
  source_url VARCHAR(500) NOT NULL,
  license VARCHAR(100) NOT NULL,
  usage_note TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS major (
  major_id VARCHAR(64) PRIMARY KEY,
  major_name VARCHAR(100) NOT NULL,
  major_code VARCHAR(64) NOT NULL,
  major_category VARCHAR(100),
  training_program_id VARCHAR(64),
  description TEXT,
  status VARCHAR(20) NOT NULL DEFAULT '启用',
  UNIQUE KEY uk_major_code (major_code),
  INDEX idx_major_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'major' AND COLUMN_NAME = 'major_code');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE major ADD COLUMN major_code VARCHAR(64) NOT NULL DEFAULT ''''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'major' AND COLUMN_NAME = 'description');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE major ADD COLUMN description TEXT', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'major' AND COLUMN_NAME = 'major_category');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE major ADD COLUMN major_category VARCHAR(100)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'major' AND COLUMN_NAME = 'training_program_id');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE major ADD COLUMN training_program_id VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE major SET major_code = major_id WHERE major_code IS NULL OR major_code = '';

CREATE TABLE IF NOT EXISTS course (
  course_id VARCHAR(64) PRIMARY KEY,
  course_name VARCHAR(100) NOT NULL,
  course_code VARCHAR(64),
  major_id VARCHAR(64),
  credit DECIMAL(5,2),
  course_type VARCHAR(50),
  course_goal TEXT,
  standard_id VARCHAR(64),
  status VARCHAR(20) NOT NULL DEFAULT '已发布',
  INDEX idx_course_major_status (major_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'course_code');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE course ADD COLUMN course_code VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'major_id');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE course ADD COLUMN major_id VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'credit');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE course ADD COLUMN credit DECIMAL(5,2)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE course SET course_code = course_id WHERE course_code IS NULL OR course_code = '';

CREATE TABLE IF NOT EXISTS teaching_standard (
  standard_id VARCHAR(64) PRIMARY KEY,
  major_id VARCHAR(64),
  course_id VARCHAR(64),
  standard_type VARCHAR(50) NOT NULL,
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  version VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT '启用',
  INDEX idx_teaching_standard_major (major_id, status),
  INDEX idx_teaching_standard_course (course_id, status),
  INDEX idx_teaching_standard_type (standard_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `class` (
  class_id VARCHAR(64) PRIMARY KEY,
  major_id VARCHAR(64),
  class_name VARCHAR(100) NOT NULL,
  grade VARCHAR(20),
  counselor_id VARCHAR(64),
  status VARCHAR(20) NOT NULL DEFAULT '启用',
  INDEX idx_class_major_status (major_id, status),
  INDEX idx_class_grade (grade)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_class (
  course_class_id VARCHAR(64) PRIMARY KEY,
  course_id VARCHAR(64) NOT NULL,
  class_id VARCHAR(64),
  teacher_id VARCHAR(64),
  semester VARCHAR(50),
  status VARCHAR(20) NOT NULL DEFAULT '开课中',
  INDEX idx_course_class_course (course_id),
  INDEX idx_course_class_class_teacher (class_id, teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS student_profile (
  student_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  student_no VARCHAR(64),
  major_id VARCHAR(64),
  class_id VARCHAR(64),
  target_job_id VARCHAR(64),
  enrollment_year VARCHAR(20),
  INDEX idx_student_profile_class (class_id),
  INDEX idx_student_profile_job (target_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS operation_log (
  log_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  module VARCHAR(64) NOT NULL,
  operation_type VARCHAR(64) NOT NULL,
  operation_result VARCHAR(32) NOT NULL,
  operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_operation_log_user (user_id),
  INDEX idx_operation_log_module_time (module, operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chapter (
  chapter_id VARCHAR(64) PRIMARY KEY,
  course_id VARCHAR(64) NOT NULL,
  parent_id VARCHAR(64),
  chapter_name VARCHAR(100) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT '启用',
  INDEX idx_chapter_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chapter' AND COLUMN_NAME = 'status');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE chapter ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''启用''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS course_material (
  material_id VARCHAR(64) PRIMARY KEY,
  course_id VARCHAR(64) NOT NULL,
  chapter_id VARCHAR(64),
  file_name VARCHAR(200) NOT NULL,
  file_type VARCHAR(50),
  storage_path VARCHAR(500),
  parse_status VARCHAR(20) NOT NULL DEFAULT '已发布',
  INDEX idx_material_course (course_id),
  INDEX idx_material_chapter (chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS material_parse_task (
  parse_task_id VARCHAR(64) PRIMARY KEY,
  material_id VARCHAR(64) NOT NULL,
  task_status VARCHAR(20) NOT NULL DEFAULT '解析中',
  error_message TEXT,
  started_time DATETIME,
  finished_time DATETIME,
  INDEX idx_parse_task_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pre_task (
  pre_task_id VARCHAR(64) PRIMARY KEY,
  task_id VARCHAR(64),
  course_class_id VARCHAR(64) NOT NULL,
  course_id VARCHAR(64) NOT NULL,
  chapter_id VARCHAR(64),
  title VARCHAR(200) NOT NULL,
  material_id VARCHAR(64),
  deadline DATETIME NOT NULL,
  task_type VARCHAR(50),
  status VARCHAR(20) NOT NULL DEFAULT '已发布',
  INDEX idx_pre_task_course (course_id),
  INDEX idx_pre_task_class (course_class_id),
  INDEX idx_pre_task_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pre_task' AND COLUMN_NAME = 'pre_task_id');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE pre_task ADD COLUMN pre_task_id VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pre_task' AND COLUMN_NAME = 'course_class_id');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE pre_task ADD COLUMN course_class_id VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pre_task' AND COLUMN_NAME = 'material_id');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE pre_task ADD COLUMN material_id VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pre_task' AND COLUMN_NAME = 'deadline');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE pre_task ADD COLUMN deadline DATETIME', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE pre_task pt SET pre_task_id = task_id WHERE pre_task_id IS NULL;
UPDATE pre_task pt
JOIN course_class cc ON pt.course_id = cc.course_id
SET pt.course_class_id = cc.course_class_id
WHERE pt.course_class_id IS NULL;
UPDATE pre_task SET material_id = 'material-jg-001' WHERE material_id IS NULL;
UPDATE pre_task SET deadline = '2099-12-31 23:59:59' WHERE deadline IS NULL;

CREATE TABLE IF NOT EXISTS pre_task_submit (
  submit_id VARCHAR(64) PRIMARY KEY,
  pre_task_id VARCHAR(64) NOT NULL,
  student_id VARCHAR(64) NOT NULL,
  submit_content TEXT NOT NULL,
  base_score FLOAT NOT NULL DEFAULT 0,
  weak_points TEXT,
  submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_pre_task_submit_task (pre_task_id),
  INDEX idx_pre_task_submit_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS homework (
  homework_id VARCHAR(64) PRIMARY KEY,
  course_id VARCHAR(64) NOT NULL,
  course_class_id VARCHAR(64),
  chapter_id VARCHAR(64),
  title VARCHAR(200) NOT NULL,
  submit_requirement TEXT,
  scoring_standard TEXT,
  deadline DATETIME,
  status VARCHAR(20) NOT NULL DEFAULT '已发布',
  INDEX idx_homework_course (course_id),
  INDEX idx_homework_class (course_class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'homework' AND COLUMN_NAME = 'course_class_id');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE homework ADD COLUMN course_class_id VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'homework' AND COLUMN_NAME = 'submit_requirement');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE homework ADD COLUMN submit_requirement TEXT', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'homework' AND COLUMN_NAME = 'scoring_standard');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE homework ADD COLUMN scoring_standard TEXT', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'homework' AND COLUMN_NAME = 'deadline');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE homework ADD COLUMN deadline DATETIME', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE homework h
JOIN course_class cc ON h.course_id = cc.course_id
SET h.course_class_id = cc.course_class_id
WHERE h.course_class_id IS NULL;
UPDATE homework SET submit_requirement = '按要求提交作业内容' WHERE submit_requirement IS NULL;
UPDATE homework SET scoring_standard = '教师确认分为最终分' WHERE scoring_standard IS NULL;
UPDATE homework SET deadline = '2099-12-31 23:59:59' WHERE deadline IS NULL;

CREATE TABLE IF NOT EXISTS homework_submit (
  submit_id VARCHAR(64) PRIMARY KEY,
  homework_id VARCHAR(64) NOT NULL,
  student_id VARCHAR(64) NOT NULL,
  submit_content TEXT NOT NULL,
  attachment_path VARCHAR(500) NOT NULL,
  submit_status VARCHAR(20) NOT NULL DEFAULT '待批改',
  submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_homework_submit_homework (homework_id),
  INDEX idx_homework_submit_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS homework_review (
  review_id VARCHAR(64) PRIMARY KEY,
  submit_id VARCHAR(64) NOT NULL,
  ai_score FLOAT,
  teacher_score FLOAT,
  ai_comment TEXT,
  teacher_comment TEXT,
  review_time DATETIME,
  INDEX idx_homework_review_submit (submit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_task (
  project_task_id VARCHAR(64) PRIMARY KEY,
  course_id VARCHAR(64) NOT NULL,
  job_id VARCHAR(64),
  title VARCHAR(200) NOT NULL,
  task_goal TEXT,
  tech_requirement TEXT,
  deliverable TEXT,
  status VARCHAR(20) NOT NULL DEFAULT '已发布',
  INDEX idx_project_task_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_task' AND COLUMN_NAME = 'job_id');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE project_task ADD COLUMN job_id VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_task' AND COLUMN_NAME = 'task_goal');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE project_task ADD COLUMN task_goal TEXT', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_task' AND COLUMN_NAME = 'tech_requirement');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE project_task ADD COLUMN tech_requirement TEXT', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_task' AND COLUMN_NAME = 'deliverable');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE project_task ADD COLUMN deliverable TEXT', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE project_task SET job_id = 'job-java-backend' WHERE job_id IS NULL;
UPDATE project_task SET task_goal = title WHERE task_goal IS NULL;
UPDATE project_task SET tech_requirement = '使用 Spring Boot 完成后端接口闭环' WHERE tech_requirement IS NULL;
UPDATE project_task SET deliverable = '接口代码、说明文档和验证结果' WHERE deliverable IS NULL;

CREATE TABLE IF NOT EXISTS enterprise_project_standard (
  standard_id VARCHAR(64) PRIMARY KEY,
  job_id VARCHAR(64) NOT NULL,
  evaluation_dimension VARCHAR(100) NOT NULL,
  score_level VARCHAR(50) NOT NULL,
  evidence_requirement TEXT NOT NULL,
  INDEX idx_project_standard_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rubric_dimension (
  dimension_id VARCHAR(64) PRIMARY KEY,
  standard_id VARCHAR(64) NOT NULL,
  dimension_name VARCHAR(100) NOT NULL,
  weight FLOAT NOT NULL,
  level_rule TEXT NOT NULL,
  INDEX idx_rubric_standard (standard_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_submission (
  submission_id VARCHAR(64) PRIMARY KEY,
  project_task_id VARCHAR(64) NOT NULL,
  student_id VARCHAR(64) NOT NULL,
  artifact_path VARCHAR(500) NOT NULL,
  description TEXT NOT NULL,
  submit_status VARCHAR(20) NOT NULL DEFAULT '待评价',
  submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_project_submission_task (project_task_id),
  INDEX idx_project_submission_student (student_id),
  INDEX idx_project_submission_status (submit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_evaluation (
  evaluation_id VARCHAR(64) PRIMARY KEY,
  submission_id VARCHAR(64) NOT NULL,
  rubric_id VARCHAR(64),
  ai_evaluation TEXT,
  teacher_score FLOAT,
  teacher_comment TEXT,
  confirmed_time DATETIME,
  INDEX idx_project_evaluation_submit (submission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ability_evidence (
  evidence_id VARCHAR(64) PRIMARY KEY,
  student_id VARCHAR(64) NOT NULL,
  source_type VARCHAR(50) NOT NULL,
  source_id VARCHAR(64) NOT NULL,
  knowledge_id VARCHAR(64),
  skill_id VARCHAR(64),
  score FLOAT NOT NULL,
  UNIQUE KEY uk_ability_evidence_source (source_type, source_id),
  INDEX idx_ability_evidence_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_session (
  session_id VARCHAR(64) PRIMARY KEY,
  student_id VARCHAR(64) NOT NULL,
  job_id VARCHAR(64) NOT NULL,
  scene VARCHAR(100) NOT NULL,
  model VARCHAR(100),
  prompt_version VARCHAR(50),
  status VARCHAR(20) NOT NULL DEFAULT '已创建',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ai_session_student (student_id),
  INDEX idx_ai_session_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_session' AND COLUMN_NAME = 'job_id');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE ai_session ADD COLUMN job_id VARCHAR(64) NOT NULL DEFAULT ''job-java-backend''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ai_message (
  message_id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  sender_type VARCHAR(20) NOT NULL,
  message_content TEXT NOT NULL,
  reference_chunk TEXT,
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ai_message_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_interview_report (
  report_id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  job_id VARCHAR(64) NOT NULL,
  score FLOAT NOT NULL,
  strength TEXT,
  weakness TEXT,
  suggestion TEXT,
  UNIQUE KEY uk_ai_report_session (session_id),
  INDEX idx_ai_report_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ability_profile (
  profile_id VARCHAR(64) PRIMARY KEY,
  student_id VARCHAR(64) NOT NULL,
  job_id VARCHAR(64) NOT NULL,
  profile_status VARCHAR(20) NOT NULL DEFAULT '数据不足',
  knowledge_mastery TEXT,
  skill_mastery TEXT,
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ability_profile_student_job (student_id, job_id),
  INDEX idx_ability_profile_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS learning_recommendation (
  recommend_id VARCHAR(64) PRIMARY KEY,
  student_id VARCHAR(64) NOT NULL,
  profile_id VARCHAR(64) NOT NULL,
  target_weakness TEXT,
  recommend_content TEXT,
  status VARCHAR(20) NOT NULL DEFAULT '待完成',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_recommend_profile (profile_id),
  INDEX idx_recommend_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_call_log (
  log_id VARCHAR(64) PRIMARY KEY,
  scene VARCHAR(100) NOT NULL,
  model VARCHAR(100),
  prompt_version VARCHAR(50),
  input_summary TEXT,
  output_summary TEXT,
  call_status VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_point (
  knowledge_id VARCHAR(64) PRIMARY KEY,
  course_id VARCHAR(64) NOT NULL,
  chapter_id VARCHAR(64),
  name VARCHAR(100) NOT NULL,
  description TEXT,
  level VARCHAR(50),
  source VARCHAR(100),
  audit_status VARCHAR(20) NOT NULL DEFAULT '已发布',
  INDEX idx_knowledge_course (course_id),
  INDEX idx_knowledge_chapter (chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_point' AND COLUMN_NAME = 'description');
SET @sql = IF(@column_exists = 0, 'ALTER TABLE knowledge_point ADD COLUMN description TEXT', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS knowledge_chunk (
  chunk_id VARCHAR(64) PRIMARY KEY,
  material_id VARCHAR(64) NOT NULL,
  knowledge_id VARCHAR(64),
  chunk_text TEXT NOT NULL,
  embedding_id VARCHAR(100),
  version VARCHAR(50),
  status VARCHAR(20) NOT NULL DEFAULT '待审核',
  INDEX idx_chunk_material (material_id),
  INDEX idx_chunk_knowledge (knowledge_id),
  INDEX idx_chunk_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS job_direction (
  job_id VARCHAR(64) PRIMARY KEY,
  job_name VARCHAR(100) NOT NULL,
  job_description TEXT,
  difficulty_level VARCHAR(50),
  status VARCHAR(20) NOT NULL DEFAULT '启用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tech_stack (
  tech_id VARCHAR(64) PRIMARY KEY,
  tech_name VARCHAR(100) NOT NULL,
  category VARCHAR(100),
  level VARCHAR(50),
  description TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS job_skill_standard (
  skill_id VARCHAR(64) PRIMARY KEY,
  job_id VARCHAR(64) NOT NULL,
  tech_id VARCHAR(64) NOT NULL,
  skill_name VARCHAR(100) NOT NULL,
  ability_level VARCHAR(50) NOT NULL,
  evidence_requirement TEXT NOT NULL,
  UNIQUE KEY uk_job_skill (job_id, tech_id, skill_name),
  INDEX idx_job_skill_job (job_id),
  INDEX idx_job_skill_tech (tech_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question (
  question_id VARCHAR(64) PRIMARY KEY,
  source_id VARCHAR(64),
  source_path VARCHAR(500),
  source_url VARCHAR(500),
  question_type VARCHAR(50) NOT NULL,
  stem TEXT NOT NULL,
  difficulty VARCHAR(50),
  answer TEXT,
  answer_analysis TEXT,
  audit_status VARCHAR(20) NOT NULL DEFAULT '待审核',
  INDEX idx_question_source (source_id),
  INDEX idx_question_status (audit_status),
  INDEX idx_question_type_difficulty (question_type, difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question_option (
  option_id VARCHAR(64) PRIMARY KEY,
  question_id VARCHAR(64) NOT NULL,
  option_label VARCHAR(20),
  option_content TEXT,
  is_correct BOOLEAN NOT NULL DEFAULT FALSE,
  INDEX idx_option_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question_knowledge_relation (
  relation_id VARCHAR(64) PRIMARY KEY,
  question_id VARCHAR(64) NOT NULL,
  knowledge_id VARCHAR(64) NOT NULL,
  weight FLOAT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_question_knowledge (question_id, knowledge_id),
  INDEX idx_qkr_knowledge (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question_job_relation (
  relation_id VARCHAR(64) PRIMARY KEY,
  question_id VARCHAR(64) NOT NULL,
  job_id VARCHAR(64) NOT NULL,
  tech_id VARCHAR(64) NOT NULL,
  match_level VARCHAR(50),
  UNIQUE KEY uk_question_job_tech (question_id, job_id, tech_id),
  INDEX idx_qjr_job_tech (job_id, tech_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS practice_record (
  record_id VARCHAR(64) PRIMARY KEY,
  student_id VARCHAR(64) NOT NULL,
  question_id VARCHAR(64) NOT NULL,
  answer TEXT,
  is_correct BOOLEAN NOT NULL DEFAULT FALSE,
  score FLOAT NOT NULL DEFAULT 0,
  submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_practice_student (student_id),
  INDEX idx_practice_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wrong_book (
  wrong_id VARCHAR(64) PRIMARY KEY,
  student_id VARCHAR(64) NOT NULL,
  question_id VARCHAR(64) NOT NULL,
  wrong_count INT NOT NULL DEFAULT 1,
  last_wrong_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  master_status VARCHAR(20) NOT NULL DEFAULT '未掌握',
  UNIQUE KEY uk_wrong_student_question (student_id, question_id),
  INDEX idx_wrong_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO data_source (source_id, source_name, source_type, source_url, license, usage_note) VALUES
  ('source-javaguide', 'JavaGuide', 'GitHub Repository', 'https://github.com/Snailclimb/JavaGuide', 'Apache-2.0', 'Keep source URL and license when displaying imported question records.')
ON DUPLICATE KEY UPDATE source_name=VALUES(source_name), source_type=VALUES(source_type), source_url=VALUES(source_url), license=VALUES(license), usage_note=VALUES(usage_note);

INSERT INTO major (major_id, major_name, major_code, major_category, training_program_id, description, status) VALUES
  ('major-cs', '计算机科学与技术', 'CS', '计算机类', NULL, '面向软件开发、数据处理和后端工程能力培养的示例专业。', '启用')
ON DUPLICATE KEY UPDATE major_name=VALUES(major_name), major_code=VALUES(major_code), major_category=VALUES(major_category), description=VALUES(description), status=VALUES(status);

INSERT INTO course (course_id, course_name, course_code, major_id, credit, course_type, course_goal, standard_id, status) VALUES
  ('course-java-001', 'Java 后端就业能力课程', 'JAVA-BACKEND-001', 'major-cs', 3.0, '就业实训', '面向计软本科生就业准备，覆盖 Java 基础、数据库、缓存、网络、操作系统和后端框架核心知识。', 'standard-java-backend', '已发布')
ON DUPLICATE KEY UPDATE course_name=VALUES(course_name), course_code=VALUES(course_code), major_id=VALUES(major_id), credit=VALUES(credit), course_type=VALUES(course_type), course_goal=VALUES(course_goal), status=VALUES(status);

INSERT INTO teaching_standard (standard_id, major_id, course_id, standard_type, title, content, version, status) VALUES
  ('standard-java-backend', 'major-cs', 'course-java-001', '课程目标', 'Java 后端就业能力课程目标', '覆盖 Java 基础、数据库、缓存、网络、操作系统和后端框架核心知识，支撑学生完成后端就业能力训练。', '2026', '启用')
ON DUPLICATE KEY UPDATE major_id=VALUES(major_id), course_id=VALUES(course_id), standard_type=VALUES(standard_type), title=VALUES(title), content=VALUES(content), version=VALUES(version), status=VALUES(status);

INSERT INTO `class` (class_id, major_id, class_name, grade, counselor_id, status) VALUES
  ('class-cs-2026', 'major-cs', '计软 2026 班', '2026', NULL, '启用')
ON DUPLICATE KEY UPDATE major_id=VALUES(major_id), class_name=VALUES(class_name), grade=VALUES(grade), counselor_id=VALUES(counselor_id), status=VALUES(status);

INSERT INTO course_class (course_class_id, course_id, class_id, teacher_id, semester, status) VALUES
  ('class-java-001', 'course-java-001', 'class-cs-2026', 'teacher001', '2026 春季', '开课中')
ON DUPLICATE KEY UPDATE course_id=VALUES(course_id), class_id=VALUES(class_id), teacher_id=VALUES(teacher_id), semester=VALUES(semester), status=VALUES(status);

INSERT INTO student_profile (student_id, user_id, student_no, major_id, class_id, target_job_id, enrollment_year) VALUES
  ('student001', 'student001', '2026001', 'major-cs', 'class-cs-2026', 'job-java-backend', '2026')
ON DUPLICATE KEY UPDATE user_id=VALUES(user_id), student_no=VALUES(student_no), major_id=VALUES(major_id), class_id=VALUES(class_id), target_job_id=VALUES(target_job_id), enrollment_year=VALUES(enrollment_year);

INSERT INTO chapter (chapter_id, course_id, parent_id, chapter_name, sort_order, status) VALUES
  ('chapter-java-001', 'course-java-001', NULL, 'Java 基础与面向对象', '1', '启用'),
  ('chapter-db-001', 'course-java-001', NULL, '数据库与缓存基础', '2', '启用'),
  ('chapter-cs-001', 'course-java-001', NULL, '计算机基础与网络', '3', '启用')
ON DUPLICATE KEY UPDATE chapter_name=VALUES(chapter_name), sort_order=VALUES(sort_order), status=VALUES(status);

INSERT INTO course_material (material_id, course_id, chapter_id, file_name, file_type, storage_path, parse_status) VALUES
  ('material-jg-001', 'course-java-001', 'chapter-java-001', 'JavaGuide Java 基础题目', 'markdown', 'data/Apache-2.0/JavaGuide/docs/java', '已发布'),
  ('material-jg-002', 'course-java-001', 'chapter-db-001', 'JavaGuide 数据库题目', 'markdown', 'data/Apache-2.0/JavaGuide/docs/database', '已发布')
ON DUPLICATE KEY UPDATE file_name=VALUES(file_name), file_type=VALUES(file_type), storage_path=VALUES(storage_path), parse_status=VALUES(parse_status);

INSERT INTO material_parse_task (parse_task_id, material_id, task_status, error_message, started_time, finished_time) VALUES
  ('parse-jg-001', 'material-jg-001', '已完成', NULL, NOW(), NOW()),
  ('parse-jg-002', 'material-jg-002', '已完成', NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE task_status=VALUES(task_status), error_message=VALUES(error_message), finished_time=VALUES(finished_time);

INSERT INTO pre_task (pre_task_id, task_id, course_class_id, course_id, chapter_id, title, material_id, deadline, task_type, status) VALUES
  ('pre-java-001', 'pre-java-001', 'class-java-001', 'course-java-001', 'chapter-java-001', '阅读 Java 基础与面向对象知识点', 'material-jg-001', '2099-12-31 23:59:59', '课前任务', '已发布')
ON DUPLICATE KEY UPDATE title=VALUES(title), material_id=VALUES(material_id), deadline=VALUES(deadline), task_type=VALUES(task_type), status=VALUES(status);

INSERT INTO homework (homework_id, course_id, course_class_id, chapter_id, title, submit_requirement, scoring_standard, deadline, status) VALUES
  ('hw-java-001', 'course-java-001', 'class-java-001', 'chapter-java-001', 'Java 基础简答题练习', '围绕课程资料完成简答题作业。', '内容完整、表达清晰，教师确认分为最终分。', '2099-12-31 23:59:59', '已发布')
ON DUPLICATE KEY UPDATE title=VALUES(title), course_class_id=VALUES(course_class_id), submit_requirement=VALUES(submit_requirement), scoring_standard=VALUES(scoring_standard), deadline=VALUES(deadline), status=VALUES(status);

INSERT INTO project_task (project_task_id, course_id, job_id, title, task_goal, tech_requirement, deliverable, status) VALUES
  ('project-java-001', 'course-java-001', 'job-java-backend', 'Spring Boot 课程问答 API 最小项目', '完成课程问答 API 最小闭环', '使用 Spring Boot 实现接口、鉴权和数据落库', '接口代码、说明文档和验证结果', '已发布')
ON DUPLICATE KEY UPDATE job_id=VALUES(job_id), title=VALUES(title), task_goal=VALUES(task_goal), tech_requirement=VALUES(tech_requirement), deliverable=VALUES(deliverable), status=VALUES(status);

INSERT INTO knowledge_point (knowledge_id, course_id, chapter_id, name, description, level, source, audit_status) VALUES
  ('kp-001', 'course-java-001', 'chapter-java-001', 'Java 基础', 'Java 语言特点、字节码和基础语法。', '基础', 'JavaGuide', '已发布'),
  ('kp-002', 'course-java-001', 'chapter-java-001', '面向对象', '封装、继承、多态和接口抽象。', '基础', 'JavaGuide', '已发布'),
  ('kp-003', 'course-java-001', 'chapter-java-001', 'Java 进阶', 'JVM、异常、泛型和常用 API。', '基础', 'JavaGuide', '已发布'),
  ('kp-004', 'course-java-001', 'chapter-java-001', '集合框架', 'List、Map、Set 和常见集合实现。', '基础', 'JavaGuide', '已发布'),
  ('kp-005', 'course-java-001', 'chapter-java-001', '并发编程', '线程、锁、线程池和并发工具。', '基础', 'JavaGuide', '已发布'),
  ('kp-006', 'course-java-001', 'chapter-db-001', 'MySQL 基础', '字段类型、事务、索引和 SQL 基础。', '基础', 'JavaGuide', '已发布'),
  ('kp-007', 'course-java-001', 'chapter-db-001', '缓存', 'Redis 缓存、淘汰策略和常见使用场景。', '基础', 'JavaGuide', '已发布'),
  ('kp-008', 'course-java-001', 'chapter-db-001', '网络协议', 'HTTP、TCP/IP 和常见网络基础。', '基础', 'JavaGuide', '已发布')
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), level=VALUES(level), source=VALUES(source), audit_status=VALUES(audit_status);

INSERT INTO knowledge_chunk (chunk_id, material_id, knowledge_id, chunk_text, embedding_id, version, status) VALUES
  ('chunk-jg-001', 'material-jg-001', 'kp-001', 'Java 基础课程资料覆盖 Java 语言特点、字节码、面向对象、集合和并发等核心知识。', NULL, 'v1', '已发布'),
  ('chunk-jg-002', 'material-jg-002', 'kp-006', '数据库课程资料覆盖 MySQL 基础、字段类型、事务、索引和 Redis 缓存等核心知识。', NULL, 'v1', '已发布')
ON DUPLICATE KEY UPDATE chunk_text=VALUES(chunk_text), knowledge_id=VALUES(knowledge_id), version=VALUES(version), status=VALUES(status);

INSERT INTO job_direction (job_id, job_name, job_description, difficulty_level, status) VALUES
  ('job-java-backend', 'Java 后端开发工程师', '面向 Java Web、数据库、缓存、网络和后端框架的就业方向。', '初中级', '启用')
ON DUPLICATE KEY UPDATE job_name=VALUES(job_name), job_description=VALUES(job_description), difficulty_level=VALUES(difficulty_level), status=VALUES(status);

INSERT INTO enterprise_project_standard (standard_id, job_id, evaluation_dimension, score_level, evidence_requirement) VALUES
  ('standard-java-project-001', 'job-java-backend', '后端项目交付质量', '优秀/合格/需改进', '提交可运行接口、测试结果和关键说明')
ON DUPLICATE KEY UPDATE job_id=VALUES(job_id), evaluation_dimension=VALUES(evaluation_dimension), score_level=VALUES(score_level), evidence_requirement=VALUES(evidence_requirement);

INSERT INTO rubric_dimension (dimension_id, standard_id, dimension_name, weight, level_rule) VALUES
  ('rubric-java-project-001', 'standard-java-project-001', '接口闭环完整度', 1.0, '按接口可用性、权限校验、状态流转和验证结果综合评价')
ON DUPLICATE KEY UPDATE standard_id=VALUES(standard_id), dimension_name=VALUES(dimension_name), weight=VALUES(weight), level_rule=VALUES(level_rule);

INSERT INTO tech_stack (tech_id, tech_name, category, level, description) VALUES
  ('tech-001', 'Java', '后端开发', '基础', 'Java 相关知识与面试题'),
  ('tech-002', 'Java 集合', '后端开发', '基础', 'Java 集合 相关知识与面试题'),
  ('tech-003', 'Java 并发', '后端开发', '基础', 'Java 并发 相关知识与面试题'),
  ('tech-004', 'MySQL', '后端开发', '基础', 'MySQL 相关知识与面试题'),
  ('tech-005', 'Redis', '后端开发', '基础', 'Redis 相关知识与面试题'),
  ('tech-006', 'HTTP', '后端开发', '进阶', 'HTTP 相关知识与面试题')
ON DUPLICATE KEY UPDATE tech_name=VALUES(tech_name), category=VALUES(category), level=VALUES(level), description=VALUES(description);

INSERT INTO job_skill_standard (skill_id, job_id, tech_id, skill_name, ability_level, evidence_requirement) VALUES
  ('skill-java-001', 'job-java-backend', 'tech-001', 'Java 语言基础', '基础', '能说明 Java 基础语法、面向对象和 JVM 基础概念，并完成课程题目练习'),
  ('skill-java-002', 'job-java-backend', 'tech-003', '并发编程基础', '进阶', '能解释线程状态、锁和并发容器，并结合项目说明使用场景'),
  ('skill-java-003', 'job-java-backend', 'tech-004', '数据库设计与 SQL', '基础', '能完成基础表结构设计、SQL 查询和事务场景说明'),
  ('skill-java-004', 'job-java-backend', 'tech-005', '缓存与性能优化', '进阶', '能说明 Redis 常见数据结构、缓存使用场景和一致性风险'),
  ('skill-java-005', 'job-java-backend', 'tech-006', 'HTTP 与接口设计', '基础', '能设计 REST 接口，说明鉴权、状态码和联调验证结果')
ON DUPLICATE KEY UPDATE ability_level=VALUES(ability_level), evidence_requirement=VALUES(evidence_requirement);

INSERT INTO question (question_id, source_id, source_path, source_url, question_type, stem, difficulty, answer, answer_analysis, audit_status) VALUES
  ('jg-q-001', 'source-javaguide', 'docs/java/basis/java-basic-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/basis/java-basic-questions-01.md', '简答题', 'Java 语言有哪些特点？', '中等', '1. 简单易学（语法简单，上手容易）； 2. 面向对象（封装，继承，多态）； 3. 平台无关性（Java 虚拟机实现平台无关性）； 4. 支持多线程（C++ 语言没有内置的多线程机制，因此必须调用操作系统的多线程功能来进行多线程程序设计，而 Java 语言却提供了多线程支持）； 5. 可靠性（具备异常处理和自动内存管理机制）； 6. 安全性（Java 语言本身的设计就提供了多重安全防护机制如访问权限修饰符、限制程序直接访问操作系统资源）； 7. 高效性（通过 Just In Time 编译器等技术的优化，Java 语言的运行效率还是非常不错的）； 8. 支持网络编程并且很方便； 9. 编译与解释并存； 10. …… > **🐛 修正（参见：[issue#544](https://github.com/Snailclimb/JavaGuide/issues/544)）**：C++11 开始（2011 年的时候），C++ 就引入了多线程库，在 Windows、Linux、macOS 都可以使用 `std::thread` 和 `std::async` 来创建线程。参考链接： 🌈 拓展一', '1. 简单易学（语法简单，上手容易）； 2. 面向对象（封装，继承，多态）； 3. 平台无关性（Java 虚拟机实现平台无关性）； 4. 支持多线程（C++ 语言没有内置的多线程机制，因此必须调用操作系统的多线程功能来进行多线程程序设计，而 Java 语言却提供了多线程支持）； 5. 可靠性（具备异常处理和自动内存管理机制）； 6. 安全性（Java 语言本身的设计就提供了多重安全防护机制如访问权限修饰符、限制程序直接访问操作系统资源）； 7. 高效性（通过 Just In Time 编译器等技术的优化，Java 语言的运行效率还是非常不错的）； 8. 支持网络编程并且很方便； 9. 编译与解释并存； 10. …… > **🐛 修正（参见：[issue#544](https://github.com/Snailclimb/JavaGuide/issues/544)）**：C++11 开始（2011 年的时候），C++ 就引入了多线程库，在 Windows、Linux、macOS 都可以使用 `std::thread` 和 `std::async` 来创建线程。参考链接： 🌈 拓展一', '已发布'),
  ('jg-q-002', 'source-javaguide', 'docs/java/basis/java-basic-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/basis/java-basic-questions-01.md', '简答题', '什么是字节码？采用字节码的好处是什么？', '中等', '在 Java 中，JVM 可以理解的代码就叫做字节码（即扩展名为 `.class` 的文件），它不面向任何特定的处理器，只面向虚拟机。Java 语言通过字节码的方式，在一定程度上解决了传统解释型语言执行效率低的问题，同时又保留了解释型语言可移植的特点。所以， Java 程序运行时相对来说还是高效的（不过，和 C、 C++，Rust，Go 等语言还是有一定差距的），而且，由于字节码并不针对一种特定的机器，因此，Java 程序无须重新编译便可在多种不同操作系统的计算机上运行。 **Java 程序从源代码到运行的过程如下图所示**： 我们需要格外注意的是 `.class->机器码` 这一步。在这一步 JVM 类加载器首先加载字节码文件，然后通过解释器逐行解释执行，这种方式的执行速度会相对比较慢。而且，有些方法和代码块是经常需要被调用的（也就是所谓的热点代码），所以后面引进了 **JIT（Just in Time Compilation）** 编译器，而 JIT 属于运行时编译。当 JIT 编译器完成第一次编译后，其会将字节码对应的机器码保存下来，下次可以直接使用。而我们知道，机器码的运行效率', '在 Java 中，JVM 可以理解的代码就叫做字节码（即扩展名为 `.class` 的文件），它不面向任何特定的处理器，只面向虚拟机。Java 语言通过字节码的方式，在一定程度上解决了传统解释型语言执行效率低的问题，同时又保留了解释型语言可移植的特点。所以， Java 程序运行时相对来说还是高效的（不过，和 C、 C++，Rust，Go 等语言还是有一定差距的），而且，由于字节码并不针对一种特定的机器，因此，Java 程序无须重新编译便可在多种不同操作系统的计算机上运行。 **Java 程序从源代码到运行的过程如下图所示**： 我们需要格外注意的是 `.class->机器码` 这一步。在这一步 JVM 类加载器首先加载字节码文件，然后通过解释器逐行解释执行，这种方式的执行速度会相对比较慢。而且，有些方法和代码块是经常需要被调用的（也就是所谓的热点代码），所以后面引进了 **JIT（Just in Time Compilation）** 编译器，而 JIT 属于运行时编译。当 JIT 编译器完成第一次编译后，其会将字节码对应的机器码保存下来，下次可以直接使用。而我们知道，机器码的运行效率', '已发布'),
  ('jg-q-003', 'source-javaguide', 'docs/java/basis/java-basic-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/basis/java-basic-questions-01.md', '简答题', 'Java 和 C++ 的区别？', '中等', '我知道很多人没学过 C++，但是面试官就是没事喜欢拿咱们 Java 和 C++ 比呀！没办法！！！就算没学过 C++，也要记下来。 虽然，Java 和 C++ 都是面向对象的语言，都支持封装、继承和多态，但是，它们还是有挺多不相同的地方： - Java 不提供指针来直接访问内存，程序内存更加安全 - Java 的类是单继承的，C++ 支持多重继承；虽然 Java 的类不可以多继承，但是接口可以多继承。 - Java 有自动内存管理垃圾回收机制(GC)，不需要程序员手动释放无用内存。 - C ++同时支持方法重载和操作符重载，但是 Java 只支持方法重载（操作符重载增加了复杂性，这与 Java 最初的设计思想不符）。 - ……', '我知道很多人没学过 C++，但是面试官就是没事喜欢拿咱们 Java 和 C++ 比呀！没办法！！！就算没学过 C++，也要记下来。 虽然，Java 和 C++ 都是面向对象的语言，都支持封装、继承和多态，但是，它们还是有挺多不相同的地方： - Java 不提供指针来直接访问内存，程序内存更加安全 - Java 的类是单继承的，C++ 支持多重继承；虽然 Java 的类不可以多继承，但是接口可以多继承。 - Java 有自动内存管理垃圾回收机制(GC)，不需要程序员手动释放无用内存。 - C ++同时支持方法重载和操作符重载，但是 Java 只支持方法重载（操作符重载增加了复杂性，这与 Java 最初的设计思想不符）。 - ……', '已发布'),
  ('jg-q-004', 'source-javaguide', 'docs/java/basis/java-basic-questions-02.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/basis/java-basic-questions-02.md', '简答题', '面向对象和面向过程的区别', '中等', '面向过程编程（Procedural-Oriented Programming，POP）和面向对象编程（Object-Oriented Programming，OOP）是两种常见的编程范式，两者的主要区别在于解决问题的方式不同： - **面向过程编程（POP）**：面向过程把解决问题的过程拆成一个个方法，通过一个个方法的执行解决问题。 - **面向对象编程（OOP）**：面向对象会先抽象出对象，然后用对象执行方法的方式解决问题。 相比较于 POP，OOP 开发的程序一般具有下面这些优点： - **易维护**：由于良好的结构和封装性，OOP 程序通常更容易维护。 - **易复用**：通过继承和多态，OOP 设计使得代码更具复用性，方便扩展功能。 - **易扩展**：模块化设计使得系统扩展变得更加容易和灵活。 POP 的编程方式通常更为简单和直接，适合处理一些较简单的任务。 POP 和 OOP 的性能差异主要取决于它们的运行机制，而不仅仅是编程范式本身。因此，简单地比较两者的性能是一个常见的误区（相关 issue : [面向过程：面向过程性能比面向对象高？？](https://github.', '面向过程编程（Procedural-Oriented Programming，POP）和面向对象编程（Object-Oriented Programming，OOP）是两种常见的编程范式，两者的主要区别在于解决问题的方式不同： - **面向过程编程（POP）**：面向过程把解决问题的过程拆成一个个方法，通过一个个方法的执行解决问题。 - **面向对象编程（OOP）**：面向对象会先抽象出对象，然后用对象执行方法的方式解决问题。 相比较于 POP，OOP 开发的程序一般具有下面这些优点： - **易维护**：由于良好的结构和封装性，OOP 程序通常更容易维护。 - **易复用**：通过继承和多态，OOP 设计使得代码更具复用性，方便扩展功能。 - **易扩展**：模块化设计使得系统扩展变得更加容易和灵活。 POP 的编程方式通常更为简单和直接，适合处理一些较简单的任务。 POP 和 OOP 的性能差异主要取决于它们的运行机制，而不仅仅是编程范式本身。因此，简单地比较两者的性能是一个常见的误区（相关 issue : [面向过程：面向过程性能比面向对象高？？](https://github.', '已发布'),
  ('jg-q-005', 'source-javaguide', 'docs/java/basis/java-basic-questions-02.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/basis/java-basic-questions-02.md', '简答题', '面向对象三大特征', '中等', '#### 封装 封装是指把一个对象的状态信息（也就是属性）隐藏在对象内部，不允许外部对象直接访问对象的内部信息。但是可以提供一些可以被外界访问的方法来操作属性。就好像我们看不到挂在墙上的空调的内部的零件信息（也就是属性），但是可以通过遥控器（方法）来控制空调。如果属性不想被外界访问，我们大可不必提供方法给外界访问。但是如果一个类没有提供给外界访问的方法，那么这个类也没有什么意义了。就好像如果没有空调遥控器，那么我们就无法操控空凋制冷，空调本身就没有意义了（当然现在还有很多其他方法，这里只是为了举例子）。 ```java public class Student { private int id;//id属性私有化 private String name;//name属性私有化 //获取id的方法 public int getId() { return id; } //设置id的方法 public void setId(int id) { this.id = id; } //获取name的方法 public String getName() { return name; } //设置na', '#### 封装 封装是指把一个对象的状态信息（也就是属性）隐藏在对象内部，不允许外部对象直接访问对象的内部信息。但是可以提供一些可以被外界访问的方法来操作属性。就好像我们看不到挂在墙上的空调的内部的零件信息（也就是属性），但是可以通过遥控器（方法）来控制空调。如果属性不想被外界访问，我们大可不必提供方法给外界访问。但是如果一个类没有提供给外界访问的方法，那么这个类也没有什么意义了。就好像如果没有空调遥控器，那么我们就无法操控空凋制冷，空调本身就没有意义了（当然现在还有很多其他方法，这里只是为了举例子）。 ```java public class Student { private int id;//id属性私有化 private String name;//name属性私有化 //获取id的方法 public int getId() { return id; } //设置id的方法 public void setId(int id) { this.id = id; } //获取name的方法 public String getName() { return name; } //设置na', '已发布'),
  ('jg-q-006', 'source-javaguide', 'docs/java/basis/java-basic-questions-02.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/basis/java-basic-questions-02.md', '简答题', '接口和抽象类有什么共同点和区别？', '中等', '#### 接口和抽象类的共同点 - **实例化**：接口和抽象类都不能直接实例化，只能被实现（接口）或继承（抽象类）后才能创建具体的对象。 - **抽象方法**：接口和抽象类都可以包含抽象方法。抽象方法没有方法体，必须在子类或实现类中实现。 #### 接口和抽象类的区别 - **设计目的**：接口主要用于对类的行为进行约束，你实现了某个接口就具有了对应的行为。抽象类主要用于代码复用，强调的是所属关系。 - **继承和实现**：一个类只能继承一个类（包括抽象类），因为 Java 不支持多继承。但一个类可以实现多个接口，一个接口也可以继承多个其他接口。 - **成员变量**：接口中的成员变量只能是 `public static final` 类型的，不能被修改且必须有初始值。抽象类的成员变量可以有任何修饰符（`private`, `protected`, `public`），可以在子类中被重新定义或赋值。 - **方法**： - Java 8 之前，接口中的方法默认是 `public abstract`，也就是只能有方法声明。自 Java 8 起，可以在接口中定义 `default`（默认', '#### 接口和抽象类的共同点 - **实例化**：接口和抽象类都不能直接实例化，只能被实现（接口）或继承（抽象类）后才能创建具体的对象。 - **抽象方法**：接口和抽象类都可以包含抽象方法。抽象方法没有方法体，必须在子类或实现类中实现。 #### 接口和抽象类的区别 - **设计目的**：接口主要用于对类的行为进行约束，你实现了某个接口就具有了对应的行为。抽象类主要用于代码复用，强调的是所属关系。 - **继承和实现**：一个类只能继承一个类（包括抽象类），因为 Java 不支持多继承。但一个类可以实现多个接口，一个接口也可以继承多个其他接口。 - **成员变量**：接口中的成员变量只能是 `public static final` 类型的，不能被修改且必须有初始值。抽象类的成员变量可以有任何修饰符（`private`, `protected`, `public`），可以在子类中被重新定义或赋值。 - **方法**： - Java 8 之前，接口中的方法默认是 `public abstract`，也就是只能有方法声明。自 Java 8 起，可以在接口中定义 `default`（默认', '已发布'),
  ('jg-q-007', 'source-javaguide', 'docs/java/basis/java-basic-questions-03.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/basis/java-basic-questions-03.md', '简答题', 'Exception 和 Error 有什么区别？', '中等', '在 Java 中，所有的异常都有一个共同的祖先 `java.lang` 包中的 `Throwable` 类。`Throwable` 类有两个重要的子类: - **`Exception`** :程序本身可以处理的异常，可以通过 `catch` 来进行捕获。`Exception` 又可以分为 Checked Exception（受检查异常，必须处理） 和 Unchecked Exception（不受检查异常，可以不处理）。 - **`Error`**：`Error` 属于程序无法处理的错误，~~我们没办法通过 `catch` 来进行捕获~~不建议通过 `catch` 捕获。例如 Java 虚拟机运行错误（`Virtual MachineError`）、虚拟机内存不够错误(`OutOfMemoryError`)、类定义错误（`NoClassDefFoundError`）等。这些异常发生时，Java 虚拟机（JVM）一般会选择线程终止。', '在 Java 中，所有的异常都有一个共同的祖先 `java.lang` 包中的 `Throwable` 类。`Throwable` 类有两个重要的子类: - **`Exception`** :程序本身可以处理的异常，可以通过 `catch` 来进行捕获。`Exception` 又可以分为 Checked Exception（受检查异常，必须处理） 和 Unchecked Exception（不受检查异常，可以不处理）。 - **`Error`**：`Error` 属于程序无法处理的错误，~~我们没办法通过 `catch` 来进行捕获~~不建议通过 `catch` 捕获。例如 Java 虚拟机运行错误（`Virtual MachineError`）、虚拟机内存不够错误(`OutOfMemoryError`)、类定义错误（`NoClassDefFoundError`）等。这些异常发生时，Java 虚拟机（JVM）一般会选择线程终止。', '已发布'),
  ('jg-q-008', 'source-javaguide', 'docs/java/basis/java-basic-questions-03.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/basis/java-basic-questions-03.md', '简答题', '什么是泛型？有什么作用？', '中等', '**Java 泛型（Generics）** 是 JDK 5 中引入的一个新特性。使用泛型参数，可以增强代码的可读性以及稳定性。 编译器可以对泛型参数进行检测，并且通过泛型参数可以指定传入的对象类型。比如 `ArrayList persons = new ArrayList ()` 这行代码就指明了该 `ArrayList` 对象只能传入 `Person` 对象，如果传入其他类型的对象就会报错。 ```java ArrayList extends AbstractList ``` 并且，原生 `List` 返回类型是 `Object`，需要手动转换类型才能使用，使用泛型后编译器自动转换。', '**Java 泛型（Generics）** 是 JDK 5 中引入的一个新特性。使用泛型参数，可以增强代码的可读性以及稳定性。 编译器可以对泛型参数进行检测，并且通过泛型参数可以指定传入的对象类型。比如 `ArrayList persons = new ArrayList ()` 这行代码就指明了该 `ArrayList` 对象只能传入 `Person` 对象，如果传入其他类型的对象就会报错。 ```java ArrayList extends AbstractList ``` 并且，原生 `List` 返回类型是 `Object`，需要手动转换类型才能使用，使用泛型后编译器自动转换。', '已发布'),
  ('jg-q-009', 'source-javaguide', 'docs/java/basis/java-basic-questions-03.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/basis/java-basic-questions-03.md', '简答题', '什么是反射？', '中等', '简单来说，Java 反射 (Reflection) 是一种**在程序运行时，动态地获取类的信息并操作类或对象（方法、属性）的能力**。 通常情况下，我们写的代码在编译时类型就已经确定了，要调用哪个方法、访问哪个字段都是明确的。但反射允许我们在**运行时**才去探知一个类有哪些方法、哪些属性、它的构造函数是怎样的，甚至可以动态地创建对象、调用方法或修改属性，哪怕这些方法或属性是私有的。 正是这种在运行时“反观自身”并进行操作的能力，使得反射成为许多**通用框架和库的基石**。它让代码更加灵活，能够处理在编译时未知的类型。', '简单来说，Java 反射 (Reflection) 是一种**在程序运行时，动态地获取类的信息并操作类或对象（方法、属性）的能力**。 通常情况下，我们写的代码在编译时类型就已经确定了，要调用哪个方法、访问哪个字段都是明确的。但反射允许我们在**运行时**才去探知一个类有哪些方法、哪些属性、它的构造函数是怎样的，甚至可以动态地创建对象、调用方法或修改属性，哪怕这些方法或属性是私有的。 正是这种在运行时“反观自身”并进行操作的能力，使得反射成为许多**通用框架和库的基石**。它让代码更加灵活，能够处理在编译时未知的类型。', '已发布'),
  ('jg-q-010', 'source-javaguide', 'docs/java/collection/java-collection-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/collection/java-collection-questions-01.md', '简答题', '说说 List, Set, Queue, Map 四者的区别？', '中等', '- `List`（对付顺序的好帮手）: 存储的元素是有序的、可重复的。 - `Set`（注重独一无二的性质）: 存储的元素不可重复的。 - `Queue`（实现排队功能的叫号机）: 按特定的排队规则来确定先后顺序，存储的元素是有序的、可重复的。 - `Map`（用 key 来搜索的专家）: 使用键值对（key-value）存储，类似于数学上的函数 y=f(x)，"x" 代表 key，"y" 代表 value，key 无序、不可重复，value 无序、可重复，每个键最多映射到一个值。注意，这里的“无序”指的是 `HashMap` 这类实现——键值对之间没有显式的关联顺序。`LinkedHashMap` 和 `TreeMap` 等实现则是有序的，它们通过额外的数据结构（双向链表或红黑树）来维护键值对的顺序。', '- `List`（对付顺序的好帮手）: 存储的元素是有序的、可重复的。 - `Set`（注重独一无二的性质）: 存储的元素不可重复的。 - `Queue`（实现排队功能的叫号机）: 按特定的排队规则来确定先后顺序，存储的元素是有序的、可重复的。 - `Map`（用 key 来搜索的专家）: 使用键值对（key-value）存储，类似于数学上的函数 y=f(x)，"x" 代表 key，"y" 代表 value，key 无序、不可重复，value 无序、可重复，每个键最多映射到一个值。注意，这里的“无序”指的是 `HashMap` 这类实现——键值对之间没有显式的关联顺序。`LinkedHashMap` 和 `TreeMap` 等实现则是有序的，它们通过额外的数据结构（双向链表或红黑树）来维护键值对的顺序。', '已发布'),
  ('jg-q-011', 'source-javaguide', 'docs/java/collection/java-collection-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/collection/java-collection-questions-01.md', '简答题', 'ArrayList 和 Array（数组）的区别？', '中等', '`ArrayList` 内部基于动态数组实现，比 `Array`（静态数组） 使用起来更加灵活： - `ArrayList` 会根据实际存储的元素动态地扩容或缩容，而 `Array` 被创建之后就不能改变它的长度了。 - `ArrayList` 允许你使用泛型来确保类型安全，`Array` 则不可以。 - `ArrayList` 中只能存储对象。对于基本类型数据，需要使用其对应的包装类（如 Integer、Double 等）。`Array` 可以直接存储基本类型数据，也可以存储对象。 - `ArrayList` 支持插入、删除、遍历等常见操作，并且提供了丰富的 API 操作方法，比如 `add()`、`remove()` 等。`Array` 只是一个固定长度的数组，只能按照下标访问其中的元素，不具备动态添加、删除元素的能力。 - `ArrayList` 创建时不需要指定大小，而 `Array` 创建时必须指定大小。 下面是二者使用的简单对比： `Array`： ```java // 初始化一个 String 类型的数组 String[] stringArr = new String[]', '`ArrayList` 内部基于动态数组实现，比 `Array`（静态数组） 使用起来更加灵活： - `ArrayList` 会根据实际存储的元素动态地扩容或缩容，而 `Array` 被创建之后就不能改变它的长度了。 - `ArrayList` 允许你使用泛型来确保类型安全，`Array` 则不可以。 - `ArrayList` 中只能存储对象。对于基本类型数据，需要使用其对应的包装类（如 Integer、Double 等）。`Array` 可以直接存储基本类型数据，也可以存储对象。 - `ArrayList` 支持插入、删除、遍历等常见操作，并且提供了丰富的 API 操作方法，比如 `add()`、`remove()` 等。`Array` 只是一个固定长度的数组，只能按照下标访问其中的元素，不具备动态添加、删除元素的能力。 - `ArrayList` 创建时不需要指定大小，而 `Array` 创建时必须指定大小。 下面是二者使用的简单对比： `Array`： ```java // 初始化一个 String 类型的数组 String[] stringArr = new String[]', '已发布'),
  ('jg-q-012', 'source-javaguide', 'docs/java/concurrent/java-concurrent-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/concurrent/java-concurrent-questions-01.md', '简答题', '什么是线程和进程？', '中等', '#### 何为进程？ 进程是程序的一次执行过程，是系统运行程序的基本单位，因此进程是动态的。系统运行一个程序即是一个进程从创建，运行到消亡的过程。 在 Java 中，当我们启动 main 函数时其实就是启动了一个 JVM 的进程，而 main 函数所在的线程就是这个进程中的一个线程，也称主线程。 如下图所示，在 Windows 中通过查看任务管理器的方式，我们就可以清楚看到 Windows 当前运行的进程（`.exe` 文件的运行）。 #### 何为线程？ 线程与进程相似，但线程是一个比进程更小的执行单位。一个进程在其执行的过程中可以产生多个线程。与进程不同的是同类的多个线程共享进程的**堆**和**方法区**资源，但每个线程有自己的**程序计数器**、**虚拟机栈**和**本地方法栈**，所以系统在产生一个线程，或是在各个线程之间做切换工作时，负担要比进程小得多，也正因为如此，线程也被称为轻量级进程。 Java 程序天生就是多线程程序，我们可以通过 JMX 来看看一个普通的 Java 程序有哪些线程，代码如下。 ```java public class MultiThread { p', '#### 何为进程？ 进程是程序的一次执行过程，是系统运行程序的基本单位，因此进程是动态的。系统运行一个程序即是一个进程从创建，运行到消亡的过程。 在 Java 中，当我们启动 main 函数时其实就是启动了一个 JVM 的进程，而 main 函数所在的线程就是这个进程中的一个线程，也称主线程。 如下图所示，在 Windows 中通过查看任务管理器的方式，我们就可以清楚看到 Windows 当前运行的进程（`.exe` 文件的运行）。 #### 何为线程？ 线程与进程相似，但线程是一个比进程更小的执行单位。一个进程在其执行的过程中可以产生多个线程。与进程不同的是同类的多个线程共享进程的**堆**和**方法区**资源，但每个线程有自己的**程序计数器**、**虚拟机栈**和**本地方法栈**，所以系统在产生一个线程，或是在各个线程之间做切换工作时，负担要比进程小得多，也正因为如此，线程也被称为轻量级进程。 Java 程序天生就是多线程程序，我们可以通过 JMX 来看看一个普通的 Java 程序有哪些线程，代码如下。 ```java public class MultiThread { p', '已发布'),
  ('jg-q-013', 'source-javaguide', 'docs/java/concurrent/java-concurrent-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/java/concurrent/java-concurrent-questions-01.md', '简答题', '说说线程的生命周期和状态？', '中等', 'Java 线程在运行的生命周期中的指定时刻只可能处于下面 6 种不同状态的其中一个状态： - NEW: 初始状态，线程被创建出来但没有被调用 `start()`。 - RUNNABLE: 运行状态，线程被调用了 `start()` 等待运行的状态。 - BLOCKED：阻塞状态，需要等待锁释放。 - WAITING：等待状态，表示该线程需要等待其他线程做出一些特定动作（通知或中断）。 - TIME_WAITING：超时等待状态，可以在指定的时间后自行返回而不是像 WAITING 那样一直等待。 - TERMINATED：终止状态，表示该线程已经运行完毕。 线程在生命周期中并不是固定处于某一个状态而是随着代码的执行在不同状态之间切换。 Java 线程状态变迁图(图源：[挑错 |《Java 并发编程的艺术》中关于线程状态的三处错误](https://mp.weixin.qq.com/s/0UTyrJpRKaKhkhHcQtXAiA))： 由上图可以看出：线程创建之后它将处于 **NEW（新建）** 状态，调用 `start()` 方法后开始运行，线程这时候处于 **READY（可运行）**', 'Java 线程在运行的生命周期中的指定时刻只可能处于下面 6 种不同状态的其中一个状态： - NEW: 初始状态，线程被创建出来但没有被调用 `start()`。 - RUNNABLE: 运行状态，线程被调用了 `start()` 等待运行的状态。 - BLOCKED：阻塞状态，需要等待锁释放。 - WAITING：等待状态，表示该线程需要等待其他线程做出一些特定动作（通知或中断）。 - TIME_WAITING：超时等待状态，可以在指定的时间后自行返回而不是像 WAITING 那样一直等待。 - TERMINATED：终止状态，表示该线程已经运行完毕。 线程在生命周期中并不是固定处于某一个状态而是随着代码的执行在不同状态之间切换。 Java 线程状态变迁图(图源：[挑错 |《Java 并发编程的艺术》中关于线程状态的三处错误](https://mp.weixin.qq.com/s/0UTyrJpRKaKhkhHcQtXAiA))： 由上图可以看出：线程创建之后它将处于 **NEW（新建）** 状态，调用 `start()` 方法后开始运行，线程这时候处于 **READY（可运行）**', '已发布'),
  ('jg-q-014', 'source-javaguide', 'docs/database/mysql/mysql-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/database/mysql/mysql-questions-01.md', '简答题', '什么是 MySQL？', '中等', '**MySQL 是一种关系型数据库，主要用于持久化存储我们的系统中的一些数据比如用户信息。** 由于 MySQL 是开源免费并且比较成熟的数据库，因此，MySQL 被大量使用在各种系统中。任何人都可以在 GPL(General Public License) 的许可下下载并根据个性化的需要对其进行修改。MySQL 的默认端口号是**3306**。', '**MySQL 是一种关系型数据库，主要用于持久化存储我们的系统中的一些数据比如用户信息。** 由于 MySQL 是开源免费并且比较成熟的数据库，因此，MySQL 被大量使用在各种系统中。任何人都可以在 GPL(General Public License) 的许可下下载并根据个性化的需要对其进行修改。MySQL 的默认端口号是**3306**。', '已发布'),
  ('jg-q-015', 'source-javaguide', 'docs/database/mysql/mysql-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/database/mysql/mysql-questions-01.md', '简答题', 'CHAR 和 VARCHAR 的区别是什么？', '中等', 'CHAR 和 VARCHAR 是最常用到的字符串类型，两者的主要区别在于：**CHAR 是定长字符串，VARCHAR 是变长字符串。** CHAR 在存储时会在右边填充空格以达到指定的长度，检索时会去掉空格；VARCHAR 在存储时需要使用 1 或 2 个额外字节记录字符串的长度，检索时不需要处理。 CHAR 更适合存储长度较短或者长度都差不多的字符串，例如 Bcrypt 算法、MD5 算法加密后的密码、身份证号码。VARCHAR 类型适合存储长度不确定或者差异较大的字符串，例如用户昵称、文章标题等。 CHAR(M) 和 VARCHAR(M) 的 M 都代表能够保存的字符数的最大值，无论是字母、数字还是中文，每个都只占用一个字符。', 'CHAR 和 VARCHAR 是最常用到的字符串类型，两者的主要区别在于：**CHAR 是定长字符串，VARCHAR 是变长字符串。** CHAR 在存储时会在右边填充空格以达到指定的长度，检索时会去掉空格；VARCHAR 在存储时需要使用 1 或 2 个额外字节记录字符串的长度，检索时不需要处理。 CHAR 更适合存储长度较短或者长度都差不多的字符串，例如 Bcrypt 算法、MD5 算法加密后的密码、身份证号码。VARCHAR 类型适合存储长度不确定或者差异较大的字符串，例如用户昵称、文章标题等。 CHAR(M) 和 VARCHAR(M) 的 M 都代表能够保存的字符数的最大值，无论是字母、数字还是中文，每个都只占用一个字符。', '已发布'),
  ('jg-q-016', 'source-javaguide', 'docs/database/mysql/mysql-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/database/mysql/mysql-questions-01.md', '简答题', 'DATETIME 和 TIMESTAMP 的区别是什么？如何选择？', '中等', 'DATETIME 类型没有时区信息，TIMESTAMP 和时区有关。 TIMESTAMP 只需要使用 4 个字节的存储空间，但是 DATETIME 需要耗费 8 个字节的存储空间。但是，这样同样造成了一个问题，Timestamp 表示的时间范围更小。 - DATETIME：''1000-01-01 00:00:00.000000'' 到 ''9999-12-31 23:59:59.999999'' - Timestamp：''1970-01-01 00:00:01.000000'' UTC 到 ''2038-01-19 03:14:07.999999'' UTC `TIMESTAMP` 的核心优势在于其内建的时区处理能力。数据库负责 UTC 存储和基于会话时区的自动转换，简化了需要处理多时区应用的开发。如果应用需要处理多时区，或者希望数据库能自动管理时区转换，`TIMESTAMP` 是自然的选择（注意其时间范围限制，也就是 2038 年问题）。 如果应用场景不涉及时区转换，或者希望应用程序完全控制时区逻辑，并且需要表示 2038 年之后的时间，`DATETIME` 是更稳妥的选择。 关于两者的详细对比', 'DATETIME 类型没有时区信息，TIMESTAMP 和时区有关。 TIMESTAMP 只需要使用 4 个字节的存储空间，但是 DATETIME 需要耗费 8 个字节的存储空间。但是，这样同样造成了一个问题，Timestamp 表示的时间范围更小。 - DATETIME：''1000-01-01 00:00:00.000000'' 到 ''9999-12-31 23:59:59.999999'' - Timestamp：''1970-01-01 00:00:01.000000'' UTC 到 ''2038-01-19 03:14:07.999999'' UTC `TIMESTAMP` 的核心优势在于其内建的时区处理能力。数据库负责 UTC 存储和基于会话时区的自动转换，简化了需要处理多时区应用的开发。如果应用需要处理多时区，或者希望数据库能自动管理时区转换，`TIMESTAMP` 是自然的选择（注意其时间范围限制，也就是 2038 年问题）。 如果应用场景不涉及时区转换，或者希望应用程序完全控制时区逻辑，并且需要表示 2038 年之后的时间，`DATETIME` 是更稳妥的选择。 关于两者的详细对比', '已发布'),
  ('jg-q-017', 'source-javaguide', 'docs/database/redis/redis-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/database/redis/redis-questions-01.md', '简答题', '什么是 Redis？', '中等', '[Redis](https://redis.io/) （**RE**mote **DI**ctionary **S**erver）是一个基于 C 语言开发的开源 NoSQL 数据库（BSD 许可）。与传统数据库不同的是，Redis 的数据是保存在内存中的（内存数据库，支持持久化），因此读写速度非常快，被广泛应用于分布式缓存方向。并且，Redis 存储的是 KV 键值对数据。 为了满足不同的业务场景，Redis 内置了多种数据类型实现（比如 String、Hash、Sorted Set、Bitmap、HyperLogLog、GEO）。并且，Redis 还支持事务、持久化、Lua 脚本、发布订阅模型、多种开箱即用的集群方案（Redis Sentinel、Redis Cluster）。 Redis 没有外部依赖，Linux 和 OS X 是 Redis 开发和测试最多的两个操作系统，官方推荐生产环境使用 Linux 部署 Redis。 个人学习的话，你可以自己本机安装 Redis 或者通过 Redis 官网提供的[在线 Redis 环境](https://try.redis.io/)（少部分', '[Redis](https://redis.io/) （**RE**mote **DI**ctionary **S**erver）是一个基于 C 语言开发的开源 NoSQL 数据库（BSD 许可）。与传统数据库不同的是，Redis 的数据是保存在内存中的（内存数据库，支持持久化），因此读写速度非常快，被广泛应用于分布式缓存方向。并且，Redis 存储的是 KV 键值对数据。 为了满足不同的业务场景，Redis 内置了多种数据类型实现（比如 String、Hash、Sorted Set、Bitmap、HyperLogLog、GEO）。并且，Redis 还支持事务、持久化、Lua 脚本、发布订阅模型、多种开箱即用的集群方案（Redis Sentinel、Redis Cluster）。 Redis 没有外部依赖，Linux 和 OS X 是 Redis 开发和测试最多的两个操作系统，官方推荐生产环境使用 Linux 部署 Redis。 个人学习的话，你可以自己本机安装 Redis 或者通过 Redis 官网提供的[在线 Redis 环境](https://try.redis.io/)（少部分', '已发布'),
  ('jg-q-018', 'source-javaguide', 'docs/database/redis/redis-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/database/redis/redis-questions-01.md', '简答题', 'Redis 为什么这么快？', '中等', 'Redis 内部做了非常多的性能优化，比较重要的有下面 4 点： 1. **纯内存操作 (Memory-Based Storage)** ：这是最主要的原因。Redis 数据读写操作都发生在内存中，访问速度是纳秒级别，而传统数据库频繁读写磁盘的速度是毫秒级别，两者相差数个数量级。 2. **高效的 I/O 模型 (I/O Multiplexing & Single-Threaded Event Loop)** ：Redis 使用单线程事件循环配合 I/O 多路复用技术，让单个线程可以同时处理多个网络连接上的 I/O 事件（如读写），避免了多线程模型中的上下文切换和锁竞争问题。虽然是单线程，但结合内存操作的高效性和 I/O 多路复用，使得 Redis 能轻松处理大量并发请求（Redis 线程模型会在后文中详细介绍到）。 3. **优化的内部数据结构 (Optimized Data Structures)** ：Redis 提供多种数据类型（如 String, List, Hash, Set, Sorted Set 等），其内部实现采用高度优化的编码方式（如 ziplist, quickl', 'Redis 内部做了非常多的性能优化，比较重要的有下面 4 点： 1. **纯内存操作 (Memory-Based Storage)** ：这是最主要的原因。Redis 数据读写操作都发生在内存中，访问速度是纳秒级别，而传统数据库频繁读写磁盘的速度是毫秒级别，两者相差数个数量级。 2. **高效的 I/O 模型 (I/O Multiplexing & Single-Threaded Event Loop)** ：Redis 使用单线程事件循环配合 I/O 多路复用技术，让单个线程可以同时处理多个网络连接上的 I/O 事件（如读写），避免了多线程模型中的上下文切换和锁竞争问题。虽然是单线程，但结合内存操作的高效性和 I/O 多路复用，使得 Redis 能轻松处理大量并发请求（Redis 线程模型会在后文中详细介绍到）。 3. **优化的内部数据结构 (Optimized Data Structures)** ：Redis 提供多种数据类型（如 String, List, Hash, Set, Sorted Set 等），其内部实现采用高度优化的编码方式（如 ziplist, quickl', '已发布'),
  ('jg-q-019', 'source-javaguide', 'docs/database/redis/redis-questions-01.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/database/redis/redis-questions-01.md', '简答题', '为什么要用 Redis？', '中等', '**1、访问速度更快** 传统数据库数据保存在磁盘，而 Redis 基于内存，内存的访问速度比磁盘快很多。引入 Redis 之后，我们可以把一些高频访问的数据放到 Redis 中，这样下次就可以直接从内存中读取，速度可以提升几十倍甚至上百倍。 **2、高并发** 一般像 MySQL 这类的数据库的 QPS 大概都在 4k 左右（4 核 8g），但是使用 Redis 缓存之后很容易达到 5w+，甚至能达到 10w+（就单机 Redis 的情况，Redis 集群的话会更高）。 > QPS（Query Per Second）：服务器每秒可以执行的查询次数； 由此可见，直接操作缓存能够承受的数据库请求数量是远远大于直接访问数据库的，所以我们可以考虑把数据库中的部分数据转移到缓存中去，这样用户的一部分请求会直接到缓存这里而不用经过数据库。进而，我们也就提高了系统整体的并发。 **3、功能全面** Redis 除了可以用作缓存之外，还可以用于分布式锁、限流、消息队列、延时队列等场景，功能强大！', '**1、访问速度更快** 传统数据库数据保存在磁盘，而 Redis 基于内存，内存的访问速度比磁盘快很多。引入 Redis 之后，我们可以把一些高频访问的数据放到 Redis 中，这样下次就可以直接从内存中读取，速度可以提升几十倍甚至上百倍。 **2、高并发** 一般像 MySQL 这类的数据库的 QPS 大概都在 4k 左右（4 核 8g），但是使用 Redis 缓存之后很容易达到 5w+，甚至能达到 10w+（就单机 Redis 的情况，Redis 集群的话会更高）。 > QPS（Query Per Second）：服务器每秒可以执行的查询次数； 由此可见，直接操作缓存能够承受的数据库请求数量是远远大于直接访问数据库的，所以我们可以考虑把数据库中的部分数据转移到缓存中去，这样用户的一部分请求会直接到缓存这里而不用经过数据库。进而，我们也就提高了系统整体的并发。 **3、功能全面** Redis 除了可以用作缓存之外，还可以用于分布式锁、限流、消息队列、延时队列等场景，功能强大！', '已发布'),
  ('jg-q-020', 'source-javaguide', 'docs/cs-basics/network/other-network-questions.md', 'https://github.com/Snailclimb/JavaGuide/blob/main/docs/cs-basics/network/other-network-questions.md', '简答题', '从输入 URL 到页面展示到底发生了什么？（非常重要）', '中等', '> 类似的问题：打开一个网页，整个过程会使用哪些协议？ 先来看一张图（来源于《图解 HTTP》）： 上图有一个错误需要注意：是 OSPF 不是 OPSF。OSPF（Open Shortest Path First，ospf）开放最短路径优先协议，是由 Internet 工程任务组开发的路由选择协议 总体来说分为以下几个步骤: 1. 在浏览器中输入指定网页的 URL。 2. 浏览器通过 DNS 协议，获取域名对应的 IP 地址。 3. 浏览器根据 IP 地址和端口号，向目标服务器发起一个 TCP 连接请求。 4. 浏览器在 TCP 连接上，向服务器发送一个 HTTP 请求报文，请求获取网页的内容。 5. 服务器收到 HTTP 请求报文后，处理请求，并返回 HTTP 响应报文给浏览器。 6. 浏览器收到 HTTP 响应报文后，解析响应体中的 HTML 代码，渲染网页的结构和样式，同时根据 HTML 中的其他资源的 URL（如图片、CSS、JS 等），再次发起 HTTP 请求，获取这些资源的内容，直到网页完全加载显示。 7. 浏览器在不需要和服务器通信时，可以主动关闭 TCP 连接，或者等待', '> 类似的问题：打开一个网页，整个过程会使用哪些协议？ 先来看一张图（来源于《图解 HTTP》）： 上图有一个错误需要注意：是 OSPF 不是 OPSF。OSPF（Open Shortest Path First，ospf）开放最短路径优先协议，是由 Internet 工程任务组开发的路由选择协议 总体来说分为以下几个步骤: 1. 在浏览器中输入指定网页的 URL。 2. 浏览器通过 DNS 协议，获取域名对应的 IP 地址。 3. 浏览器根据 IP 地址和端口号，向目标服务器发起一个 TCP 连接请求。 4. 浏览器在 TCP 连接上，向服务器发送一个 HTTP 请求报文，请求获取网页的内容。 5. 服务器收到 HTTP 请求报文后，处理请求，并返回 HTTP 响应报文给浏览器。 6. 浏览器收到 HTTP 响应报文后，解析响应体中的 HTML 代码，渲染网页的结构和样式，同时根据 HTML 中的其他资源的 URL（如图片、CSS、JS 等），再次发起 HTTP 请求，获取这些资源的内容，直到网页完全加载显示。 7. 浏览器在不需要和服务器通信时，可以主动关闭 TCP 连接，或者等待', '已发布')
ON DUPLICATE KEY UPDATE source_path=VALUES(source_path), source_url=VALUES(source_url), question_type=VALUES(question_type), stem=VALUES(stem), difficulty=VALUES(difficulty), answer=VALUES(answer), answer_analysis=VALUES(answer_analysis), audit_status=VALUES(audit_status);

INSERT INTO question_knowledge_relation (relation_id, question_id, knowledge_id, weight) VALUES
  ('qk-001', 'jg-q-001', 'kp-001', '1.0'),
  ('qk-002', 'jg-q-002', 'kp-001', '1.0'),
  ('qk-003', 'jg-q-003', 'kp-001', '1.0'),
  ('qk-004', 'jg-q-004', 'kp-002', '1.0'),
  ('qk-005', 'jg-q-005', 'kp-002', '1.0'),
  ('qk-006', 'jg-q-006', 'kp-002', '1.0'),
  ('qk-007', 'jg-q-007', 'kp-003', '1.0'),
  ('qk-008', 'jg-q-008', 'kp-003', '1.0'),
  ('qk-009', 'jg-q-009', 'kp-003', '1.0'),
  ('qk-010', 'jg-q-010', 'kp-004', '1.0'),
  ('qk-011', 'jg-q-011', 'kp-004', '1.0'),
  ('qk-012', 'jg-q-012', 'kp-005', '1.0'),
  ('qk-013', 'jg-q-013', 'kp-005', '1.0'),
  ('qk-014', 'jg-q-014', 'kp-006', '1.0'),
  ('qk-015', 'jg-q-015', 'kp-006', '1.0'),
  ('qk-016', 'jg-q-016', 'kp-006', '1.0'),
  ('qk-017', 'jg-q-017', 'kp-007', '1.0'),
  ('qk-018', 'jg-q-018', 'kp-007', '1.0'),
  ('qk-019', 'jg-q-019', 'kp-007', '1.0'),
  ('qk-020', 'jg-q-020', 'kp-008', '1.0')
ON DUPLICATE KEY UPDATE weight=VALUES(weight);

INSERT INTO question_job_relation (relation_id, question_id, job_id, tech_id, match_level) VALUES
  ('qj-001', 'jg-q-001', 'job-java-backend', 'tech-001', '核心'),
  ('qj-002', 'jg-q-002', 'job-java-backend', 'tech-001', '核心'),
  ('qj-003', 'jg-q-003', 'job-java-backend', 'tech-001', '核心'),
  ('qj-004', 'jg-q-004', 'job-java-backend', 'tech-001', '核心'),
  ('qj-005', 'jg-q-005', 'job-java-backend', 'tech-001', '核心'),
  ('qj-006', 'jg-q-006', 'job-java-backend', 'tech-001', '核心'),
  ('qj-007', 'jg-q-007', 'job-java-backend', 'tech-001', '核心'),
  ('qj-008', 'jg-q-008', 'job-java-backend', 'tech-001', '核心'),
  ('qj-009', 'jg-q-009', 'job-java-backend', 'tech-001', '核心'),
  ('qj-010', 'jg-q-010', 'job-java-backend', 'tech-002', '核心'),
  ('qj-011', 'jg-q-011', 'job-java-backend', 'tech-002', '核心'),
  ('qj-012', 'jg-q-012', 'job-java-backend', 'tech-003', '核心'),
  ('qj-013', 'jg-q-013', 'job-java-backend', 'tech-003', '核心'),
  ('qj-014', 'jg-q-014', 'job-java-backend', 'tech-004', '核心'),
  ('qj-015', 'jg-q-015', 'job-java-backend', 'tech-004', '核心'),
  ('qj-016', 'jg-q-016', 'job-java-backend', 'tech-004', '核心'),
  ('qj-017', 'jg-q-017', 'job-java-backend', 'tech-005', '核心'),
  ('qj-018', 'jg-q-018', 'job-java-backend', 'tech-005', '核心'),
  ('qj-019', 'jg-q-019', 'job-java-backend', 'tech-005', '核心'),
  ('qj-020', 'jg-q-020', 'job-java-backend', 'tech-006', '核心')
ON DUPLICATE KEY UPDATE match_level=VALUES(match_level);
