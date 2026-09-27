const CACHE_NAME = 'warehouse-pwa-v7';
const SHARE_DB_NAME = 'warehouse-share-target-v1';
const SHARE_DB_VERSION = 1;
const SHARE_STORE_NAME = 'files';
const APP_URL = '/warehouse.html';

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    try {
      const cache = await caches.open(CACHE_NAME);
      await cache.add(APP_URL);
    } catch (error) {
      // Офлайн-кэш необязателен: установка не должна падать
    }
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(names.filter(n => n !== CACHE_NAME).map(n => caches.delete(n)));
    await self.clients.claim();
  })());
});

function openShareDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(SHARE_DB_NAME, SHARE_DB_VERSION);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(SHARE_STORE_NAME)) {
        database.createObjectStore(SHARE_STORE_NAME, { autoIncrement: true });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function normalizeType(file) {
  const name = (file.name || '').toLowerCase();
  let type = (file.type || '').toLowerCase();
  if (name.endsWith('.pdf')) return 'application/pdf';
  if (name.endsWith('.xlsx')) return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
  if (name.endsWith('.xls')) return 'application/vnd.ms-excel';
  if (name.endsWith('.csv')) return 'text/csv';
  if (!type || type === 'application/octet-stream') {
    if (/\.jpe?g$/.test(name)) return 'image/jpeg';
    if (name.endsWith('.png')) return 'image/png';
    if (name.endsWith('.webp')) return 'image/webp';
    if (name.endsWith('.heic')) return 'image/heic';
  }
  return type || 'application/octet-stream';
}

function bytesToBinaryString(bytes) {
  let result = '';
  const CHUNK = 0x8000;
  for (let i = 0; i < bytes.length; i += CHUNK) {
    result += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK));
  }
  return result;
}

function binaryStringToBytes(str) {
  const bytes = new Uint8Array(str.length);
  for (let i = 0; i < str.length; i++) bytes[i] = str.charCodeAt(i) & 0xFF;
  return bytes;
}

// Резервный ручной парсер multipart/form-data. Нужен потому, что на части
// Android-браузеров (воспроизведено на Samsung A56 и в Chrome, и в Samsung
// Internet) встроенный Request.formData() в контексте Service Worker молча
// возвращает 0 полей для запроса Share Target, хотя байты тела запроса на
// самом деле присутствуют и корректны. Разбираем их сами по границе
// (boundary) из заголовка Content-Type.
function parseMultipartManually(arrayBuffer, boundary) {
  if (!boundary) return [];
  const bytes = new Uint8Array(arrayBuffer);
  const binStr = bytesToBinaryString(bytes);
  const delim = '--' + boundary;
  const rawParts = binStr.split(delim);
  const parts = [];
  for (let part of rawParts) {
    if (!part || part.startsWith('--')) continue; // преамбула/финальный маркер
    if (part.startsWith('\r\n')) part = part.slice(2);
    const headerEnd = part.indexOf('\r\n\r\n');
    if (headerEnd === -1) continue;
    const headerStr = part.slice(0, headerEnd);
    let bodyStr = part.slice(headerEnd + 4);
    if (bodyStr.endsWith('\r\n')) bodyStr = bodyStr.slice(0, -2);
    const nameMatch = headerStr.match(/name="([^"]*)"/i);
    const filenameMatch = headerStr.match(/filename="([^"]*)"/i);
    const typeMatch = headerStr.match(/content-type:\s*([^\r\n]+)/i);
    parts.push({
      fieldName: nameMatch ? nameMatch[1] : '',
      filename: filenameMatch ? filenameMatch[1] : '',
      type: typeMatch ? typeMatch[1].trim() : '',
      bytes: binaryStringToBytes(bodyStr)
    });
  }
  return parts;
}

function extractBoundary(contentType) {
  if (!contentType) return '';
  const match = contentType.match(/boundary="?([^";]+)"?/i);
  return match ? match[1] : '';
}

