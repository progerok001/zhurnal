import { inflateRawSync } from 'node:zlib';
import { ApiError } from './util.js';

export const SUBSTITUTIONS_PAGE =
  'http://www.bobruisk.belstu.by/students/schedule-substitutions';
const SITE_ORIGIN = 'http://www.bobruisk.belstu.by';
const CACHE_TTL_MS = 10 * 60 * 1000;

export interface SubstitutionResult {
  title: string;
  fileUrl: string;
  fileName: string;
  publishedAt: string | null;
  text: string;
  lines: string[];
  blocks: DocBlock[];
  sourcePage: string;
  fetchedAt: string;
  fromCache: boolean;
}

export interface DocCell {
  text: string;
  bold: boolean;
  colspan: number;
}

export interface DocBlock {
  type: 'paragraph' | 'table';
  text?: string;
  bold?: boolean;
  align?: 'left' | 'center' | 'right';
  /** Сколько первых строк таблицы считать заголовком. */
  headerRows?: number;
  rows?: DocCell[][];
}

interface CacheEntry {
  data: SubstitutionResult;
  timestamp: number;
}

let cache: CacheEntry | null = null;
let inFlight: Promise<SubstitutionResult> | null = null;

async function fetchText(url: string, accept: string): Promise<{ body: Buffer; contentType: string }> {
  let response: Response;
  try {
    response = await fetch(url, {
      headers: {
        'User-Agent':
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36',
        Accept: accept,
      },
      signal: AbortSignal.timeout(20_000),
      redirect: 'follow',
    });
  } catch {
    throw new ApiError(502, 'Не удалось связаться с сайтом колледжа. Проверьте интернет на сервере.');
  }
  if (!response.ok) {
    throw new ApiError(
      502,
      `Сайт колледжа ответил ошибкой ${response.status}. Попробуйте позже.`,
    );
  }
  return {
    body: Buffer.from(await response.arrayBuffer()),
    contentType: response.headers.get('content-type') ?? '',
  };
}

interface RawSubstitution {
  title: string;
  path: string;
  publishedAt: string | null;
  sortOrder: number;
}

function firstString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

/**
 * Nuxt отдаёт данные devalue-массивом: объекты ссылаются на значения по индексам.
 * Находим в нём записи с wordFileUrl и разворачиваем ссылки на title/url.
 */
export function parseSubstitutionsPayload(html: string): RawSubstitution[] {
  const match = html.match(
    /<script[^>]*id=["']__NUXT_DATA__["'][^>]*>([\s\S]*?)<\/script>/i,
  );
  if (!match) {
    throw new ApiError(502, 'На странице замен не найден блок данных. Возможно, сайт обновился.');
  }

  let data: unknown;
  try {
    data = JSON.parse(match[1]);
  } catch {
    throw new ApiError(502, 'Не удалось разобрать данные страницы замен.');
  }
  if (!Array.isArray(data)) {
    throw new ApiError(502, 'Неожиданный формат данных страницы замен.');
  }

  const resolve = (value: unknown): unknown => {
    let current = value;
    for (let depth = 0; depth < 8; depth += 1) {
      if (
        typeof current !== 'number' ||
        !Number.isInteger(current) ||
        current < 0 ||
        current >= data.length
      ) {
        return current;
      }
      current = data[current];
    }
    return current;
  };

  const found = new Map<string, RawSubstitution>();
  for (const item of data) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) continue;
    const record = item as Record<string, unknown>;
    if (!('wordFileUrl' in record)) continue;

    const path = firstString(resolve(record.wordFileUrl));
    if (!path) continue;
    const title = firstString(resolve(record.title)) ?? 'Замены';
    const publishedAt = firstString(resolve(record.publishedAt));
    const sortOrderRaw = resolve(record.sortOrder);
    const sortOrder = typeof sortOrderRaw === 'number' ? sortOrderRaw : 0;
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;

    const existing = found.get(normalizedPath);
    if (!existing || sortOrder > existing.sortOrder) {
      found.set(normalizedPath, {
        title: title.replace(/\s+/g, ' ').trim(),
        path: normalizedPath,
        publishedAt,
        sortOrder,
      });
    }
  }

  return [...found.values()].sort((a, b) => {
    if (a.sortOrder !== b.sortOrder) return b.sortOrder - a.sortOrder;
    return (b.publishedAt ?? '').localeCompare(a.publishedAt ?? '');
  });
}

interface ZipEntry {
  method: number;
  compressedSize: number;
  localOffset: number;
}

