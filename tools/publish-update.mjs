import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const gradlePath = path.join(root, 'android', 'app', 'build.gradle.kts');
const apkSource = path.join(root, 'android', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
const publicDir = path.join(root, 'server', 'public');
const apkTarget = path.join(publicDir, 'zhurnal-debug.apk');
const metaTarget = path.join(publicDir, 'update.json');

function fail(message) {
  console.error(`[publish-update] ${message}`);
  process.exit(1);
}

if (!fs.existsSync(gradlePath)) fail(`Не найден ${gradlePath}`);
if (!fs.existsSync(apkSource)) {
  fail('APK не собран. Сначала выполните build-apk.cmd или gradlew assembleDebug.');
}

const gradle = fs.readFileSync(gradlePath, 'utf8');
const versionCode = Number(gradle.match(/versionCode\s*=\s*(\d+)/)?.[1]);
const versionName = gradle.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
if (!Number.isInteger(versionCode) || versionCode <= 0) fail('Не удалось прочитать versionCode');
if (!versionName) fail('Не удалось прочитать versionName');

fs.mkdirSync(publicDir, { recursive: true });
fs.copyFileSync(apkSource, apkTarget);

const apk = fs.readFileSync(apkTarget);
const meta = {
  versionCode,
  versionName,
  apkUrl: '/api/app/download',
  notes: process.argv.slice(2).join(' ').trim() ||
    'Обновление приложения: исправления и новые возможности.',
  mandatory: true,
  publishedAt: new Date().toISOString(),
  size: apk.length,
  sha256: crypto.createHash('sha256').update(apk).digest('hex'),
};

fs.writeFileSync(metaTarget, `${JSON.stringify(meta, null, 2)}\n`, 'utf8');

console.log('');
console.log('  Обновление опубликовано');
console.log(`  Версия: ${versionName} (${versionCode})`);
console.log(`  APK:    ${apkTarget}`);
console.log(`  Мета:   ${metaTarget}`);
console.log(`  SHA256: ${meta.sha256}`);
console.log('');