// Возвращает массив «файло-подобных» объектов вида {name, type, size,
// lastModified, arrayBuffer()}. Тело запроса читаем РОВНО ОДИН РАЗ: на
// части Android-устройств повторное чтение через request.clone() (сначала
// formData(), потом ещё раз arrayBuffer()) само по себе обрывает/портит
// поток тела запроса, и второе чтение получает уже пустой хвост — поэтому
// сразу и только разбираем сырые байты вручную, без попытки formData().
async function extractSharedFiles(request) {
  const contentType = request.headers.get('content-type') || '';
  const boundary = extractBoundary(contentType);

  let buffer;
  try {
    buffer = await request.arrayBuffer();
  } catch (error) {
    return {files: [], debug: `не удалось прочитать тело запроса: ${error && error.message || error}`};
  }

  const manualParts = parseMultipartManually(buffer, boundary)
    .filter(part => part.filename && part.bytes.length > 0);
  if (manualParts.length) {
    const files = manualParts.map(part => ({
      name: part.filename,
      type: part.type || 'application/octet-stream',
      size: part.bytes.length,
      lastModified: Date.now(),
      arrayBuffer: async () => part.bytes.buffer
    }));
    return {files, debug: ''};
  }

  const debug = `content-type: "${contentType}" (boundary: ${boundary || 'не найден'}); `
    + `байт в теле запроса: ${buffer.byteLength}`;
  return {files: [], debug};
}

async function saveSharedFiles(request) {
  const {files, debug} = await extractSharedFiles(request);
  if (!files.length) {
    const error = new Error(debug || 'файл не найден в запросе');
    error.isDiagnostic = true;
    throw error;
  }

  const records = [];
  for (const value of files) {
    records.push({
      kind: 'file',
      buffer: await value.arrayBuffer(),
      name: value.name || 'shared-file',
      type: normalizeType(value),
      lastModified: value.lastModified || Date.now()
    });
  }

  const database = await openShareDatabase();
  try {
    await new Promise((resolve, reject) => {
      const tx = database.transaction(SHARE_STORE_NAME, 'readwrite');
      const store = tx.objectStore(SHARE_STORE_NAME);
      records.forEach(record => store.add(record));
      tx.oncomplete = resolve;
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } finally {
    database.close();
  }
  return records.length;
}

function redirectToApp(query) {
  return Response.redirect(new URL(APP_URL + (query || ''), self.location.origin).href, 303);
}

function isShareRequest(request, url) {
  if (url.origin !== self.location.origin) return false;
  // POST на /upload (любой регистр, любой префикс) — это Share Target.
  // Любой другой POST-переход по страницам приложения тоже обрабатываем здесь,
  // иначе статический сервер ответит HTTP 405.
  if (request.method !== 'POST') return false;
  return /\/upload\/?$/i.test(url.pathname) || request.mode === 'navigate';
}

self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);

  if (isShareRequest(request, url)) {
    event.respondWith((async () => {
      try {
        const count = await saveSharedFiles(request);
        return redirectToApp(count ? '?shared=1' : '?shared=empty');
      } catch (error) {
        console.error('Share Target storage failed:', error);
        const reason = encodeURIComponent(String(error && error.message || error || 'unknown').slice(0, 300));
        return redirectToApp('?shared=error&reason=' + reason);
      }
    })());
    return;
  }

  if (request.method !== 'GET') return;

  // Обновление/открытие /upload обычным GET — просто возвращаем в приложение
  if (url.origin === self.location.origin && /\/upload\/?$/i.test(url.pathname)) {
    event.respondWith(redirectToApp(''));
    return;
  }

  event.respondWith((async () => {
    try {
      const response = await fetch(request);
      if (response && response.ok && request.mode === 'navigate' &&
          url.origin === self.location.origin && url.pathname === APP_URL) {
        const cache = await caches.open(CACHE_NAME);
        cache.put(APP_URL, response.clone()).catch(() => {});
      }
      return response;
    } catch (error) {
      const cached = await caches.match(request, { ignoreSearch: true });
      return cached || caches.match(APP_URL) || Response.error();
    }
  })());
});
