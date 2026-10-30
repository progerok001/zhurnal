import { DatabaseSync } from 'node:sqlite';
import bcrypt from 'bcryptjs';
import { config } from './config.js';

export const db = new DatabaseSync(config.dbPath);

db.exec('PRAGMA journal_mode = WAL');
db.exec('PRAGMA foreign_keys = ON');
db.exec('PRAGMA busy_timeout = 5000');

db.exec(`
CREATE TABLE IF NOT EXISTS groups (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT NOT NULL UNIQUE,
  code        TEXT NOT NULL COLLATE NOCASE UNIQUE,
  avatar      TEXT,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS users (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  login          TEXT NOT NULL COLLATE NOCASE UNIQUE,
  password_hash  TEXT NOT NULL,
  role           TEXT NOT NULL CHECK (role IN ('admin', 'teacher', 'student', 'headman')),
  full_name      TEXT NOT NULL,
  group_id       INTEGER REFERENCES groups(id) ON DELETE SET NULL,
  avatar         TEXT,
  is_active      INTEGER NOT NULL DEFAULT 1,
  created_at     TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS subjects (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT NOT NULL,
  group_id    INTEGER NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  teacher_id  INTEGER REFERENCES users(id) ON DELETE SET NULL,
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (group_id, name)
);

CREATE TABLE IF NOT EXISTS lessons (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  subject_id   INTEGER NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
  date         TEXT NOT NULL,
  pair_number  INTEGER NOT NULL CHECK (pair_number BETWEEN 1 AND 8),
  topic        TEXT,
  created_at   TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (subject_id, date, pair_number)
);

CREATE TABLE IF NOT EXISTS records (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  lesson_id   INTEGER NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
  student_id  INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  grade       INTEGER CHECK (grade BETWEEN 1 AND 10),
  absent      INTEGER NOT NULL DEFAULT 0,
  reason      TEXT,
  updated_at  TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (lesson_id, student_id)
);

CREATE TABLE IF NOT EXISTS schedule (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  group_id    INTEGER NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  subject_id  INTEGER NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
  day         INTEGER NOT NULL CHECK (day BETWEEN 1 AND 7),
  pair_number INTEGER NOT NULL CHECK (pair_number BETWEEN 1 AND 8),
  week_type   TEXT NOT NULL DEFAULT 'all' CHECK (week_type IN ('all', 'upper', 'lower')),
  room        TEXT,
  color       TEXT,
  note        TEXT,
  second_subject_id INTEGER REFERENCES subjects(id) ON DELETE SET NULL,
  second_room TEXT,
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at  TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (group_id, day, pair_number, week_type)
);

CREATE TABLE IF NOT EXISTS schedule_times (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  group_id    INTEGER NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  day         INTEGER NOT NULL CHECK (day BETWEEN 1 AND 7),
  pair_number INTEGER NOT NULL CHECK (pair_number BETWEEN 1 AND 8),
  start_time  TEXT NOT NULL,
  end_time    TEXT NOT NULL,
  updated_at  TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (group_id, day, pair_number)
);

CREATE TABLE IF NOT EXISTS homework (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  group_id     INTEGER NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  subject_id   INTEGER NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
  author_id    INTEGER REFERENCES users(id) ON DELETE SET NULL,
  author_name  TEXT NOT NULL,
  author_role  TEXT NOT NULL,
  text         TEXT NOT NULL,
  due_date     TEXT,
  lesson_date  TEXT,
  created_at   TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS audit_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     INTEGER REFERENCES users(id) ON DELETE SET NULL,
  user_login  TEXT NOT NULL,
  user_name   TEXT NOT NULL,
  user_role   TEXT NOT NULL,
  action      TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id   INTEGER,
  group_id    INTEGER,
  group_name  TEXT,
  summary     TEXT NOT NULL,
  details     TEXT,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_users_group ON users(group_id);
CREATE INDEX IF NOT EXISTS idx_subjects_group ON subjects(group_id);
CREATE INDEX IF NOT EXISTS idx_subjects_teacher ON subjects(teacher_id);
CREATE INDEX IF NOT EXISTS idx_lessons_subject ON lessons(subject_id, date, pair_number);
CREATE INDEX IF NOT EXISTS idx_records_lesson ON records(lesson_id);
CREATE INDEX IF NOT EXISTS idx_records_student ON records(student_id);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_schedule_group ON schedule(group_id, day, pair_number);
CREATE INDEX IF NOT EXISTS idx_schedule_subject ON schedule(subject_id);
CREATE INDEX IF NOT EXISTS idx_schedule_times_group ON schedule_times(group_id, day, pair_number);
CREATE INDEX IF NOT EXISTS idx_homework_group_subject ON homework(group_id, subject_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_homework_created ON homework(created_at DESC);
`);

function hasColumn(table: string, column: string): boolean {
  return db
    .prepare(`PRAGMA table_info(${table})`)
    .all()
    .some((row) => (row as { name: string }).name === column);
}

function tableSql(table: string): string {
  const row = get<{ sql: string }>(
    "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?",
    table,
  );
  return row?.sql ?? '';
}

/**
 * Догоняющие миграции для уже существующих баз.
 * SQLite не умеет менять CHECK-ограничение, поэтому таблицу users
 * приходится пересобирать при добавлении новой роли.
 */
