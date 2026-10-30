import { ApiError } from './util.js';

/** Фото с телефона приходит data-URL'ом; ограничиваем размер, чтобы не раздувать базу. */
export const MAX_AVATAR_LENGTH = 420_000;

const IMAGE_DATA_URL =
  /^data:image\/(png|jpe?g|webp);base64,[A-Za-z0-9+/\r\n]+={0,2}$/;

/**
 * Приводит аватарку к виду, который хранится в базе:
 * `null` — инициалы, короткая строка — эмодзи, `data:image/...` — фото с устройства.
 */
export function normalizeAvatar(value: string | null | undefined): string | null {
  if (value == null) return null;
  const trimmed = value.trim();
  if (!trimmed) return null;

  if (trimmed.startsWith('data:image/')) {
    if (trimmed.length > MAX_AVATAR_LENGTH) {
      throw new ApiError(400, 'Фото слишком большое — выберите другое');
    }
    if (!IMAGE_DATA_URL.test(trimmed)) {
      throw new ApiError(400, 'Поддерживаются только изображения PNG, JPEG и WebP');
    }
    return trimmed;
  }

  if ([...trimmed].length > 8) {
    throw new ApiError(400, 'Слишком длинная аватарка');
  }
  return trimmed;
}

export function isImageAvatar(value: string | null | undefined): boolean {
  return typeof value === 'string' && value.startsWith('data:image/');
}
