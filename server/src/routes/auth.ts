import type { FastifyPluginAsync, FastifyRequest } from 'fastify';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { get, run } from '../db.js';
import { signToken, requireAuth, userFromRow, type UserRow } from '../auth.js';
import { ApiError, parseBody } from '../util.js';
import { MAX_AVATAR_LENGTH, isImageAvatar, normalizeAvatar } from '../avatar.js';
import { mapUser, USER_SELECT } from '../mappers.js';
import { writeAudit } from '../audit.js';

const loginSchema = z.object({
  login: z.string().trim().min(1, 'Введите логин').max(64, 'Слишком длинный логин'),
  password: z.string().min(1, 'Введите пароль').max(128, 'Слишком длинный пароль'),
});

const registerSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(2, 'Укажите фамилию и имя')
    .max(120, 'Слишком длинное имя'),
  login: z
    .string()
    .trim()
    .regex(/^[a-zA-Z0-9._-]{3,32}$/, 'Логин: 3–32 символа (латиница, цифры, . _ -)'),
  password: z.string().min(6, 'Пароль должен быть не короче 6 символов').max(128),
  groupCode: z.string().trim().min(1, 'Введите код группы').max(64),
});

const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, 'Введите текущий пароль'),
  newPassword: z.string().min(6, 'Новый пароль должен быть не короче 6 символов').max(128),
});

interface Attempt {
  count: number;
  blockedUntil: number;
}

const attempts = new Map<string, Attempt>();
const MAX_ATTEMPTS = 12;
const WINDOW_MS = 10 * 60 * 1000;

function clientKey(request: FastifyRequest, login: string): string {
  return `${request.ip || 'unknown'}|${login.trim().toLowerCase()}`;
}

function guardRateLimit(key: string): void {
  const entry = attempts.get(key);
  if (!entry) return;
  if (entry.blockedUntil > Date.now() && entry.count >= MAX_ATTEMPTS) {
    throw new ApiError(429, 'Слишком много попыток входа. Попробуйте через 10 минут.');
  }
}

function registerFailure(key: string): void {
  const now = Date.now();
  const entry = attempts.get(key);
  if (!entry || entry.blockedUntil < now) {
    attempts.set(key, { count: 1, blockedUntil: now + WINDOW_MS });
    return;
  }
  entry.count += 1;
  entry.blockedUntil = now + WINDOW_MS;
}

function clearFailures(key: string): void {
  attempts.delete(key);
}

function findUserRow(id: number) {
  return get<UserRow & { group_name: string | null }>(
    `${USER_SELECT} WHERE u.id = ?`,
    id,
  );
}

function authPayload(row: UserRow & { group_name: string | null }) {
  const user = userFromRow(row);
  return {
    token: signToken(user),
    user: mapUser(row),
  };
}

