import type { FastifyPluginAsync } from 'fastify';
import { requireAuth } from '../auth.js';
import { getSubstitutions } from '../substitutions.js';

const substitutionsRoutes: FastifyPluginAsync = async (app) => {
  app.get('/substitutions', { preHandler: requireAuth }, async (request) => {
    const query = request.query as { refresh?: string };
    const force = query.refresh === '1' || query.refresh === 'true';
    return getSubstitutions(force);
  });
};

export default substitutionsRoutes;
