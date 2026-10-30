import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

const root = process.cwd();
const source = path.join(root, 'server', 'data', 'zhurnal.db');

if (!fs.existsSync(source)) {
  console.error(`База не найдена: ${source}`);
  process.exit(1);
}

const dir = path.join(root, 'backups');
fs.mkdirSync(dir, { recursive: true });

const stamp = new Date()
  .toISOString()
  .replace(/[:.]/g, '-')
  .replace('T', '_')
  .slice(0, 19);

const target = path.join(dir, `zhurnal-${stamp}.db`);
const escaped = target.replace(/'/g, "''");

const db = new DatabaseSync(source);
try {
  db.exec(`VACUUM INTO '${escaped}'`);
} finally {
  db.close();
}

const size = fs.statSync(target).size;
console.log(`Резервная копия создана: ${target}`);
console.log(`Размер: ${(size / 1024).toFixed(1)} КБ`);
