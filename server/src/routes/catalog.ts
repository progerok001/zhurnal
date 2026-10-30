import type { FastifyPluginAsync } from 'fastify';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { requireAuth, requireRole, type Role } from '../auth.js';
import { MAX_AVATAR_LENGTH, normalizeAvatar } from '../avatar.js';
import { all, get, run } from '../db.js';
import { mapUser, USER_SELECT } from '../mappers.js';
import { ApiError, parseBody, round2 } from '../util.js';
import { writeAudit } from '../audit.js';

interface GroupRow {
  id: number;
  name: string;
  code: string;
  avatar: string | null;
  created_at: string;
  students_count: number;
  subjects_count: number;
  absent_pairs: number;
  average: number | null;
}

const GROUP_SELECT = `
  SELECT g.id, g.name, g.code, g.avatar, g.created_at,
    (SELECT COUNT(*) FROM users u
      WHERE u.group_id = g.id AND u.role IN ('student', 'headman') AND u.is_active = 1) AS students_count,
    (SELECT COUNT(*) FROM subjects s WHERE s.group_id = g.id) AS subjects_count,
    (SELECT COUNT(*) FROM records r
      JOIN lessons l ON l.id = r.lesson_id
      JOIN subjects s ON s.id = l.subject_id
      WHERE s.group_id = g.id AND r.absent = 1) AS absent_pairs,
    (SELECT AVG(r.grade) FROM records r
      JOIN lessons l ON l.id = r.lesson_id
      JOIN subjects s ON s.id = l.subject_id
      WHERE s.group_id = g.id AND r.grade IS NOT NULL) AS average
  FROM groups g
`;

function mapGroup(row: GroupRow) {
  return {
    id: row.id,
    name: row.name,
    code: row.code,
    avatar: row.avatar ?? null,
    studentsCount: Number(row.students_count),
    subjectsCount: Number(row.subjects_count),
    absentPairs: Number(row.absent_pairs),
    average: round2(row.average),
    createdAt: row.created_at,
  };
}

function isUniqueError(error: unknown): boolean {
  return error instanceof Error && /UNIQUE constraint failed/i.test(error.message);
}

const avatarSchema = z
  .string()
  .trim()
  .max(MAX_AVATAR_LENGTH, 'Слишком большая аватарка')
  .nullable()
  .optional();

function roleTitle(role: string): string {
  switch (role) {
    case 'admin':
      return 'администратор';
    case 'teacher':
      return 'преподаватель';
    case 'headman':
      return 'староста';
    default:
      return 'обучающийся';
  }
}

function requireGroup(id: number) {
  const group = get<{ id: number; name: string; code: string; avatar: string | null }>(
    'SELECT id, name, code, avatar FROM groups WHERE id = ?',
    id,
  );
  if (!group) throw new ApiError(404, 'Группа не найдена');
  return group;
}

const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function generateGroupCode(): string {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    let code = '';
    for (let i = 0; i < 6; i += 1) {
      code += alphabet[Math.floor(Math.random() * alphabet.length)];
    }
    const exists = get<{ id: number }>('SELECT id FROM groups WHERE code = ?', code);
    if (!exists) return code;
  }
  return `G${Date.now().toString(36).toUpperCase().slice(-6)}`;
}

