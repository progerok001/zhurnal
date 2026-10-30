import fs from 'node:fs';
import path from 'node:path';
import { createReadStream } from 'node:fs';
import type { FastifyPluginAsync } from 'fastify';
import { config } from '../config.js';

const publicDir = path.join(config.root, 'public');
const metaPath = path.join(publicDir, 'update.json');
const apkPath = path.join(publicDir, 'zhurnal-debug.apk');

interface UpdateMeta {
  versionCode: number;
  versionName: string;
  apkUrl?: string;
  notes?: string;
  mandatory?: boolean;
  publishedAt?: string;
  size?: number;
  sha256?: string;
}

/**
 * Информация об обновлении и сам APK лежат рядом с сервером в папке public.
 * Файлы туда кладёт tools/publish-update.mjs после сборки приложения.
 */
const appUpdateRoutes: FastifyPluginAsync = async (app) => {
  app.get('/app/update', async (_request, reply) => {
    try {
      const raw = await fs.promises.readFile(metaPath, 'utf8');
      const meta = JSON.parse(raw) as UpdateMeta;
      if (!meta.versionCode || !meta.versionName) {
        return reply.code(503).send({ error: 'Некорректные данные об обновлении' });
      }
      return {
        ...meta,
        apkUrl: meta.apkUrl?.trim() || '/api/app/download',
        mandatory: meta.mandatory !== false,
      };
    } catch {
      return reply.code(404).send({ error: 'Обновления ещё не опубликованы' });
    }
  });

  app.get('/app/download', async (_request, reply) => {
    try {
      const stat = await fs.promises.stat(apkPath);
      if (!stat.isFile() || stat.size === 0) {
        return reply.code(404).send({ error: 'Файл обновления не найден' });
      }
      reply.header('Content-Type', 'application/vnd.android.package-archive');
      reply.header('Content-Length', String(stat.size));
      reply.header('Content-Disposition', 'attachment; filename="zhurnal-debug.apk"');
      reply.header('Cache-Control', 'no-store');
      return reply.send(createReadStream(apkPath));
    } catch {
      return reply.code(404).send({ error: 'Файл обновления не найден' });
    }
  });
};

export default appUpdateRoutes;