const authRoutes: FastifyPluginAsync = async (app) => {
  app.post('/register', async (request, reply) => {
    const body = parseBody(registerSchema, request.body);

    const group = get<{ id: number; name: string }>(
      'SELECT id, name FROM groups WHERE code = ?',
      body.groupCode,
    );
    if (!group) {
      throw new ApiError(404, 'Группа с таким кодом не найдена. Проверьте код у администратора.');
    }

    const existing = get<{ id: number }>('SELECT id FROM users WHERE login = ?', body.login);
    if (existing) {
      throw new ApiError(409, 'Такой логин уже занят');
    }

    const passwordHash = bcrypt.hashSync(body.password, 10);
    const created = run(
      `INSERT INTO users (login, password_hash, role, full_name, group_id)
       VALUES (?, ?, 'student', ?, ?)`,
      body.login,
      passwordHash,
      body.fullName,
      group.id,
    );

    const row = findUserRow(created.lastInsertRowid);
    if (!row) throw new ApiError(500, 'Не удалось создать аккаунт');

    writeAudit(
      request,
      {
        action: 'user.register',
        entityType: 'user',
        entityId: created.lastInsertRowid,
        groupId: group.id,
        groupName: group.name,
        summary: `Зарегистрирован новый обучающийся @${body.login} (${body.fullName}) в группе «${group.name}»`,
        details: { login: body.login, groupCode: body.groupCode },
      },
      {
        id: created.lastInsertRowid,
        login: body.login,
        fullName: body.fullName,
        role: 'student',
      },
    );

    reply.code(201);
    return authPayload(row);
  });

  app.post('/login', async (request) => {
    const body = parseBody(loginSchema, request.body);
    const key = clientKey(request, body.login);
    guardRateLimit(key);

    const row = get<UserRow & { group_name: string | null }>(
      `${USER_SELECT} WHERE u.login = ?`,
      body.login,
    );

    if (!row || !bcrypt.compareSync(body.password, row.password_hash)) {
      registerFailure(key);
      throw new ApiError(401, 'Неверный логин или пароль');
    }
    if (!row.is_active) {
      throw new ApiError(403, 'Аккаунт отключён. Обратитесь к администратору.');
    }

    clearFailures(key);
    return authPayload(row);
  });

  app.post('/check-code', async (request) => {
    const body = parseBody(
      z.object({ code: z.string().trim().min(1, 'Введите код группы').max(64) }),
      request.body,
    );
    const group = get<{ id: number; name: string; avatar: string | null }>(
      'SELECT id, name, avatar FROM groups WHERE code = ?',
      body.code,
    );
    if (!group) throw new ApiError(404, 'Группа с таким кодом не найдена');
    return { group: { id: group.id, name: group.name, avatar: group.avatar ?? null } };
  });

  app.get('/me', { preHandler: requireAuth }, async (request) => {
    const row = findUserRow(request.user!.id);
    if (!row) throw new ApiError(404, 'Пользователь не найден');
    return { user: mapUser(row) };
  });

  app.post('/change-password', { preHandler: requireAuth }, async (request) => {
    const body = parseBody(changePasswordSchema, request.body);
    const row = get<UserRow>('SELECT * FROM users WHERE id = ?', request.user!.id);
    if (!row) throw new ApiError(404, 'Пользователь не найден');
    if (!bcrypt.compareSync(body.currentPassword, row.password_hash)) {
      throw new ApiError(400, 'Текущий пароль указан неверно');
    }
    if (body.currentPassword === body.newPassword) {
      throw new ApiError(400, 'Новый пароль должен отличаться от текущего');
    }
    const hash = bcrypt.hashSync(body.newPassword, 10);
    run('UPDATE users SET password_hash = ? WHERE id = ?', hash, row.id);
    writeAudit(request, {
      action: 'user.password',
      entityType: 'user',
      entityId: row.id,
      groupId: request.user!.groupId,
      summary: 'Смена собственного пароля',
      details: null,
    });
    return { ok: true };
  });

  app.patch('/me/avatar', { preHandler: requireAuth }, async (request) => {
    const body = parseBody(
      z.object({
        avatar: z.string().trim().max(MAX_AVATAR_LENGTH, 'Слишком большая аватарка').nullable().optional(),
      }),
      request.body,
    );
    const avatar = normalizeAvatar(body.avatar);
    run('UPDATE users SET avatar = ? WHERE id = ?', avatar, request.user!.id);

    const row = findUserRow(request.user!.id);
    if (!row) throw new ApiError(404, 'Пользователь не найден');

    writeAudit(request, {
      action: 'user.avatar',
      entityType: 'user',
      entityId: row.id,
      groupId: row.group_id,
      groupName: row.group_name,
      summary: !avatar
        ? 'Удалена аватарка профиля'
        : isImageAvatar(avatar)
          ? 'Обновлена аватарка профиля (фото с устройства)'
          : `Обновлена аватарка профиля ${avatar}`,
      details: { image: isImageAvatar(avatar) },
    });

    return { user: mapUser(row) };
  });
};

export default authRoutes;