function migrate(): void {
  if (!hasColumn('groups', 'avatar')) {
    db.exec('ALTER TABLE groups ADD COLUMN avatar TEXT');
  }
  if (!hasColumn('users', 'avatar')) {
    db.exec('ALTER TABLE users ADD COLUMN avatar TEXT');
  }
  if (!hasColumn('schedule', 'second_subject_id')) {
    db.exec(
      'ALTER TABLE schedule ADD COLUMN second_subject_id INTEGER REFERENCES subjects(id) ON DELETE SET NULL',
    );
  }
  if (!hasColumn('schedule', 'second_room')) {
    db.exec('ALTER TABLE schedule ADD COLUMN second_room TEXT');
  }
  if (!hasColumn('homework', 'lesson_date')) {
    db.exec('ALTER TABLE homework ADD COLUMN lesson_date TEXT');
    // Старые задания не имели даты: привязываем их к ближайшему занятию этого предмета,
    // иначе после обновления они перестали бы открываться из расписания.
    db.exec(`
      UPDATE homework
      SET lesson_date = COALESCE(
        (
          SELECT date(
            homework.created_at,
            '+' || (
              (s.day - (((CAST(strftime('%w', homework.created_at) AS INTEGER) + 6) % 7) + 1) + 7) % 7
            ) || ' days'
          )
          FROM schedule s
          WHERE s.group_id = homework.group_id
            AND (s.subject_id = homework.subject_id OR s.second_subject_id = homework.subject_id)
          ORDER BY (
            (s.day - (((CAST(strftime('%w', homework.created_at) AS INTEGER) + 6) % 7) + 1) + 7) % 7
          ) ASC, s.pair_number ASC
          LIMIT 1
        ),
        date(homework.created_at)
      )
      WHERE homework.lesson_date IS NULL
    `);
  }
  db.exec(
    'CREATE INDEX IF NOT EXISTS idx_homework_lesson ON homework(group_id, subject_id, lesson_date)',
  );

  if (!tableSql('users').includes("'headman'")) {
    db.exec('PRAGMA foreign_keys = OFF');
    try {
      db.exec(`
        BEGIN;
        CREATE TABLE users_migrated (
          id             INTEGER PRIMARY KEY AUTOINCREMENT,
          login          TEXT NOT NULL COLLATE NOCASE UNIQUE,
          password_hash  TEXT NOT NULL,
          role           TEXT NOT NULL CHECK (role IN ('admin', 'teacher', 'student', 'headman')),
          full_name      TEXT NOT NULL,
          group_id       INTEGER REFERENCES groups(id) ON DELETE SET NULL,
          avatar         TEXT,
          is_active      INTEGER NOT NULL DEFAULT 1,
          created_at     TEXT NOT NULL DEFAULT (datetime('now'))
        );
        INSERT INTO users_migrated
          (id, login, password_hash, role, full_name, group_id, avatar, is_active, created_at)
        SELECT id, login, password_hash, role, full_name, group_id, avatar, is_active, created_at
        FROM users;
        DROP TABLE users;
        ALTER TABLE users_migrated RENAME TO users;
        COMMIT;
      `);
    } catch (error) {
      try {
        db.exec('ROLLBACK');
      } catch {
        // транзакции могло не быть
      }
      throw error;
    } finally {
      db.exec('PRAGMA foreign_keys = ON');
    }
  }

  // Индексы могли пропасть вместе со старой таблицей users.
  db.exec(`
    CREATE INDEX IF NOT EXISTS idx_users_group ON users(group_id);
    CREATE INDEX IF NOT EXISTS idx_records_student ON records(student_id);
    CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_log(user_id);
  `);
}

migrate();


export function all<T = unknown>(sql: string, ...params: unknown[]): T[] {
  return db.prepare(sql).all(...(params as never[])) as T[];
}

export function get<T = unknown>(sql: string, ...params: unknown[]): T | undefined {
  return db.prepare(sql).get(...(params as never[])) as T | undefined;
}

export function run(
  sql: string,
  ...params: unknown[]
): { changes: number; lastInsertRowid: number } {
  const result = db.prepare(sql).run(...(params as never[]));
  return {
    changes: Number(result.changes),
    lastInsertRowid: Number(result.lastInsertRowid),
  };
}

export function transaction<T>(fn: () => T): T {
  db.exec('BEGIN');
  try {
    const result = fn();
    db.exec('COMMIT');
    return result;
  } catch (error) {
    db.exec('ROLLBACK');
    throw error;
  }
}

export function seedAdmin(): void {
  const row = get<{ c: number }>("SELECT COUNT(*) AS c FROM users WHERE role = 'admin'");
  if ((row?.c ?? 0) > 0) return;

  const passwordHash = bcrypt.hashSync(config.adminPassword, 10);
  run(
    'INSERT INTO users (login, password_hash, role, full_name) VALUES (?, ?, ?, ?)',
    config.adminLogin,
    passwordHash,
    'admin',
    'Администратор',
  );
  console.log(
    `[seed] Создан администратор: логин "${config.adminLogin}", пароль "${config.adminPassword}"`,
  );
  console.log('[seed] Смените пароль после первого входа (Профиль → Сменить пароль).');
}

seedAdmin();