const groupRoutes: FastifyPluginAsync = async (app) => {
  app.get('/groups', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    let rows: GroupRow[];

    if (user.role === 'admin') {
      rows = all<GroupRow>(`${GROUP_SELECT} ORDER BY g.name COLLATE NOCASE`);
    } else if (user.role === 'teacher') {
      rows = all<GroupRow>(
        `${GROUP_SELECT}
         WHERE EXISTS (
           SELECT 1 FROM subjects s2
           WHERE s2.group_id = g.id AND s2.teacher_id = ?
         )
         ORDER BY g.name COLLATE NOCASE`,
        user.id,
      );
    } else if (user.groupId) {
      rows = all<GroupRow>(
        `${GROUP_SELECT} WHERE g.id = ? ORDER BY g.name COLLATE NOCASE`,
        user.groupId,
      );
    } else {
      rows = [];
    }

    return { groups: rows.map(mapGroup) };
  });

  app.post(
    '/groups',
    { preHandler: [requireAuth, requireRole('admin')] },
    async (request, reply) => {
      const body = parseBody(
        z.object({
          name: z.string().trim().min(2, 'Название группы слишком короткое').max(60),
          code: z
            .string()
            .trim()
            .regex(/^[A-Za-z0-9_-]{3,32}$/, 'Код: 3–32 символа (латиница, цифры, _ и -)')
            .optional(),
          avatar: avatarSchema,
        }),
        request.body,
      );

      const code = (body.code ?? generateGroupCode()).toUpperCase();
      try {
        const created = run(
          'INSERT INTO groups (name, code, avatar) VALUES (?, ?, ?)',
          body.name,
          code,
          normalizeAvatar(body.avatar),
        );
        const row = get<GroupRow>(`${GROUP_SELECT} WHERE g.id = ?`, created.lastInsertRowid);
        writeAudit(request, {
          action: 'group.create',
          entityType: 'group',
          entityId: created.lastInsertRowid,
          groupId: created.lastInsertRowid,
          groupName: body.name,
          summary: `Создана группа «${body.name}» (код ${code})`,
          details: { name: body.name, code, avatarChanged: !!body.avatar },
        });
        reply.code(201);
        return { group: row ? mapGroup(row) : null };
      } catch (error) {
        if (isUniqueError(error)) {
          throw new ApiError(409, 'Группа с таким названием или кодом уже существует');
        }
        throw error;
      }
    },
  );

  app.patch(
    '/groups/:id',
    { preHandler: [requireAuth, requireRole('admin')] },
    async (request) => {
      const groupId = Number((request.params as { id: string }).id);
      const existing = requireGroup(groupId);
      const body = parseBody(
        z.object({
          name: z.string().trim().min(2).max(60).optional(),
          code: z
            .string()
            .trim()
            .regex(/^[A-Za-z0-9_-]{3,32}$/, 'Код: 3–32 символа')
            .optional(),
          avatar: avatarSchema,
        }),
        request.body,
      );

      try {
        if (body.name !== undefined) {
          run('UPDATE groups SET name = ? WHERE id = ?', body.name, groupId);
        }
        if (body.code !== undefined) {
          run('UPDATE groups SET code = ? WHERE id = ?', body.code.toUpperCase(), groupId);
        }
        if (body.avatar !== undefined) {
          run('UPDATE groups SET avatar = ? WHERE id = ?', normalizeAvatar(body.avatar), groupId);
        }
      } catch (error) {
        if (isUniqueError(error)) {
          throw new ApiError(409, 'Группа с таким названием или кодом уже существует');
        }
        throw error;
      }

      const row = get<GroupRow>(`${GROUP_SELECT} WHERE g.id = ?`, groupId);
      writeAudit(request, {
        action: 'group.update',
        entityType: 'group',
        entityId: groupId,
        groupId,
        groupName: row?.name ?? existing.name,
        summary: `Изменена группа «${existing.name}»`,
        details: {
          name: body.name ?? null,
          code: body.code ?? null,
          avatarChanged: body.avatar !== undefined,
        },
      });
      return { group: row ? mapGroup(row) : null };
    },
  );

  app.delete(
    '/groups/:id',
    { preHandler: [requireAuth, requireRole('admin')] },
    async (request) => {
      const groupId = Number((request.params as { id: string }).id);
      const group = requireGroup(groupId);
      run('DELETE FROM groups WHERE id = ?', groupId);
      writeAudit(request, {
        action: 'group.delete',
        entityType: 'group',
        entityId: groupId,
        groupId,
        groupName: group.name,
        summary: `Удалена группа «${group.name}» (код ${group.code})`,
        details: { name: group.name, code: group.code },
      });
      return { ok: true };
    },
  );

  app.get('/subjects', { preHandler: requireAuth }, async (request) => {
    const user = request.user!;
    const query = request.query as { groupId?: string };
    const groupId = query.groupId ? Number(query.groupId) : null;

    const where: string[] = [];
    const params: unknown[] = [];

    if (user.role === 'teacher') {
      where.push('s.teacher_id = ?');
      params.push(user.id);
    } else if (user.role === 'student' || user.role === 'headman') {
      if (!user.groupId) return { subjects: [] };
      where.push('s.group_id = ?');
      params.push(user.groupId);
    }

    if (groupId && Number.isFinite(groupId)) {
      where.push('s.group_id = ?');
      params.push(groupId);
    }

    const rows = all<{
      id: number;
      name: string;
      group_id: number;
      group_name: string;
      teacher_id: number | null;
      teacher_name: string | null;
      lessons_count: number;
    }>(
      `SELECT s.id, s.name, s.group_id, g.name AS group_name,
              s.teacher_id, u.full_name AS teacher_name,
              (SELECT COUNT(*) FROM lessons l WHERE l.subject_id = s.id) AS lessons_count
       FROM subjects s
       JOIN groups g ON g.id = s.group_id
       LEFT JOIN users u ON u.id = s.teacher_id
       ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
       ORDER BY g.name COLLATE NOCASE, s.name COLLATE NOCASE`,
      ...params,
    );

    return {
      subjects: rows.map((row) => ({
        id: row.id,
        name: row.name,
        groupId: row.group_id,
        groupName: row.group_name,
        teacherId: row.teacher_id,
        teacherName: row.teacher_name,
        lessonsCount: Number(row.lessons_count),
      })),
    };
  });

  app.post(
    '/subjects',
    { preHandler: [requireAuth, requireRole('admin')] },
    async (request, reply) => {
      const body = parseBody(
        z.object({
          name: z.string().trim().min(2, 'Название предмета слишком короткое').max(80),
          groupId: z.number().int().positive('Выберите группу'),
          teacherId: z.number().int().positive().nullable().optional(),
        }),
        request.body,
      );
      requireGroup(body.groupId);
      if (body.teacherId) {
        const teacher = get<{ role: Role }>('SELECT role FROM users WHERE id = ?', body.teacherId);
        if (!teacher || teacher.role !== 'teacher') {
          throw new ApiError(400, 'Выбранный пользователь не является преподавателем');
        }
      }
      try {
        const created = run(
          'INSERT INTO subjects (name, group_id, teacher_id) VALUES (?, ?, ?)',
          body.name,
          body.groupId,
          body.teacherId ?? null,
        );
        const group = requireGroup(body.groupId);
        writeAudit(request, {
          action: 'subject.create',
          entityType: 'subject',
          entityId: created.lastInsertRowid,
          groupId: body.groupId,
          groupName: group.name,
          summary: `Добавлен предмет «${body.name}» в группу «${group.name}»`,
          details: { name: body.name, teacherId: body.teacherId ?? null },
        });
        reply.code(201);
        return { id: created.lastInsertRowid };
      } catch (error) {
        if (isUniqueError(error)) {
          throw new ApiError(409, 'Такой предмет уже есть в этой группе');
        }
        throw error;
      }
    },
  );

  app.patch(
    '/subjects/:id',
    { preHandler: [requireAuth, requireRole('admin')] },
    async (request) => {
      const subjectId = Number((request.params as { id: string }).id);
      const subject = get<{ id: number; name: string; group_id: number }>(
        'SELECT id, name, group_id FROM subjects WHERE id = ?',
        subjectId,
      );
      if (!subject) throw new ApiError(404, 'Предмет не найден');

      const body = parseBody(
        z.object({
          name: z.string().trim().min(2).max(80).optional(),
          teacherId: z.number().int().positive().nullable().optional(),
        }),
        request.body,
      );

      if (body.teacherId !== undefined && body.teacherId !== null) {
        const teacher = get<{ role: Role }>('SELECT role FROM users WHERE id = ?', body.teacherId);
        if (!teacher || teacher.role !== 'teacher') {
          throw new ApiError(400, 'Выбранный пользователь не является преподавателем');
        }
      }

      try {
        if (body.name !== undefined) {
          run('UPDATE subjects SET name = ? WHERE id = ?', body.name, subjectId);
        }
        if (body.teacherId !== undefined) {
          run('UPDATE subjects SET teacher_id = ? WHERE id = ?', body.teacherId, subjectId);
        }
      } catch (error) {
        if (isUniqueError(error)) {
          throw new ApiError(409, 'Такой предмет уже есть в этой группе');
        }
        throw error;
      }
      const group = requireGroup(subject.group_id);
      writeAudit(request, {
        action: 'subject.update',
        entityType: 'subject',
        entityId: subjectId,
        groupId: subject.group_id,
        groupName: group.name,
        summary: `Изменён предмет «${body.name ?? subject.name}» (${group.name})`,
        details: { name: body.name ?? null, teacherId: body.teacherId ?? null },
      });
      return { ok: true };
    },
  );

  app.delete(
    '/subjects/:id',
    { preHandler: [requireAuth, requireRole('admin')] },
    async (request) => {
      const subjectId = Number((request.params as { id: string }).id);
      const subject = get<{ id: number; name: string; group_id: number }>(
        'SELECT id, name, group_id FROM subjects WHERE id = ?',
        subjectId,
      );
      if (!subject) throw new ApiError(404, 'Предмет не найден');
      const group = requireGroup(subject.group_id);
      run('DELETE FROM subjects WHERE id = ?', subjectId);
      writeAudit(request, {
        action: 'subject.delete',
        entityType: 'subject',
        entityId: subjectId,
        groupId: subject.group_id,
        groupName: group.name,
        summary: `Удалён предмет «${subject.name}» (${group.name})`,
        details: { name: subject.name },
      });
      return { ok: true };
    },
  );
};