function readZipEntries(buffer: Buffer): Map<string, ZipEntry> {
  const minOffset = Math.max(0, buffer.length - 66_000);
  let eocd = -1;
  for (let index = buffer.length - 22; index >= minOffset; index -= 1) {
    if (buffer.readUInt32LE(index) === 0x06054b50) {
      eocd = index;
      break;
    }
  }
  if (eocd < 0) throw new Error('ZIP: не найден конец центрального каталога');

  const count = buffer.readUInt16LE(eocd + 10);
  let offset = buffer.readUInt32LE(eocd + 16);
  const entries = new Map<string, ZipEntry>();

  for (let index = 0; index < count; index += 1) {
    if (buffer.readUInt32LE(offset) !== 0x02014b50) {
      throw new Error('ZIP: повреждён центральный каталог');
    }
    const method = buffer.readUInt16LE(offset + 10);
    const compressedSize = buffer.readUInt32LE(offset + 20);
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    const localOffset = buffer.readUInt32LE(offset + 42);
    const name = buffer
      .subarray(offset + 46, offset + 46 + nameLength)
      .toString('utf8');
    entries.set(name, { method, compressedSize, localOffset });
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

function readZipFile(buffer: Buffer, entry: ZipEntry): Buffer {
  const localOffset = entry.localOffset;
  if (buffer.readUInt32LE(localOffset) !== 0x04034b50) {
    throw new Error('ZIP: повреждён локальный заголовок');
  }
  const nameLength = buffer.readUInt16LE(localOffset + 26);
  const extraLength = buffer.readUInt16LE(localOffset + 28);
  const start = localOffset + 30 + nameLength + extraLength;
  const data = buffer.subarray(start, start + entry.compressedSize);
  return entry.method === 0 ? data : inflateRawSync(data);
}

function decodeXmlEntities(value: string): string {
  return value
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, code: string) => String.fromCodePoint(Number(code)))
    .replace(/&#x([0-9a-f]+);/gi, (_, code: string) =>
      String.fromCodePoint(Number.parseInt(code, 16)),
    )
    .replace(/&amp;/g, '&');
}

function readDocumentXml(buffer: Buffer): string {
  if (buffer.length < 4 || buffer.readUInt32LE(0) !== 0x04034b50) {
    throw new ApiError(502, 'Скачанный файл не похож на документ Word (.docx).');
  }
  const entries = readZipEntries(buffer);
  const entry = entries.get('word/document.xml');
  if (!entry) {
    throw new ApiError(502, 'В документе Word не найден текст.');
  }
  return readZipFile(buffer, entry).toString('utf8');
}

export function docxToText(buffer: Buffer): string {
  const xml = readDocumentXml(buffer);

  const text = decodeXmlEntities(
    xml
      .replace(/<w:tab[^>]*\/>/g, '\t')
      .replace(/<w:br[^>]*\/>/g, '\n')
      .replace(/<w:cr[^>]*\/>/g, '\n')
      .replace(/<\/w:p>/g, '\n')
      .replace(/<\/w:tr>/g, '\n')
      .replace(/<\/w:tc>/g, '\t')
      .replace(/<[^>]+>/g, ''),
  );

  return text
    .replace(/\r/g, '')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .replace(/[ \t]{2,}/g, ' ')
    .trim();
}

const BOLD_TAG = /<w:b(?:\s[^>]*)?\/>/;
const PARAGRAPH_RE = /<w:p(?:\s[^>]*)?>([\s\S]*?)<\/w:p>/g;
const TABLE_RE = /<w:tbl(?:\s[^>]*)?>([\s\S]*?)<\/w:tbl>/g;
const ROW_RE = /<w:tr(?:\s[^>]*)?>([\s\S]*?)<\/w:tr>/g;
const CELL_RE = /<w:tc(?:\s[^>]*)?>([\s\S]*?)<\/w:tc>/g;

/** Собирает видимый текст одного абзаца Word. */
function paragraphText(xml: string): string {
  let out = '';
  const re = /<w:t(?:\s[^>]*)?>([\s\S]*?)<\/w:t>|<w:tab(?:\s[^>]*)?\/>|<w:br(?:\s[^>]*)?\/>/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(xml)) !== null) {
    if (match[1] !== undefined) {
      out += decodeXmlEntities(match[1]);
    } else if (match[0].startsWith('<w:tab')) {
      out += ' ';
    } else {
      out += '\n';
    }
  }
  return out.replace(/[ \t]+/g, ' ').replace(/\n{2,}/g, '\n').trim();
}

function paragraphAlign(xml: string): DocBlock['align'] {
  const match = xml.match(/<w:jc\s+w:val="(left|center|right|both)"/);
  if (!match) return undefined;
  if (match[1] === 'center') return 'center';
  if (match[1] === 'right') return 'right';
  return undefined;
}

function parseTable(tableXml: string): DocBlock {
  const rows: DocCell[][] = [];

  for (const rowMatch of tableXml.matchAll(ROW_RE)) {
    const rowXml = rowMatch[1];
    const cells = [...tableCells(rowXml)];
    if (cells.length === 0) continue;
    rows.push(cells);
  }

  return {
    type: 'table',
    headerRows: detectHeaderRows(rows),
    rows,
  };
}

