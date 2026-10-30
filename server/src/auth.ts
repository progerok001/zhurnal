import jwt, { type JwtPayload } from 'jsonwebtoken';
import type { FastifyReply, FastifyRequest } from 'fastify';
import { config } from './config.js';
import { get } from './db.js';

export type Role = 'admin' | 'teacher' | 'student' | 'headman';

export const ROLES: Role[] = ['admin', 'teacher', 'student', 'headman'];

export interface AuthUser {
  id: number;
  login: string;
  role: Role;
  fullName: string;
  groupId: number | null;
}

export interface UserRow {
  id: number;
  login: string;
  password_hash: string;
  role: Role;
  full_name: string;
  group_id: number | null;
  avatar: string | null;
  is_active: number;
  created_at: string;
}

declare module 'fastify' {
  interface FastifyRequest {
    user: AuthUser | null;
  }
}

export function userFromRow(row: UserRow): AuthUser {
  return {
    id: row.id,
    login: row.login,
    role: row.role,
    fullName: row.full_name,
    groupId: row.group_id,
  };
}

export function signToken(user: AuthUser): string {
  return jwt.sign({ sub: String(user.id), role: user.role }, config.jwtSecret, {
    expiresIn: '120d',
  });
}

/** SQLite хранит время в UTC без суффикса Z. */
function sqliteUtcMillis(value: string): number {
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const withZone = /(?:Z|[+-]\d{2}:?\d{2})$/.test(normalized) ? normalized : `${normalized}Z`;
  return Date.parse(withZone);
}

export async function requireAuth(
  request: FastifyRequest,
  reply: FastifyReply,
): Promise<void | FastifyReply> {
  const header = request.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    return reply.code(401).send({ error: 'Требуется авторизация' });
  }

  try {
    const payload = jwt.verify(header.slice(7).trim(), config.jwtSecret) as JwtPayload;
    const userId = Number(payload.sub);
    if (!Number.isFinite(userId)) {
      return reply.code(401).send({ error: 'Некорректный токен' });
    }
    const row = get<UserRow>(
      'SELECT id, login, role, full_name, group_id, avatar, is_active, created_at FROM users WHERE id = ?',
      userId,
    );
    if (!row || !row.is_active) {
      return reply.code(401).send({ error: 'Аккаунт не найден или отключён' });
    }
    // Если база пересоздана и id достался новому человеку, старый токен не должен подходить.
    const issuedAt = typeof payload.iat === 'number' ? payload.iat * 1000 : 0;
    const createdAt = sqliteUtcMillis(row.created_at);
    if (!issuedAt || (Number.isFinite(createdAt) && issuedAt + 2000 < createdAt)) {
      return reply.code(401).send({ error: 'Сессия недействительна, войдите заново' });
    }
    request.user = userFromRow(row);
  } catch {
    return reply.code(401).send({ error: 'Сессия истекла, войдите заново' });
  }
}

export function requireRole(...roles: Role[]) {
  return async (
    request: FastifyRequest,
    reply: FastifyReply,
  ): Promise<void | FastifyReply> => {
    if (!request.user || !roles.includes(request.user.role)) {
      return reply.code(403).send({ error: 'Недостаточно прав' });
    }
  };
}

export function requireAdmin(
  request: FastifyRequest,
  reply: FastifyReply,
): void | FastifyReply | Promise<void | FastifyReply> {
  return requireRole('admin')(request, reply);
}

/** Староста может редактировать журнал только закреплённой за ним группы. */
export function isHeadmanOf(user: AuthUser, groupId: number | null | undefined): boolean {
  return user.role === 'headman' && groupId != null && user.groupId === groupId;
}
