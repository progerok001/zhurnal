import type { FastifyPluginAsync } from 'fastify';
import { requireAuth, requireRole } from '../auth.js';
import { all, get } from '../db.js';

interface AuditRow {
  id: number;
  user_id: number | null;
  user_login: string;
  user_name: string;
  user_role: string;
  action: string;
  entity_type: string;
  entity_id: number | null;
  group_id: number | null;
  group_name: string | null;
  summary: string;
  details: string | null;
  created_at: string;
}

export function mapAudit(row: AuditRow) {
  let details: unknown = null;
  if (row.details) {
    try {
      details = JSON.parse(row.details);
    } catch {
      details = row.details;
    }
  }
  return {
    id: row.id,
    userId: row.user_id,
    userLogin: row.user_login,
    userName: row.user_name,
    userRole: row.user_role,
    action: row.action,
    entityType: row.entity_type,
    entityId: row.entity_id,
    groupId: row.group_id,
    groupName: row.group_name,
    summary: row.summary,
    details,
    createdAt: row.created_at,
  };
}

const auditRoutes: FastifyPluginAsync = async (app) => {
  app.get(
    '/audit',
    { preHandler: [requireAuth, requireRole('admin')] },
    async (request) => {
      const query = request.query as {
        limit?: string;
        offset?: string;
        entityType?: string;
        groupId?: string;
        userId?: string;
        q?: string;
      };

      const limit = Math.min(Math.max(Number(query.limit) || 50, 1), 200);
      const offset = Math.max(Number(query.offset) || 0, 0);

      const where: string[] = [];
      const params: unknown[] = [];

      if (query.entityType && query.entityType !== 'all') {
        where.push('entity_type = ?');
        params.push(query.entityType);
      }
      if (query.groupId && Number.isFinite(Number(query.groupId))) {
        where.push('group_id = ?');
        params.push(Number(query.groupId));
      }
      if (query.userId && Number.isFinite(Number(query.userId))) {
        where.push('user_id = ?');
        params.push(Number(query.userId));
      }
      if (query.q?.trim()) {
        where.push('(summary LIKE ? OR user_name LIKE ? OR user_login LIKE ?)');
        const like = `%${query.q.trim()}%`;
        params.push(like, like, like);
      }

      const clause = where.length ? `WHERE ${where.join(' AND ')}` : '';
      const total = get<{ c: number }>(
        `SELECT COUNT(*) AS c FROM audit_log ${clause}`,
        ...params,
      );
      const rows = all<AuditRow>(
        `SELECT * FROM audit_log ${clause}
         ORDER BY created_at DESC, id DESC
         LIMIT ? OFFSET ?`,
        ...params,
        limit,
        offset,
      );

      return {
        entries: rows.map(mapAudit),
        total: total?.c ?? 0,
        limit,
        offset,
        hasMore: offset + rows.length < (total?.c ?? 0),
      };
    },
  );
};

export default auditRoutes;
