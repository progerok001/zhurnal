import Fastify from 'fastify';
import cors from '@fastify/cors';
import os from 'node:os';
import { config } from './config.js';
import { db, get } from './db.js';
import authRoutes from './routes/auth.js';
import { groupRoutes, usersRoutes } from './routes/catalog.js';
import journalRoutes from './routes/journal.js';
import auditRoutes from './routes/audit.js';
import substitutionsRoutes from './routes/substitutions.js';
import scheduleRoutes from './routes/schedule.js';
import appUpdateRoutes from './routes/appUpdate.js';

const app = Fastify({
  logger: {
    level: 'info',
    transport: undefined,
  },
});

app.decorateRequest('user', null);

// Allow empty bodies for DELETE/POST requests that declare JSON content-type.
app.addContentTypeParser(
  'application/json',
  { parseAs: 'string' },
  (_request, body, done) => {
    const raw = typeof body === 'string' ? body.trim() : '';
    if (!raw) {
      done(null, undefined);
      return;
    }
    try {
      done(null, JSON.parse(raw));
    } catch (error) {
      const parseError = error as Error & { statusCode?: number };
      parseError.statusCode = 400;
      done(parseError, undefined);
    }
  },
);

app.setErrorHandler((error: Error & { statusCode?: number }, request, reply) => {
  const statusCode =
    typeof error.statusCode === 'number' && error.statusCode >= 400
      ? error.statusCode
      : 500;

  if (statusCode >= 500) {
    request.log.error(error);
  }

  reply.code(statusCode).send({
    error:
      statusCode >= 500
        ? 'Внутренняя ошибка сервера'
        : (error.message || 'Ошибка запроса'),
  });
});

await app.register(cors, {
  origin: true,
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
});

app.get('/', async () => ({
  name: 'Журнал API',
  version: config.version,
  status: 'ok',
  health: '/api/health',
}));

app.get('/api/health', async () => {
  const groups = get<{ c: number }>('SELECT COUNT(*) AS c FROM groups');
  const users = get<{ c: number }>('SELECT COUNT(*) AS c FROM users');
  return {
    ok: true,
    name: 'Журнал API',
    version: config.version,
    time: new Date().toISOString(),
    counts: {
      groups: groups?.c ?? 0,
      users: users?.c ?? 0,
    },
  };
});

await app.register(authRoutes, { prefix: '/api/auth' });
await app.register(groupRoutes, { prefix: '/api' });
await app.register(usersRoutes, { prefix: '/api' });
await app.register(journalRoutes, { prefix: '/api' });
await app.register(auditRoutes, { prefix: '/api' });
await app.register(substitutionsRoutes, { prefix: '/api' });
await app.register(scheduleRoutes, { prefix: '/api' });
await app.register(appUpdateRoutes, { prefix: '/api' });

app.addHook('onClose', async () => {
  db.close();
});

function localAddresses(): string[] {
  const addresses: string[] = [];
  for (const entries of Object.values(os.networkInterfaces())) {
    for (const entry of entries ?? []) {
      if (entry.family === 'IPv4' && !entry.internal) {
        addresses.push(`http://${entry.address}:${config.port}`);
      }
    }
  }
  return addresses;
}

try {
  await app.listen({ port: config.port, host: config.host });
  console.log('');
  console.log('  ЖУРНАЛ · сервер запущен');
  console.log(`  Локально:  http://localhost:${config.port}`);
  for (const address of localAddresses()) {
    console.log(`  В сети:    ${address}`);
  }
  console.log('  Проверка:  /api/health');
  console.log('');
} catch (error) {
  app.log.error(error);
  process.exit(1);
}