/**
 * В документах замен шапка бывает в одну или две строки:
 * сначала «Группа / Пара / Ауд.», ниже — «Учебный предмет / Ф.И.О.».
 */
function detectHeaderRows(rows: DocCell[][]): number {
  const isBoldRow = (row: DocCell[]) => {
    const filled = row.filter((cell) => cell.text.length > 0);
    return filled.length > 0 && filled.every((cell) => cell.bold);
  };
  const isShortRow = (row: DocCell[]) =>
    row.every((cell) => cell.text.length <= 40) &&
    row.some((cell) => cell.text.length > 0);

  if (rows.length === 0) return 0;
  if (isBoldRow(rows[0])) return 1;
  if (rows.length > 1 && isBoldRow(rows[1]) && isShortRow(rows[0])) return 2;
  return 0;
}

function* tableCells(rowXml: string): Generator<DocCell> {
  const re = new RegExp(CELL_RE.source, 'g');
  let match: RegExpExecArray | null;
  while ((match = re.exec(rowXml)) !== null) {
    const cellXml = match[1];
    const paragraphs: string[] = [];
    for (const paragraph of cellXml.matchAll(PARAGRAPH_RE)) {
      const text = paragraphText(paragraph[1]);
      if (text) paragraphs.push(text);
    }
    const text = paragraphs.join('\n');
    const spanMatch = cellXml.match(/<w:gridSpan\s+w:val="(\d+)"/);
    const span = spanMatch ? Math.min(20, Math.max(1, Number(spanMatch[1]))) : 1;
    yield {
      text,
      bold: BOLD_TAG.test(cellXml),
      colspan: span,
    };
  }
}

/** Разбирает документ Word на абзацы и таблицы, сохраняя порядок блоков. */
export function docxToBlocks(buffer: Buffer): DocBlock[] {
  const xml = readDocumentXml(buffer);
  const bodyStart = xml.indexOf('<w:body>');
  const bodyEnd = xml.lastIndexOf('</w:body>');
  const body =
    bodyStart >= 0 && bodyEnd > bodyStart ? xml.slice(bodyStart + 8, bodyEnd) : xml;

  const blocks: DocBlock[] = [];
  const combined = new RegExp(
    `${TABLE_RE.source}|${PARAGRAPH_RE.source}`,
    'g',
  );

  let match: RegExpExecArray | null;
  while ((match = combined.exec(body)) !== null) {
    const raw = match[1] ?? match[2];
    if (match[0].startsWith('<w:tbl')) {
      const table = parseTable(raw);
      if ((table.rows?.length ?? 0) > 0) blocks.push(table);
    } else {
      const text = paragraphText(raw);
      if (text) {
        blocks.push({
          type: 'paragraph',
          text,
          bold: BOLD_TAG.test(raw),
          align: paragraphAlign(raw),
        });
      }
    }
  }

  return blocks;
}

async function downloadAndParse(raw: RawSubstitution): Promise<SubstitutionResult> {
  const fileUrl = new URL(raw.path, SITE_ORIGIN).toString();
  const file = await fetchText(
    fileUrl,
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document,*/*',
  );
  const text = docxToText(file.body);
  if (!text) {
    throw new ApiError(502, 'Документ замен оказался пустым.');
  }
  let blocks: DocBlock[] = [];
  try {
    blocks = docxToBlocks(file.body).slice(0, 800);
  } catch {
    blocks = [];
  }
  return {
    title: raw.title,
    fileUrl,
    fileName: raw.path.split('/').pop() ?? 'substitutions.docx',
    publishedAt: raw.publishedAt,
    text,
    lines: text.split('\n'),
    blocks,
    sourcePage: SUBSTITUTIONS_PAGE,
    fetchedAt: new Date().toISOString(),
    fromCache: false,
  };
}

async function loadSubstitutions(): Promise<SubstitutionResult> {
  const page = await fetchText(SUBSTITUTIONS_PAGE, 'text/html,application/xhtml+xml');
  const html = page.body.toString('utf8');
  const list = parseSubstitutionsPayload(html);
  if (list.length === 0) {
    throw new ApiError(404, 'На сайте колледжа сейчас нет опубликованных замен.');
  }
  return downloadAndParse(list[0]);
}

export async function getSubstitutions(force = false): Promise<SubstitutionResult> {
  const now = Date.now();
  if (!force && cache && now - cache.timestamp < CACHE_TTL_MS) {
    return { ...cache.data, fromCache: true };
  }
  if (inFlight) return inFlight;

  inFlight = (async () => {
    const data = await loadSubstitutions();
    cache = { data, timestamp: Date.now() };
    return data;
  })();

  try {
    return await inFlight;
  } finally {
    inFlight = null;
  }
}
