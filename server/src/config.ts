import 'dotenv/config';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const root = process.cwd();
const dataDir = process.env.DATA_DIR
  ? path.resolve(process.env.DATA_DIR)
  : path.join(root, 'data');

fs.mkdirSync(dataDir, { recursive: true });

function loadJwtSecret(): string {
  if (process.env.JWT_SECRET && process.env.JWT_SECRET.length >= 16) {
    return process.env.JWT_SECRET;
  }
  const file = path.join(dataDir, 'jwt-secret.txt');
  if (fs.existsSync(file)) {
    const saved = fs.readFileSync(file, 'utf8').trim();
    if (saved.length >= 16) return saved;
  }
  const secret = crypto.randomBytes(48).toString('hex');
  fs.writeFileSync(file, secret, { encoding: 'utf8' });
  return secret;
}

export const config = {
  root,
  dataDir,
  port: Number(process.env.PORT ?? 8080),
  host: process.env.HOST ?? '0.0.0.0',
  dbPath: process.env.DB_PATH
    ? path.resolve(process.env.DB_PATH)
    : path.join(dataDir, 'zhurnal.db'),
  jwtSecret: loadJwtSecret(),
  adminLogin: process.env.ADMIN_LOGIN ?? 'admin',
  adminPassword: process.env.ADMIN_PASSWORD ?? 'admin12345',
  version: '1.0.0',
};
