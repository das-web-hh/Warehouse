const CACHE_NAME = 'warehouse-pwa-v1';
const SHARE_DB_NAME = 'warehouse-share-target-v1';
const SHARE_DB_VERSION = 1;
const SHARE_STORE_NAME = 'files';

self.addEventListener('install', event => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', event => {
  event.waitUntil(self.clients.claim());
});

function openShareDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(SHARE_DB_NAME, SHARE_DB_VERSION);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(SHARE_STORE_NAME)) {
        database.createObjectStore(SHARE_STORE_NAME, {autoIncrement: true});
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function saveSharedFiles(formData) {
  const files = formData.getAll('file').filter(value => value instanceof File);
  if (!files.length) return 0;

  const records = await Promise.all(files.map(async file => ({
    buffer: await file.arrayBuffer(),
    name: file.name || 'shared-file',
    type: file.type || 'application/octet-stream',
    lastModified: file.lastModified || Date.now()
  })));

  const database = await openShareDatabase();
  try {
    await new Promise((resolve, reject) => {
      const transaction = database.transaction(SHARE_STORE_NAME, 'readwrite');
      const store = transaction.objectStore(SHARE_STORE_NAME);

      records.forEach(record => store.add(record));

      transaction.oncomplete = resolve;
      transaction.onerror = () => reject(transaction.error);
      transaction.onabort = () => reject(transaction.error);
    });
  } finally {
    database.close();
  }
  return files.length;
}

self.addEventListener('fetch', event => {
  const requestUrl = new URL(event.request.url);

  if (event.request.method === 'POST' && requestUrl.pathname === '/upload') {
    event.respondWith((async () => {
      try {
        await saveSharedFiles(await event.request.formData());
      } catch (error) {
        console.error('Share Target storage failed:', error);
      }
      return Response.redirect(new URL('/warehouse.html', self.location.origin), 303);
    })());
    return;
  }

  if (event.request.method !== 'GET') return;

  event.respondWith((async () => {
    try {
      return await fetch(event.request);
    } catch (error) {
      const cachedResponse = await caches.match(event.request);
      return cachedResponse || caches.match('/');
    }
  })());
});