const usersRoutes: FastifyPluginAsync = async (app) => {
  app.get(
    '/users',
    { preHandler: [requireAuth, requireRole('admin')] },
    async (request) => {
      const query = request.query as { role?: string; groupId?: string; q?: string };
      const where: string[] = [];
      const params: unknown[] = [];

      if (query.role && ['admin', 'teacher', 'student', 'headman'].includes(query.role)) {
        where.push('u.role = ?');
        params.push(query.role);
      }
      if (query.groupId && Number.isFinite(Number(query.groupId))) {
        where.push('u.group_id = ?');
        params.push(Number(query.groupId));
      }
      if (query.q && query.q.trim()) {
        where.push('(u.full_name LIKE ? OR u.login LIKE ?)');
        const like = `%${query.q.trim()}%`;
        params.push(like, like);
      }

      const rows = all(
        `${USER_SELECT}
         ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
         ORDER BY u.role, u.full_name COLLATE NOCASE`,
        ...params,
      );
      return { users: rows.map((row) => mapUser(row as never)) };
    },
  );

  app.post(
    '/users',
    { preHandler: [requireAuth, requireRole('admin')] },
    async (request, reply) => {
      const body = parseBody(
        z.object({
          fullName: z.string().trim().min(2, 'Укажите имя').max(120),
          login: z
            .string()
            .trim()
            .regex(/^[a-zA-Z0-9._-]{3,32}$/, 'Логин: 3–32 символа (латиница, цифры, . _ -)'),
          password: z.string().min(6, 'Пароль должен быть не короче 6 символов').max(128),
          role: z.enum(['admin', 'teacher', 'student', 'headman']),
          groupId: z.number().int().positive().nullable().optional(),
          avatar: avatarSchema,
        }),
        request.body,
      );

      const needsGroup = body.role === 'student' || body.role === 'headman';
      if (needsGroup) {
        if (!body.groupId) {
          throw new ApiError(
            400,
            body.role === 'headman' ? 'Для старосты выберите группу' : 'Для обучающегося выберите группу',
          );
        }
        requireGroup(body.groupId);
      }

      const passwordHash = bcrypt.hashSync(body.password, 10);
      try {
        const created = run(
          `INSERT INTO users (login, password_hash, role, full_name, group_id, avatar)
           VALUES (?, ?, ?, ?, ?, ?)`,
          body.login,
          passwordHash,
          body.role,
          body.fullName,
          needsGroup ? body.groupId : null,
          normalizeAvatar(body.avatar),
        );
        const row = get(
          `${USER_SELECT} WHERE u.id = ?`,
          created.lastInsertRowid,
        );
        const groupName = body.groupId
          ? get<{ name: string }>('SELECT name FROM groups WHERE id = ?', body.groupId)?.name
          : null;
        writeAudit(request, {
          action: 'user.create',
          entityType: 'user',
          entityId: created.lastInsertRowid,
          groupId: needsGroup ? body.groupId ?? null : null,
          groupName,
          summary: `Создан аккаунт @${body.login} (${body.fullName}, ${roleTitle(body.role)})`,
          details: { role: body.role, groupId: body.groupId ?? null },
        });
        reply.code(201);
        return { user: row ? mapUser(row as never) : null };
      } catch (error) {
        if (isUniqueError(error)) {
          throw new ApiError(409, 'Такой логин уже занят');
        }
        throw error;
      }
    },
  );

  app.patch(
    '/users/:id',
    { preHandler: [requireAuth, requireRole('admin')] },
    async (request) => {
      const userId = Number((request.params as { id: string }).id);
      const target = get<{
        id: number;
        login: string;
        full_name: string;
        role: Role;
        group_id: number | null;
        is_active: number;
      }>(
        'SELECT id, login, full_name, role, group_id, is_active FROM users WHERE id = ?',
        userId,
      );
      if (!target) throw new ApiError(404, 'Пользователь не найден');

      const body = parseBody(
        z.object({
          fullName: z.string().trim().min(2).max(120).optional(),
          login: z
            .string()
            .trim()
            .regex(/^[a-zA-Z0-9._-]{3,32}$/, 'Логин: 3–32 символа')
            .optional(),
          password: z.string().min(6, 'Пароль должен быть не короче 6 символов').max(128).optional(),
          role: z.enum(['admin', 'teacher', 'student', 'headman']).optional(),
          groupId: z.number().int().positive().nullable().optional(),
          avatar: avatarSchema,
          isActive: z.boolean().optional(),
        }),
        request.body,
      );

      const actor = request.user!;
      if (target.id === actor.id && body.isActive === false) {
        throw new ApiError(400, 'Нельзя отключить собственный аккаунт');
      }

      const nextRole = body.role ?? target.role;
      const roleChanged = body.role !== undefined && body.role !== target.role;

      if (target.role === 'admin' && roleChanged) {
        const admins = get<{ c: number }>(
          "SELECT COUNT(*) AS c FROM users WHERE role = 'admin' AND is_active = 1",
        );
        if ((admins?.c ?? 0) <= 1) {
          throw new ApiError(400, 'Нельзя снять роль с последнего администратора');
        }
      }

      if (body.groupId !== undefined && body.groupId !== null) requireGroup(body.groupId);

      const keepGroup = nextRole === 'student' || nextRole === 'headman';
      const nextGroupId = body.groupId !== undefined ? body.groupId : target.group_id;
      if (keepGroup && nextGroupId === null) {
        throw new ApiError(
          400,
          nextRole === 'headman' ? 'Для старосты выберите группу' : 'У обучающегося должна быть группа',
        );
      }

      const changes: string[] = [];
      if (body.fullName !== undefined && body.fullName !== target.full_name) changes.push('имя');
      if (body.login !== undefined && body.login !== target.login) changes.push('логин');
      if (body.password !== undefined) changes.push('пароль');
      if (roleChanged) changes.push(`роль (${roleTitle(nextRole)})`);
      if (body.groupId !== undefined) changes.push('группа');
      if (body.avatar !== undefined) changes.push('аватарка');
      if (body.isActive !== undefined && body.isActive !== (target.is_active === 1)) {
        changes.push(body.isActive ? 'включён' : 'отключён');
      }

      try {
        if (body.fullName !== undefined) {
          run('UPDATE users SET full_name = ? WHERE id = ?', body.fullName, userId);
        }
        if (body.login !== undefined) {
          run('UPDATE users SET login = ? WHERE id = ?', body.login, userId);
        }
        if (body.password !== undefined) {
          run(
            'UPDATE users SET password_hash = ? WHERE id = ?',
            bcrypt.hashSync(body.password, 10),
            userId,
          );
        }
        if (roleChanged && body.role !== undefined) {
          run('UPDATE users SET role = ? WHERE id = ?', body.role, userId);
        }
        if (body.groupId !== undefined || roleChanged) {
          run(
            'UPDATE users SET group_id = ? WHERE id = ?',
            keepGroup ? nextGroupId : null,
            userId,
          );
        }
        if (body.avatar !== undefined) {
          run('UPDATE users SET avatar = ? WHERE id = ?', normalizeAvatar(body.avatar), userId);
        }
        if (body.isActive !== undefined) {
          run('UPDATE users SET is_active = ? WHERE id = ?', body.isActive ? 1 : 0, userId);
        }
      } catch (error) {
        if (isUniqueError(error)) {
          throw new ApiError(409, 'Такой логин уже занят');
        }
        throw error;
      }

      const row = get(`${USER_SELECT} WHERE u.id = ?`, userId);
      const groupName = keepGroup && nextGroupId
        ? get<{ name: string }>('SELECT name FROM groups WHERE id = ?', nextGroupId)?.name ?? null
        : null;
      writeAudit(request, {
        action: body.password !== undefined && changes.length === 1 ? 'user.password' : 'user.update',
        entityType: 'user',
        entityId: userId,
        groupId: keepGroup ? nextGroupId : null,
        groupName,
        summary:
          changes.length === 0
            ? `Просмотр аккаунта @${target.login}`
            : `Изменён аккаунт @${target.login}: ${changes.join(', ')}`,
        details: {
          targetLogin: target.login,
          role: roleChanged ? nextRole : null,
          groupId: body.groupId !== undefined ? nextGroupId : null,
          avatarChanged: body.avatar !== undefined,
          passwordChanged: body.password !== undefined,
          isActive: body.isActive ?? null,
        },
      });
      return { user: row ? mapUser(row as never) : null };
    },
  );

  app.delete(
    '/users/:id',
    { preHandler: [requireAuth, requireRole('admin')] },
    async (request) => {
      const userId = Number((request.params as { id: string }).id);
      const actor = request.user!;
      if (userId === actor.id) throw new ApiError(400, 'Нельзя удалить собственный аккаунт');

      const target = get<{
        id: number;
        login: string;
        full_name: string;
        role: Role;
        group_id: number | null;
      }>(
        'SELECT id, login, full_name, role, group_id FROM users WHERE id = ?',
        userId,
      );
      if (!target) throw new ApiError(404, 'Пользователь не найден');

      if (target.role === 'admin') {
        const admins = get<{ c: number }>(
          "SELECT COUNT(*) AS c FROM users WHERE role = 'admin' AND is_active = 1",
        );
        if ((admins?.c ?? 0) <= 1) {
          throw new ApiError(400, 'Нельзя удалить последнего администратора');
        }
      }

      const groupName = target.group_id
        ? get<{ name: string }>('SELECT name FROM groups WHERE id = ?', target.group_id)?.name ?? null
        : null;
      run('DELETE FROM users WHERE id = ?', userId);
      writeAudit(request, {
        action: 'user.delete',
        entityType: 'user',
        entityId: userId,
        groupId: target.group_id,
        groupName,
        summary: `Удалён аккаунт @${target.login} (${target.full_name}, ${roleTitle(target.role)})`,
        details: { login: target.login, role: target.role },
      });
      return { ok: true };
    },
  );
};

export { groupRoutes, usersRoutes };
