import type { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { isHeadmanOf, requireAuth, type AuthUser, type Role } from '../auth.js';
import { all, get, run } from '../db.js';
import { ApiError, isIsoDate, parseBody, round2 } from '../util.js';
import { writeAudit } from '../audit.js';

interface GroupRow {
  id: number;
  name: string;
  code: string;
  avatar: string | null;
}

interface SubjectRow {
  id: number;
  name: string;
  group_id: number;
  teacher_id: number | null;
  teacher_name: string | null;
}

interface StudentRow {
  id: number;
  full_name: string;
  avatar: string | null;
}

interface LessonRow {
  id: number;
  subject_id: number;
  date: string;
  pair_number: number;
  topic: string | null;
}

interface RecordRow {
  id: number;
  lesson_id: number;
  student_id: number;
  grade: number | null;
  absent: number;
  reason: string | null;
}

function loadGroup(groupId: number): GroupRow {
  const group = get<GroupRow>('SELECT id, name, code, avatar FROM groups WHERE id = ?', groupId);
  if (!group) throw new ApiError(404, 'Группа не найдена');
  return group;
}

function visibleSubjects(user: AuthUser, groupId: number): SubjectRow[] {
  const subjects = all<SubjectRow>(
    `SELECT s.id, s.name, s.group_id, s.teacher_id, u.full_name AS teacher_name
     FROM subjects s
     LEFT JOIN users u ON u.id = s.teacher_id
     WHERE s.group_id = ?
     ORDER BY s.name COLLATE NOCASE`,
    groupId,
  );
  if (user.role === 'teacher') {
    return subjects.filter((subject) => subject.teacher_id === user.id);
  }
  return subjects;
}

function assertGroupAccess(user: AuthUser, groupId: number): SubjectRow[] {
  if ((user.role === 'student' || user.role === 'headman') && user.groupId !== groupId) {
    throw new ApiError(403, 'Нет доступа к журналу этой группы');
  }
  const subjects = visibleSubjects(user, groupId);
  if (user.role === 'teacher' && subjects.length === 0) {
    throw new ApiError(403, 'Вы не преподаёте в этой группе');
  }
  return subjects;
}

function canManageSubject(
  user: AuthUser,
  subject: { teacher_id: number | null; group_id: number },
): boolean {
  return (
    user.role === 'admin' ||
    isHeadmanOf(user, subject.group_id) ||
    (user.role === 'teacher' && subject.teacher_id === user.id)
  );
}

function studentRows(groupId: number): StudentRow[] {
  return all<StudentRow>(
    `SELECT id, full_name, avatar FROM users
     WHERE group_id = ? AND role IN ('student', 'headman') AND is_active = 1`,
    groupId,
  ).sort((a, b) => a.full_name.localeCompare(b.full_name, 'ru'));
}

function lessonDto(row: LessonRow) {
  return {
    id: row.id,
    subjectId: row.subject_id,
    date: row.date,
    pairNumber: row.pair_number,
    topic: row.topic,
  };
}

function mapRecord(row: RecordRow) {
  return {
    id: row.id,
    lessonId: row.lesson_id,
    studentId: row.student_id,
    grade: row.grade,
    absent: row.absent === 1,
    reason: row.reason,
  };
}

function subjectDto(row: SubjectRow) {
  return {
    id: row.id,
    name: row.name,
    teacherId: row.teacher_id,
    teacherName: row.teacher_name,
  };
}

function averagesByStudent(students: StudentRow[], records: RecordRow[]) {
  const map = new Map<number, { sum: number; count: number; absent: number }>();
  for (const student of students) {
    map.set(student.id, { sum: 0, count: 0, absent: 0 });
  }
  for (const record of records) {
    const item = map.get(record.student_id);
    if (!item) continue;
    if (record.grade !== null) {
      item.sum += record.grade;
      item.count += 1;
    }
    if (record.absent === 1) item.absent += 1;
  }
  return map;
}

const journalRoutes: FastifyPluginAsync = async (app) => {
  app.get('/groups/:id/journal', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    const groupId = Number((request.params as { id: string }).id);
    const query = request.query as { subjectId?: string };

    const group = loadGroup(groupId);
    const subjects = assertGroupAccess(user, groupId);

    let subject: SubjectRow | null = null;
    if (query.subjectId) {
      const requestedId = Number(query.subjectId);
      subject = subjects.find((item) => item.id === requestedId) ?? null;
      if (!subject) throw new ApiError(403, 'Этот предмет вам недоступен');
    } else {
      subject = subjects[0] ?? null;
    }

    const students = studentRows(groupId);
    let lessons: LessonRow[] = [];
    let records: RecordRow[] = [];

    if (subject) {
      lessons = all<LessonRow>(
        `SELECT id, subject_id, date, pair_number, topic
         FROM lessons WHERE subject_id = ?
         ORDER BY date, pair_number`,
        subject.id,
      );
      records = all<RecordRow>(
        `SELECT r.id, r.lesson_id, r.student_id, r.grade, r.absent, r.reason
         FROM records r
         JOIN lessons l ON l.id = r.lesson_id
         WHERE l.subject_id = ?
         ORDER BY l.date, l.pair_number`,
        subject.id,
      );
    }

    const stats = averagesByStudent(students, records);
    const gradeValues = records.filter((record) => record.grade !== null);
    const average =
      gradeValues.length === 0
        ? null
        : gradeValues.reduce((sum, record) => sum + (record.grade ?? 0), 0) /
          gradeValues.length;

    const canEdit =
      user.role === 'admin' ||
      isHeadmanOf(user, groupId) ||
      (user.role === 'teacher' && subject !== null && subject.teacher_id === user.id);

    return {
      group: { id: group.id, name: group.name, code: group.code, avatar: group.avatar },
      subjects: subjects.map(subjectDto),
      subject: subject ? subjectDto(subject) : null,
      students: students.map((student) => {
        const stat = stats.get(student.id) ?? { sum: 0, count: 0, absent: 0 };
        return {
          id: student.id,
          fullName: student.full_name,
          avatar: student.avatar,
          avg: stat.count > 0 ? round2(stat.sum / stat.count) : null,
          gradesCount: stat.count,
          absentPairs: stat.absent,
          absentHours: stat.absent * 2,
        };
      }),
      lessons: lessons.map(lessonDto),
      records: records.map(mapRecord),
      stats: {
        average: round2(average),
        gradesCount: gradeValues.length,
        absentPairs: records.filter((record) => record.absent === 1).length,
        studentsCount: students.length,
      },
      canEdit,
    };
  });

  app.get('/groups/:id/summary', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    const groupId = Number((request.params as { id: string }).id);
    const group = loadGroup(groupId);
    const subjects = assertGroupAccess(user, groupId);
    const students = studentRows(groupId);

    let lessons: LessonRow[] = [];
    let records: RecordRow[] = [];

    if (subjects.length > 0) {
      const placeholders = subjects.map(() => '?').join(',');
      const ids = subjects.map((subject) => subject.id);
      lessons = all<LessonRow>(
        `SELECT id, subject_id, date, pair_number, topic
         FROM lessons WHERE subject_id IN (${placeholders})
         ORDER BY date, pair_number`,
        ...ids,
      );
      records = all<RecordRow>(
        `SELECT r.id, r.lesson_id, r.student_id, r.grade, r.absent, r.reason
         FROM records r
         JOIN lessons l ON l.id = r.lesson_id
         WHERE l.subject_id IN (${placeholders})`,
        ...ids,
      );
    }

    const lessonSubject = new Map(lessons.map((lesson) => [lesson.id, lesson.subject_id]));
    const byStudent = new Map<number, { sum: number; count: number; absent: number }>();
    const byStudentSubject = new Map<
      string,
      { sum: number; count: number; absent: number }
    >();

    for (const student of students) {
      byStudent.set(student.id, { sum: 0, count: 0, absent: 0 });
      for (const subject of subjects) {
        byStudentSubject.set(`${student.id}:${subject.id}`, {
          sum: 0,
          count: 0,
          absent: 0,
        });
      }
    }

    for (const record of records) {
      const subjectId = lessonSubject.get(record.lesson_id);
      if (subjectId === undefined) continue;
      const studentTotal = byStudent.get(record.student_id);
      const subjectTotal = byStudentSubject.get(`${record.student_id}:${subjectId}`);
      const grade = record.grade;
      if (studentTotal) {
        if (grade !== null) {
          studentTotal.sum += grade;
          studentTotal.count += 1;
        }
        if (record.absent === 1) studentTotal.absent += 1;
      }
      if (subjectTotal) {
        if (grade !== null) {
          subjectTotal.sum += grade;
          subjectTotal.count += 1;
        }
        if (record.absent === 1) subjectTotal.absent += 1;
      }
    }

    const subjectStats = subjects.map((subject) => {
      let sum = 0;
      let count = 0;
      let absent = 0;
      for (const student of students) {
        const stat = byStudentSubject.get(`${student.id}:${subject.id}`);
        if (!stat) continue;
        sum += stat.sum;
        count += stat.count;
        absent += stat.absent;
      }
      return {
        ...subjectDto(subject),
        average: count > 0 ? round2(sum / count) : null,
        gradesCount: count,
        absentPairs: absent,
      };
    });

    const allGrades = records.filter((record) => record.grade !== null);
    const groupAverage =
      allGrades.length === 0
        ? null
        : allGrades.reduce((sum, record) => sum + (record.grade ?? 0), 0) / allGrades.length;

    return {
      group: { id: group.id, name: group.name, code: group.code, avatar: group.avatar },
      subjects: subjectStats,
      students: students.map((student) => {
        const total = byStudent.get(student.id) ?? { sum: 0, count: 0, absent: 0 };
        return {
          id: student.id,
          fullName: student.full_name,
          avatar: student.avatar,
          avg: total.count > 0 ? round2(total.sum / total.count) : null,
          gradesCount: total.count,
          absentPairs: total.absent,
          absentHours: total.absent * 2,
          subjectAverages: subjects.map((subject) => {
            const stat = byStudentSubject.get(`${student.id}:${subject.id}`) ?? {
              sum: 0,
              count: 0,
              absent: 0,
            };
            return {
              subjectId: subject.id,
              average: stat.count > 0 ? round2(stat.sum / stat.count) : null,
              gradesCount: stat.count,
            };
          }),
        };
      }),
      stats: {
        average: round2(groupAverage),
        gradesCount: allGrades.length,
        absentPairs: records.filter((record) => record.absent === 1).length,
        studentsCount: students.length,
      },
    };
  });

  app.post('/lessons', { preHandler: requireAuth }, async (request, reply) => {
    const user = request.user!;
    const body = parseBody(
      z.object({
        subjectId: z.number().int().positive(),
        date: z.string().trim(),
        pairNumber: z.number().int().min(1, 'Пара: от 1 до 8').max(8, 'Пара: от 1 до 8'),
        topic: z.string().trim().max(200).optional().nullable(),
      }),
      request.body,
    );

    if (!isIsoDate(body.date)) throw new ApiError(400, 'Некорректная дата');

    const subject = get<SubjectRow>(
      `SELECT s.id, s.name, s.group_id, s.teacher_id, u.full_name AS teacher_name
       FROM subjects s LEFT JOIN users u ON u.id = s.teacher_id
       WHERE s.id = ?`,
      body.subjectId,
    );
    if (!subject) throw new ApiError(404, 'Предмет не найден');
    if (!canManageSubject(user, subject)) {
      throw new ApiError(403, 'Можно добавлять пары только по своим предметам или в своей группе');
    }

    try {
      const created = run(
        `INSERT INTO lessons (subject_id, date, pair_number, topic)
         VALUES (?, ?, ?, ?)`,
        body.subjectId,
        body.date,
        body.pairNumber,
        body.topic?.trim() || null,
      );
      const lesson = get<LessonRow>(
        'SELECT id, subject_id, date, pair_number, topic FROM lessons WHERE id = ?',
        created.lastInsertRowid,
      );
      const group = loadGroup(subject.group_id);
      writeAudit(request, {
        action: 'lesson.create',
        entityType: 'lesson',
        entityId: created.lastInsertRowid,
        groupId: group.id,
        groupName: group.name,
        summary: `Добавлена пара: ${subject.name}, ${body.date}, ${body.pairNumber} пара (${group.name})`,
        details: { subjectId: body.subjectId, date: body.date, pairNumber: body.pairNumber, topic: body.topic ?? null },
      });
      reply.code(201);
      return { lesson: lesson ? lessonDto(lesson) : null };
    } catch (error) {
      if (error instanceof Error && /UNIQUE constraint failed/i.test(error.message)) {
        throw new ApiError(409, 'На эту дату такая пара уже существует');
      }
      throw error;
    }
  });

  app.patch('/lessons/:id', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    const lessonId = Number((request.params as { id: string }).id);
    const existing = get<LessonRow & { teacher_id: number | null; group_id: number; subject_name: string }>(
      `SELECT l.id, l.subject_id, l.date, l.pair_number, l.topic, s.teacher_id, s.group_id,
              s.name AS subject_name
       FROM lessons l JOIN subjects s ON s.id = l.subject_id
       WHERE l.id = ?`,
      lessonId,
    );
    if (!existing) throw new ApiError(404, 'Пара не найдена');
    if (!canManageSubject(user, existing)) {
      throw new ApiError(403, 'Можно изменять только свои пары или пары своей группы');
    }

    const body = parseBody(
      z.object({
        date: z.string().trim().optional(),
        pairNumber: z.number().int().min(1).max(8).optional(),
        topic: z.string().trim().max(200).nullable().optional(),
      }),
      request.body,
    );

    if (body.date !== undefined && !isIsoDate(body.date)) {
      throw new ApiError(400, 'Некорректная дата');
    }

    try {
      if (body.date !== undefined) {
        run('UPDATE lessons SET date = ? WHERE id = ?', body.date, lessonId);
      }
      if (body.pairNumber !== undefined) {
        run('UPDATE lessons SET pair_number = ? WHERE id = ?', body.pairNumber, lessonId);
      }
      if (body.topic !== undefined) {
        run('UPDATE lessons SET topic = ? WHERE id = ?', body.topic || null, lessonId);
      }
    } catch (error) {
      if (error instanceof Error && /UNIQUE constraint failed/i.test(error.message)) {
        throw new ApiError(409, 'На эту дату такая пара уже существует');
      }
      throw error;
    }

    const lesson = get<LessonRow>(
      'SELECT id, subject_id, date, pair_number, topic FROM lessons WHERE id = ?',
      lessonId,
    );
    const group = loadGroup(existing.group_id);
    writeAudit(request, {
      action: 'lesson.update',
      entityType: 'lesson',
      entityId: lessonId,
      groupId: group.id,
      groupName: group.name,
      summary: `Изменена пара: ${existing.subject_name}, ${lesson?.date ?? existing.date}, ${lesson?.pair_number ?? existing.pair_number} пара (${group.name})`,
      details: {
        date: body.date ?? null,
        pairNumber: body.pairNumber ?? null,
        topic: body.topic ?? null,
      },
    });
    return { lesson: lesson ? lessonDto(lesson) : null };
  });

  app.delete('/lessons/:id', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    const lessonId = Number((request.params as { id: string }).id);
    const existing = get<{
      id: number;
      date: string;
      pair_number: number;
      teacher_id: number | null;
      group_id: number;
      subject_name: string;
    }>(
      `SELECT l.id, l.date, l.pair_number, s.teacher_id, s.group_id, s.name AS subject_name
       FROM lessons l JOIN subjects s ON s.id = l.subject_id
       WHERE l.id = ?`,
      lessonId,
    );
    if (!existing) throw new ApiError(404, 'Пара не найдена');
    if (!canManageSubject(user, existing)) {
      throw new ApiError(403, 'Можно удалять только свои пары или пары своей группы');
    }
    const group = loadGroup(existing.group_id);
    run('DELETE FROM lessons WHERE id = ?', lessonId);
    writeAudit(request, {
      action: 'lesson.delete',
      entityType: 'lesson',
      entityId: lessonId,
      groupId: group.id,
      groupName: group.name,
      summary: `Удалена пара: ${existing.subject_name}, ${existing.date}, ${existing.pair_number} пара (${group.name})`,
      details: { date: existing.date, pairNumber: existing.pair_number },
    });
    return { ok: true };
  });

  app.put('/records', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    const body = parseBody(
      z.object({
        lessonId: z.number().int().positive(),
        studentId: z.number().int().positive(),
        grade: z
          .number()
          .int()
          .min(1, 'Оценка должна быть от 1 до 10')
          .max(10, 'Оценка должна быть от 1 до 10')
          .nullable()
          .optional(),
        absent: z.boolean().optional(),
        reason: z.string().trim().max(200, 'Слишком длинная причина').nullable().optional(),
      }),
      request.body,
    );

    const lesson = get<LessonRow & { teacher_id: number | null; group_id: number }>(
      `SELECT l.id, l.subject_id, l.date, l.pair_number, l.topic, s.teacher_id, s.group_id
       FROM lessons l JOIN subjects s ON s.id = l.subject_id
       WHERE l.id = ?`,
      body.lessonId,
    );
    if (!lesson) throw new ApiError(404, 'Пара не найдена');
    if (!canManageSubject(user, lesson)) {
      throw new ApiError(403, 'Можно выставлять оценки только по своим предметам или в своей группе');
    }

    const student = get<{ id: number; full_name: string }>(
      `SELECT id, full_name FROM users
       WHERE id = ? AND group_id = ? AND role IN ('student', 'headman') AND is_active = 1`,
      body.studentId,
      lesson.group_id,
    );
    if (!student) throw new ApiError(400, 'Обучающийся не найден в этой группе');

    const group = loadGroup(lesson.group_id);
    const previous = get<RecordRow>(
      `SELECT id, lesson_id, student_id, grade, absent, reason
       FROM records WHERE lesson_id = ? AND student_id = ?`,
      body.lessonId,
      body.studentId,
    );

    const isAbsent = body.absent === true;
    const grade = isAbsent ? null : (body.grade ?? null);
    const reason = isAbsent && body.reason?.trim() ? body.reason.trim() : null;

    if (grade === null && !isAbsent) {
      run(
        'DELETE FROM records WHERE lesson_id = ? AND student_id = ?',
        body.lessonId,
        body.studentId,
      );
      writeAudit(request, {
        action: 'record.clear',
        entityType: 'record',
        entityId: previous?.id ?? null,
        groupId: group.id,
        groupName: group.name,
        summary: `Очищена отметка: ${student.full_name} · ${lesson.date}, ${lesson.pair_number} пара (${group.name})`,
        details: {
          studentId: body.studentId,
          studentName: student.full_name,
          lessonId: body.lessonId,
          date: lesson.date,
          pairNumber: lesson.pair_number,
          previousGrade: previous?.grade ?? null,
          previousAbsent: previous ? previous.absent === 1 : null,
        },
      });
      return { record: null };
    }

    run(
      `INSERT INTO records (lesson_id, student_id, grade, absent, reason, updated_at)
       VALUES (?, ?, ?, ?, ?, datetime('now'))
       ON CONFLICT(lesson_id, student_id) DO UPDATE SET
         grade = excluded.grade,
         absent = excluded.absent,
         reason = excluded.reason,
         updated_at = datetime('now')`,
      body.lessonId,
      body.studentId,
      grade,
      isAbsent ? 1 : 0,
      reason,
    );

    const record = get<RecordRow>(
      `SELECT id, lesson_id, student_id, grade, absent, reason
       FROM records WHERE lesson_id = ? AND student_id = ?`,
      body.lessonId,
      body.studentId,
    );

    const value = isAbsent ? 'Н (пропуск)' : `оценка ${grade}`;
    writeAudit(request, {
      action: 'record.set',
      entityType: 'record',
      entityId: record?.id ?? null,
      groupId: group.id,
      groupName: group.name,
      summary: `${student.full_name}: ${value} · ${lesson.date}, ${lesson.pair_number} пара (${group.name})`,
      details: {
        studentId: body.studentId,
        studentName: student.full_name,
        lessonId: body.lessonId,
        date: lesson.date,
        pairNumber: lesson.pair_number,
        grade,
        absent: isAbsent,
        reason,
        previousGrade: previous?.grade ?? null,
        previousAbsent: previous ? previous.absent === 1 : null,
      },
    });

    return { record: record ? mapRecord(record) : null };
  });
};

export default journalRoutes;
