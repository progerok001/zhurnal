import type { FastifyRequest } from 'fastify';
import { run } from './db.js';

export interface AuditActor {
  id: number | null;
  login: string;
  fullName: string;
  role: string;
}

export interface AuditEntry {
  action: string;
  entityType: string;
  entityId?: number | null;
  groupId?: number | null;
  groupName?: string | null;
  summary: string;
  details?: unknown;
}

function actorFromRequest(request: FastifyRequest): AuditActor | null {
  const user = request.user;
  if (!user) return null;
  return {
    id: user.id,
    login: user.login,
    fullName: user.fullName,
    role: user.role,
  };
}

/**
 * Пишет действие в журнал аудита. Ошибки логирования не должны ломать
 * основную операцию, поэтому они только пишутся в лог сервера.
 */
export function writeAudit(
  request: FastifyRequest,
  entry: AuditEntry,
  actor?: AuditActor | null,
): void {
  const who = actor ?? actorFromRequest(request);
  if (!who) return;

  try {
    run(
      `INSERT INTO audit_log
        (user_id, user_login, user_name, user_role, action, entity_type, entity_id,
         group_id, group_name, summary, details, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))`,
      who.id,
      who.login,
      who.fullName,
      who.role,
      entry.action,
      entry.entityType,
      entry.entityId ?? null,
      entry.groupId ?? null,
      entry.groupName ?? null,
      entry.summary,
      entry.details === undefined ? null : JSON.stringify(entry.details),
    );
  } catch (error) {
    request.log.error({ err: error }, 'Не удалось записать действие в журнал аудита');
  }
}

export function actorSnapshot(actor: AuditActor): AuditActor {
  return { ...actor };
}
