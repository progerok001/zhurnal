import type { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { requireAuth, isHeadmanOf, type AuthUser } from '../auth.js';
import { all, get, run, transaction } from '../db.js';
import { ApiError, isIsoDate, parseBody, toInt } from '../util.js';
import { writeAudit } from '../audit.js';

const WEEK_TYPES = ['all', 'upper', 'lower'] as const;
type WeekType = (typeof WEEK_TYPES)[number];

const COLOR_RE = /^#[0-9a-fA-F]{6}$/;
const colorSchema = z
  .string()
  .trim()
  .max(7, 'Цвет должен быть в формате #RRGGBB')
  .refine(
    (value) => value === '' || COLOR_RE.test(value),
    'Цвет должен быть в формате #RRGGBB',
  )
  .nullable()
  .optional();

/** Пустая строка — явная очистка цвета. */
function normalizeColor(value: string | null | undefined): string | null {
  const trimmed = value?.trim() ?? '';
  return trimmed ? trimmed : null;
}

/** Пустая строка — явная очистка срока сдачи. */
function normalizeDueDate(value: string | null | undefined): string | null {
  const trimmed = value?.trim() ?? '';
  return trimmed ? trimmed : null;
}

function addDaysIso(iso: string, days: number): string {
  const date = new Date(`${iso}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** Понедельник учебной недели из query, иначе — понедельник текущей недели. */
function mondayIso(raw: string | undefined): string {
  if (raw) {
    if (!isIsoDate(raw)) throw new ApiError(400, 'Некорректная дата начала недели');
    return raw;
  }
  const now = new Date();
  const offset = (now.getUTCDay() + 6) % 7;
  return addDaysIso(now.toISOString().slice(0, 10), -offset);
}

/** Ключ «предмет + дата»: ДЗ относится только к своей паре в свой день. */
function homeworkKey(subjectId: number, lessonDate: string): string {
  return `${subjectId}|${lessonDate}`;
}

const TIME_RE = /^([01]?\d|2[0-3]):[0-5]\d$/;
const timeSchema = z
  .string()
  .trim()
  .regex(TIME_RE, 'Время в формате ЧЧ:ММ')
  .transform((value) => {
    const [hours, minutes] = value.split(':');
    return `${hours.padStart(2, '0')}:${minutes}`;
  });

/** 0 и пустое значение означают «второй половины нет». */
function normalizeSecondSubject(value: number | null | undefined): number | null {
  return value && value > 0 ? value : null;
}

/** Проверяет предмет второй половины пары и возвращает его. */
function validateSecondSubject(
  secondId: number | null,
  firstSubjectId: number,
  groupId: number,
): SubjectRow | null {
  if (secondId == null) return null;
  if (secondId === firstSubjectId) {
    throw new ApiError(400, 'Вторая половина пары должна быть другим предметом');
  }
  const second = requireSubject(secondId);
  if (second.group_id !== groupId) {
    throw new ApiError(400, 'Предмет относится к другой группе');
  }
  return second;
}

interface GroupBrief {
  id: number;
  name: string;
  avatar: string | null;
}

interface ScheduleRow {
  id: number;
  group_id: number;
  subject_id: number;
  day: number;
  pair_number: number;
  week_type: WeekType;
  room: string | null;
  color: string | null;
  note: string | null;
  second_subject_id: number | null;
  second_room: string | null;
  subject_name: string;
  teacher_id: number | null;
  teacher_name: string | null;
  second_subject_name: string | null;
  second_teacher_id: number | null;
  second_teacher_name: string | null;
}

interface HomeworkRow {
  id: number;
  group_id: number;
  subject_id: number;
  author_id: number | null;
  author_name: string;
  author_role: string;
  author_avatar: string | null;
  text: string;
  due_date: string | null;
  lesson_date: string | null;
  created_at: string;
  updated_at: string;
}

const HOMEWORK_SELECT = `
  SELECT h.id, h.group_id, h.subject_id, h.author_id, h.author_name, h.author_role,
         h.text, h.due_date, h.lesson_date, h.created_at, h.updated_at,
         COALESCE(u.avatar, NULL) AS author_avatar
  FROM homework h
  LEFT JOIN users u ON u.id = h.author_id
`;

function requireGroup(groupId: number): GroupBrief {
  const group = get<GroupBrief>('SELECT id, name, avatar FROM groups WHERE id = ?', groupId);
  if (!group) throw new ApiError(404, 'Группа не найдена');
  return group;
}

interface SubjectRow {
  id: number;
  name: string;
  group_id: number;
  teacher_id: number | null;
  teacher_name: string | null;
}

function requireSubject(subjectId: number): SubjectRow {
  const subject = get<SubjectRow>(
    `SELECT s.id, s.name, s.group_id, s.teacher_id, u.full_name AS teacher_name
     FROM subjects s
     LEFT JOIN users u ON u.id = s.teacher_id
     WHERE s.id = ?`,
    subjectId,
  );
  if (!subject) throw new ApiError(404, 'Предмет не найден');
  return subject;
}

function teachesInGroup(userId: number, groupId: number): boolean {
  return !!get<{ id: number }>(
    'SELECT id FROM subjects WHERE group_id = ? AND teacher_id = ? LIMIT 1',
    groupId,
    userId,
  );
}

/** Единое расписание групп ведёт только администратор. */
function canEditSchedule(user: AuthUser, _groupId: number): boolean {
  return user.role === 'admin';
}

/** ДЗ своей группы видят все её участники, преподаватели группы и администратор. */
function canViewHomework(user: AuthUser, groupId: number): boolean {
  if (user.role === 'admin') return true;
  if (user.groupId === groupId) return true;
  return user.role === 'teacher' && teachesInGroup(user.id, groupId);
}

/** Писать ДЗ может любой участник группы, староста, преподаватель и админ. */
function canPostHomework(user: AuthUser, groupId: number): boolean {
  return canViewHomework(user, groupId);
}

function canModerateHomework(user: AuthUser, groupId: number): boolean {
  if (user.role === 'admin') return true;
  if (isHeadmanOf(user, groupId)) return true;
  return user.role === 'teacher' && teachesInGroup(user.id, groupId);
}

function mapHomework(row: HomeworkRow, user: AuthUser) {
  return {
    id: row.id,
    groupId: row.group_id,
    subjectId: row.subject_id,
    text: row.text,
    dueDate: row.due_date,
    lessonDate: row.lesson_date,
    authorId: row.author_id,
    authorName: row.author_name,
    authorRole: row.author_role,
    authorAvatar: row.author_avatar,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    canEdit: row.author_id === user.id || canModerateHomework(user, row.group_id),
  };
}

function mapSchedule(row: ScheduleRow) {
  return {
    id: row.id,
    groupId: row.group_id,
    subjectId: row.subject_id,
    subjectName: row.subject_name,
    teacherId: row.teacher_id,
    teacherName: row.teacher_name,
    day: row.day,
    pairNumber: row.pair_number,
    weekType: row.week_type,
    room: row.room,
    color: row.color,
    note: row.note,
    split: row.second_subject_id != null,
    secondSubjectId: row.second_subject_id,
    secondSubjectName: row.second_subject_name,
    secondTeacherId: row.second_teacher_id,
    secondTeacherName: row.second_teacher_name,
    secondRoom: row.second_room,
  };
}

function sqliteDateTime(date: Date): string {
  return date.toISOString().slice(0, 19).replace('T', ' ');
}

function periodStart(period: 'week' | 'month'): string {
  const now = new Date();
  if (period === 'month') {
    return sqliteDateTime(
      new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1)),
    );
  }
  const mondayOffset = (now.getUTCDay() + 6) % 7;
  return sqliteDateTime(
    new Date(
      Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - mondayOffset),
    ),
  );
}

const scheduleRoutes: FastifyPluginAsync = async (app) => {
  app.get('/schedule/groups', { preHandler: requireAuth }, async () => {
    const rows = all<GroupBrief>(
      `SELECT g.id, g.name, g.avatar
       FROM groups g
       ORDER BY g.name COLLATE NOCASE`,
    );
    return { groups: rows };
  });

  app.get('/schedule', { preHandler: requireAuth }, async (request) => {
    const query = request.query as { groupId?: string; week?: string; weekStart?: string };
    const groupId = toInt(query.groupId);
    if (!groupId) throw new ApiError(400, 'Укажите группу');
    const group = requireGroup(groupId);
    const user = request.user!;
    const weekStart = mondayIso(query.weekStart);

    const weekParam = query.week === 'lower' ? 'lower' : 'upper';
    const week: WeekType = weekParam === 'lower' ? 'lower' : 'upper';

    const rows = all<ScheduleRow>(
      `SELECT sch.id, sch.group_id, sch.subject_id, sch.day, sch.pair_number, sch.week_type,
              sch.room, sch.color, sch.note, sch.second_subject_id, sch.second_room,
              s.name AS subject_name, s.teacher_id, t.full_name AS teacher_name,
              s2.name AS second_subject_name, s2.teacher_id AS second_teacher_id,
              t2.full_name AS second_teacher_name
       FROM schedule sch
       JOIN subjects s ON s.id = sch.subject_id
       LEFT JOIN subjects s2 ON s2.id = sch.second_subject_id
       LEFT JOIN users t ON t.id = s.teacher_id
       LEFT JOIN users t2 ON t2.id = s2.teacher_id
       WHERE sch.group_id = ? AND sch.week_type IN ('all', ?)
       ORDER BY sch.day, sch.pair_number`,
      groupId,
      week,
    );

    // Если на слот есть пара конкретной недели, она важнее общей пары «каждую неделю».
    const bySlot = new Map<string, ScheduleRow>();
    for (const row of rows) {
      const key = `${row.day}-${row.pair_number}`;
      const previous = bySlot.get(key);
      if (!previous || (previous.week_type === 'all' && row.week_type !== 'all')) {
        bySlot.set(key, row);
      }
    }
    const effectiveRows = [...bySlot.values()].sort(
      (a, b) => a.day - b.day || a.pair_number - b.pair_number,
    );

    const viewHomework = canViewHomework(user, groupId);
    const homeworkBySlot = new Map<string, HomeworkRow[]>();
    if (viewHomework) {
      // Показываем только ДЗ, закреплённые за днями выбранной недели.
      const weekEnd = addDaysIso(weekStart, 6);
      const homeworkRows = all<HomeworkRow>(
        `${HOMEWORK_SELECT}
         WHERE h.group_id = ? AND h.lesson_date BETWEEN ? AND ?
         ORDER BY h.created_at DESC, h.id DESC`,
        groupId,
        weekStart,
        weekEnd,
      );
      for (const row of homeworkRows) {
        if (!row.lesson_date) continue;
        const key = homeworkKey(row.subject_id, row.lesson_date);
        const list = homeworkBySlot.get(key) ?? [];
        list.push(row);
        homeworkBySlot.set(key, list);
      }
    }

    // Время пар можно переопределить для конкретного дня (например, короткая суббота).
    const times = all<{
      day: number;
      pair_number: number;
      start_time: string;
      end_time: string;
    }>(
      `SELECT day, pair_number, start_time, end_time
       FROM schedule_times
       WHERE group_id = ?
       ORDER BY day, pair_number`,
      groupId,
    ).map((row) => ({
      day: row.day,
      pairNumber: row.pair_number,
      startTime: row.start_time,
      endTime: row.end_time,
    }));

    return {
      group,
      week,
      entries: effectiveRows.map((row) => {
        const lessonDate = addDaysIso(weekStart, row.day - 1);
        const homework = homeworkBySlot.get(homeworkKey(row.subject_id, lessonDate)) ?? [];
        const secondHomework = row.second_subject_id != null
          ? homeworkBySlot.get(homeworkKey(row.second_subject_id, lessonDate)) ?? []
          : [];
        return {
          ...mapSchedule(row),
          lessonDate,
          homeworkCount: homework.length,
          secondHomeworkCount: secondHomework.length,
          homeworkPreview: homework[0]
            ? {
                id: homework[0].id,
                text: homework[0].text,
                authorName: homework[0].author_name,
                createdAt: homework[0].created_at,
              }
            : null,
        };
      }),
      times,
      canEdit: canEditSchedule(user, groupId),
      canViewHomework: viewHomework,
      isOwnGroup: user.groupId === groupId,
    };
  });

  app.post('/schedule', { preHandler: requireAuth }, async (request, reply) => {
    const user = request.user!;
    const body = parseBody(
      z.object({
        groupId: z.number().int().positive(),
        subjectId: z.number().int().positive(),
        day: z.number().int().min(1, 'День недели — от 1 до 7').max(7),
        pairNumber: z.number().int().min(1, 'Номер пары — от 1 до 8').max(8),
        weekType: z.enum(WEEK_TYPES).optional(),
        room: z.string().trim().max(40).nullable().optional(),
        color: colorSchema,
        note: z.string().trim().max(200).nullable().optional(),
        secondSubjectId: z.number().int().min(0).nullable().optional(),
        secondRoom: z.string().trim().max(40).nullable().optional(),
      }),
      request.body,
    );

    const group = requireGroup(body.groupId);
    if (!canEditSchedule(user, body.groupId)) {
      throw new ApiError(403, 'Расписание может менять только администратор');
    }
    const subject = requireSubject(body.subjectId);
    if (subject.group_id !== body.groupId) {
      throw new ApiError(400, 'Предмет относится к другой группе');
    }
    const second = validateSecondSubject(
      normalizeSecondSubject(body.secondSubjectId),
      body.subjectId,
      body.groupId,
    );

    const weekType: WeekType = body.weekType ?? 'all';
    try {
      const created = run(
        `INSERT INTO schedule
           (group_id, subject_id, day, pair_number, week_type, room, color, note,
            second_subject_id, second_room)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        body.groupId,
        body.subjectId,
        body.day,
        body.pairNumber,
        weekType,
        body.room?.trim() || null,
        normalizeColor(body.color),
        body.note?.trim() || null,
        second?.id ?? null,
        second ? body.secondRoom?.trim() || null : null,
      );

      writeAudit(request, {
        action: 'schedule.create',
        entityType: 'schedule',
        entityId: created.lastInsertRowid,
        groupId: body.groupId,
        groupName: group.name,
        summary: second
          ? `В расписание ${group.name} добавлена пара из двух предметов: ${subject.name} / ${second.name}, день ${body.day}, ${body.pairNumber} пара`
          : `В расписание ${group.name} добавлена пара: ${subject.name}, день ${body.day}, ${body.pairNumber} пара`,
        details: {
          weekType,
          room: body.room ?? null,
          color: normalizeColor(body.color),
          secondSubjectId: second?.id ?? null,
        },
      });
      reply.code(201);
      return { id: created.lastInsertRowid };
    } catch (error) {
      if (error instanceof Error && /UNIQUE constraint failed/i.test(error.message)) {
        throw new ApiError(409, 'На это время уже стоит пара — измените существующую');
      }
      throw error;
    }
  });

  app.patch('/schedule/:id', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    const id = toInt((request.params as { id: string }).id);
    if (!id) throw new ApiError(400, 'Некорректная пара');
    const existing = get<ScheduleRow>(
      `SELECT sch.id, sch.group_id, sch.subject_id, sch.day, sch.pair_number, sch.week_type,
              sch.room, sch.color, sch.note, sch.second_subject_id, sch.second_room,
              s.name AS subject_name, s.teacher_id, t.full_name AS teacher_name,
              s2.name AS second_subject_name, s2.teacher_id AS second_teacher_id,
              t2.full_name AS second_teacher_name
       FROM schedule sch
       JOIN subjects s ON s.id = sch.subject_id
       LEFT JOIN subjects s2 ON s2.id = sch.second_subject_id
       LEFT JOIN users t ON t.id = s.teacher_id
       LEFT JOIN users t2 ON t2.id = s2.teacher_id
       WHERE sch.id = ?`,
      id,
    );
    if (!existing) throw new ApiError(404, 'Пара не найдена');
    if (!canEditSchedule(user, existing.group_id)) {
      throw new ApiError(403, 'Расписание может менять только администратор');
    }

    const body = parseBody(
      z.object({
        subjectId: z.number().int().positive().optional(),
        day: z.number().int().min(1).max(7).optional(),
        pairNumber: z.number().int().min(1).max(8).optional(),
        weekType: z.enum(WEEK_TYPES).optional(),
        room: z.string().trim().max(40).nullable().optional(),
        color: colorSchema,
        note: z.string().trim().max(200).nullable().optional(),
        secondSubjectId: z.number().int().min(0).nullable().optional(),
        secondRoom: z.string().trim().max(40).nullable().optional(),
      }),
      request.body,
    );

    const nextSubjectId = body.subjectId ?? existing.subject_id;
    const subject = requireSubject(nextSubjectId);
    if (subject.group_id !== existing.group_id) {
      throw new ApiError(400, 'Предмет относится к другой группе');
    }
    const nextSecondRaw = body.secondSubjectId !== undefined
      ? normalizeSecondSubject(body.secondSubjectId)
      : existing.second_subject_id;
    const second = validateSecondSubject(nextSecondRaw, nextSubjectId, existing.group_id);
    const nextSecondRoom = second
      ? (body.secondRoom !== undefined
          ? body.secondRoom?.trim() || null
          : existing.second_room)
      : null;

    try {
      run(
        `UPDATE schedule
         SET subject_id = ?, day = ?, pair_number = ?, week_type = ?, room = ?, color = ?, note = ?,
             second_subject_id = ?, second_room = ?, updated_at = datetime('now')
         WHERE id = ?`,
        nextSubjectId,
        body.day ?? existing.day,
        body.pairNumber ?? existing.pair_number,
        body.weekType ?? existing.week_type,
        body.room !== undefined ? body.room?.trim() || null : existing.room,
        body.color !== undefined ? normalizeColor(body.color) : existing.color,
        body.note !== undefined ? body.note?.trim() || null : existing.note,
        second?.id ?? null,
        nextSecondRoom,
        id,
      );
    } catch (error) {
      if (error instanceof Error && /UNIQUE constraint failed/i.test(error.message)) {
        throw new ApiError(409, 'На это время уже стоит пара — измените существующую');
      }
      throw error;
    }

    const group = requireGroup(existing.group_id);
    writeAudit(request, {
      action: 'schedule.update',
      entityType: 'schedule',
      entityId: id,
      groupId: existing.group_id,
      groupName: group.name,
      summary: second
        ? `Изменена пара в расписании ${group.name}: ${subject.name} / ${second.name}, день ${body.day ?? existing.day}, ${body.pairNumber ?? existing.pair_number} пара`
        : `Изменена пара в расписании ${group.name}: ${subject.name}, день ${body.day ?? existing.day}, ${body.pairNumber ?? existing.pair_number} пара`,
      details: {
        movedToWeek: body.weekType ?? null,
        color: normalizeColor(body.color),
        secondSubjectId: second?.id ?? null,
      },
    });
    return { ok: true };
  });

  app.delete('/schedule/:id', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    const id = toInt((request.params as { id: string }).id);
    if (!id) throw new ApiError(400, 'Некорректная пара');
    const existing = get<ScheduleRow>(
      `SELECT sch.id, sch.group_id, sch.subject_id, sch.day, sch.pair_number, sch.week_type,
              sch.room, sch.color, sch.note,
              s.name AS subject_name, s.teacher_id, t.full_name AS teacher_name
       FROM schedule sch
       JOIN subjects s ON s.id = sch.subject_id
       LEFT JOIN users t ON t.id = s.teacher_id
       WHERE sch.id = ?`,
      id,
    );
    if (!existing) throw new ApiError(404, 'Пара не найдена');
    if (!canEditSchedule(user, existing.group_id)) {
      throw new ApiError(403, 'Расписание может менять только администратор');
    }
    run('DELETE FROM schedule WHERE id = ?', id);
    const group = requireGroup(existing.group_id);
    writeAudit(request, {
      action: 'schedule.delete',
      entityType: 'schedule',
      entityId: id,
      groupId: existing.group_id,
      groupName: group.name,
      summary: `Удалена пара из расписания ${group.name}: ${existing.subject_name}, день ${existing.day}, ${existing.pair_number} пара`,
      details: null,
    });
    return { ok: true };
  });

  app.put('/schedule/times', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    const body = parseBody(
      z.object({
        groupId: z.number().int().positive(),
        day: z.number().int().min(1, 'День недели — от 1 до 7').max(7),
        entries: z
          .array(
            z.object({
              pairNumber: z.number().int().min(1).max(8),
              startTime: timeSchema,
              endTime: timeSchema,
            }),
          )
          .max(8, 'Не больше 8 пар в день'),
      }),
      request.body,
    );

    const group = requireGroup(body.groupId);
    if (!canEditSchedule(user, body.groupId)) {
      throw new ApiError(403, 'Расписание может менять только администратор');
    }

    const seen = new Set<number>();
    for (const entry of body.entries) {
      if (seen.has(entry.pairNumber)) {
        throw new ApiError(400, 'Пара указана дважды');
      }
      seen.add(entry.pairNumber);
      if (entry.startTime >= entry.endTime) {
        throw new ApiError(400, 'Начало пары должно быть раньше конца');
      }
    }

    transaction(() => {
      // Пустой список означает «вернуть обычное время» для этого дня.
      run('DELETE FROM schedule_times WHERE group_id = ? AND day = ?', body.groupId, body.day);
      for (const entry of body.entries) {
        run(
          `INSERT INTO schedule_times (group_id, day, pair_number, start_time, end_time)
           VALUES (?, ?, ?, ?, ?)`,
          body.groupId,
          body.day,
          entry.pairNumber,
          entry.startTime,
          entry.endTime,
        );
      }
    });

    writeAudit(request, {
      action: 'schedule.times',
      entityType: 'schedule',
      entityId: null,
      groupId: body.groupId,
      groupName: group.name,
      summary: `Изменено время пар в расписании ${group.name}, день ${body.day}`,
      details: { day: body.day, entries: body.entries },
    });
    return { ok: true };
  });

  app.get('/homework', { preHandler: requireAuth }, async (request) => {
    const query = request.query as { groupId?: string; subjectId?: string; date?: string };
    const groupId = toInt(query.groupId);
    const subjectId = toInt(query.subjectId);
    if (!groupId || !subjectId) throw new ApiError(400, 'Укажите группу и предмет');
    const date = query.date?.trim() || null;
    if (date && !isIsoDate(date)) throw new ApiError(400, 'Некорректная дата занятия');
    const user = request.user!;
    const group = requireGroup(groupId);
    const subject = requireSubject(subjectId);
    if (subject.group_id !== groupId) {
      throw new ApiError(400, 'Предмет относится к другой группе');
    }
    if (!canViewHomework(user, groupId)) {
      throw new ApiError(403, 'Домашние задания доступны только участникам группы');
    }

    const rows = date
      ? all<HomeworkRow>(
          `${HOMEWORK_SELECT}
           WHERE h.group_id = ? AND h.subject_id = ? AND h.lesson_date = ?
           ORDER BY h.created_at DESC, h.id DESC`,
          groupId,
          subjectId,
          date,
        )
      : all<HomeworkRow>(
          `${HOMEWORK_SELECT}
           WHERE h.group_id = ? AND h.subject_id = ?
           ORDER BY h.created_at DESC, h.id DESC`,
          groupId,
          subjectId,
        );

    return {
      group,
      subject: {
        id: subject.id,
        name: subject.name,
        teacherId: subject.teacher_id,
        teacherName: subject.teacher_name,
      },
      date,
      items: rows.map((row) => mapHomework(row, user)),
      canPost: canPostHomework(user, groupId),
      canModerate: canModerateHomework(user, groupId),
      isOwnGroup: user.groupId === groupId,
    };
  });

  app.post('/homework', { preHandler: requireAuth }, async (request, reply) => {
    const user = request.user!;
    const body = parseBody(
      z.object({
        groupId: z.number().int().positive(),
        subjectId: z.number().int().positive(),
        text: z.string().trim().min(3, 'Запишите задание').max(4000, 'Слишком длинное задание'),
        dueDate: z.string().trim().nullable().optional(),
        lessonDate: z.string().trim().nullable().optional(),
      }),
      request.body,
    );

    const group = requireGroup(body.groupId);
    const subject = requireSubject(body.subjectId);
    if (subject.group_id !== body.groupId) {
      throw new ApiError(400, 'Предмет относится к другой группе');
    }
    if (!canPostHomework(user, body.groupId)) {
      throw new ApiError(403, 'Недостаточно прав для записи домашнего задания');
    }
    if (body.dueDate && !isIsoDate(body.dueDate)) {
      throw new ApiError(400, 'Некорректная дата сдачи');
    }
    if (body.lessonDate && !isIsoDate(body.lessonDate)) {
      throw new ApiError(400, 'Некорректная дата занятия');
    }

    const created = run(
      `INSERT INTO homework
        (group_id, subject_id, author_id, author_name, author_role, text, due_date, lesson_date)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      body.groupId,
      body.subjectId,
      user.id,
      user.fullName,
      user.role,
      body.text,
      normalizeDueDate(body.dueDate),
      normalizeDueDate(body.lessonDate),
    );

    writeAudit(request, {
      action: 'homework.create',
      entityType: 'homework',
      entityId: created.lastInsertRowid,
      groupId: body.groupId,
      groupName: group.name,
      summary: `Добавлено ДЗ по предмету «${subject.name}» (${group.name})` +
        (body.lessonDate ? ` на ${body.lessonDate}` : ''),
      details: {
        subjectId: body.subjectId,
        dueDate: normalizeDueDate(body.dueDate),
        lessonDate: normalizeDueDate(body.lessonDate),
      },
    });
    reply.code(201);
    return { id: created.lastInsertRowid };
  });

  app.patch('/homework/:id', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    const id = toInt((request.params as { id: string }).id);
    if (!id) throw new ApiError(400, 'Некорректная запись');
    const existing = get<HomeworkRow>(`${HOMEWORK_SELECT} WHERE h.id = ?`, id);
    if (!existing) throw new ApiError(404, 'Запись не найдена');
    if (existing.author_id !== user.id && !canModerateHomework(user, existing.group_id)) {
      throw new ApiError(403, 'Можно редактировать только своё задание');
    }

    const body = parseBody(
      z.object({
        text: z.string().trim().min(3, 'Запишите задание').max(4000).optional(),
        dueDate: z.string().trim().nullable().optional(),
      }),
      request.body,
    );
    if (body.dueDate && !isIsoDate(body.dueDate)) {
      throw new ApiError(400, 'Некорректная дата сдачи');
    }

    run(
      `UPDATE homework
       SET text = ?, due_date = ?, updated_at = datetime('now')
       WHERE id = ?`,
      body.text ?? existing.text,
      body.dueDate !== undefined ? normalizeDueDate(body.dueDate) : existing.due_date,
      id,
    );

    const subject = requireSubject(existing.subject_id);
    const group = requireGroup(existing.group_id);
    writeAudit(request, {
      action: 'homework.update',
      entityType: 'homework',
      entityId: id,
      groupId: existing.group_id,
      groupName: group.name,
      summary: `Изменено ДЗ по предмету «${subject.name}» (${group.name})`,
      details: { dueDate: normalizeDueDate(body.dueDate) },
    });
    return { ok: true };
  });

  app.delete('/homework/:id', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    const id = toInt((request.params as { id: string }).id);
    if (!id) throw new ApiError(400, 'Некорректная запись');
    const existing = get<HomeworkRow>(`${HOMEWORK_SELECT} WHERE h.id = ?`, id);
    if (!existing) throw new ApiError(404, 'Запись не найдена');
    if (existing.author_id !== user.id && !canModerateHomework(user, existing.group_id)) {
      throw new ApiError(403, 'Можно удалить только своё задание');
    }
    run('DELETE FROM homework WHERE id = ?', id);
    const subject = requireSubject(existing.subject_id);
    const group = requireGroup(existing.group_id);
    writeAudit(request, {
      action: 'homework.delete',
      entityType: 'homework',
      entityId: id,
      groupId: existing.group_id,
      groupName: group.name,
      summary: `Удалено ДЗ по предмету «${subject.name}» (${group.name})`,
      details: null,
    });
    return { ok: true };
  });

  app.get('/homework/top', { preHandler: requireAuth }, async (request) => {
    const query = request.query as { groupId?: string; period?: string };
    const groupId = toInt(query.groupId);
    if (!groupId) throw new ApiError(400, 'Укажите группу');
    const user = request.user!;
    const group = requireGroup(groupId);
    if (!canViewHomework(user, groupId)) {
      throw new ApiError(403, 'Топ доступен только участникам группы');
    }

    const period = query.period === 'month' ? 'month' : 'week';
    const since = periodStart(period);

    const rows = all<{
      author_id: number | null;
      author_name: string;
      author_role: string;
      avatar: string | null;
      count: number;
      last_at: string;
    }>(
      `SELECT h.author_id, h.author_name, h.author_role,
              u.avatar AS avatar, COUNT(*) AS count, MAX(h.created_at) AS last_at
       FROM homework h
       LEFT JOIN users u ON u.id = h.author_id
       WHERE h.group_id = ? AND h.created_at >= ?
       GROUP BY h.author_id, h.author_name, h.author_role, u.avatar
       ORDER BY count DESC, last_at DESC`,
      groupId,
      since,
    );

    const total = rows.reduce((sum, row) => sum + Number(row.count), 0);

    return {
      group,
      period,
      since,
      total,
      items: rows.map((row) => ({
        userId: row.author_id,
        fullName: row.author_name,
        role: row.author_role,
        avatar: row.avatar,
        count: Number(row.count),
        lastAt: row.last_at,
      })),
    };
  });
};

export default scheduleRoutes;
