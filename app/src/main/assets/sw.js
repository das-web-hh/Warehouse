/* Service Worker — "Mening shaxsiy kutubxonam"
   Ilova qobigʻi (index.html, manifest, ikonkalar) oflayn ishlashi uchun keshlanadi.
   Kitoblar IndexedDB’da saqlanadi, shuning uchun ular har doim oflayn mavjud.
   Versiyani oʻzgartirsangiz (VERSION), yangi kesh yaratiladi va foydalanuvchiga “Yangilash” taklif qilinadi. */
const VERSION = 'v1';
const SHELL_CACHE = 'kutubxona-shell-' + VERSION;
const FONT_CACHE = 'kutubxona-fonts-' + VERSION;
const SHELL = [
  './',
  './index.html',
  './manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png',
  './icons/apple-touch-icon.png'
];
const FONT_HOSTS = ['fonts.googleapis.com', 'fonts.gstatic.com'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE).then((cache) =>
      Promise.all(SHELL.map((url) => cache.add(url).catch(() => {})))
    )
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys
        .filter((k) => k.startsWith('kutubxona-') && k !== SHELL_CACHE && k !== FONT_CACHE)
        .map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

/* Foydalanuvchi “Yangilash” bosganda yangi versiya darhol faollashadi */
self.addEventListener('message', (event) => {
  if (event.data === 'SKIP_WAITING') self.skipWaiting();
});

function timeout(ms) {
  return new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), ms));
}

/* Sahifa ochilishi: avval tarmoq (eng yangi versiya), 4 soniyada javob boʻlmasa yoki oflayn boʻlsa — keshdan */
async function handleNavigation(request) {
  const cache = await caches.open(SHELL_CACHE);
  try {
    const fresh = await Promise.race([fetch(request), timeout(4000)]);
    if (fresh && fresh.ok) cache.put('./index.html', fresh.clone());
    return fresh;
  } catch (e) {
    return (await cache.match(request, { ignoreSearch: true }))
        || (await cache.match('./index.html'))
        || Response.error();
  }
}

/* Statik fayllar: keshdan darhol, fonda yangilanadi */
async function staleWhileRevalidate(request, cacheName) {
  const cache = await caches.open(cacheName);
  const cached = await cache.match(request);
  const network = fetch(request).then((res) => {
    if (res && (res.ok || res.type === 'opaque')) cache.put(request, res.clone());
    return res;
  }).catch(() => null);
  return cached || (await network) || Response.error();
}

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;               // POST/PUT (serverga saqlash) — hech qachon keshlanmaydi
  const url = new URL(req.url);

  if (url.origin !== self.location.origin) {
    // Google Fonts — keshlanadi; boshqa tashqi soʻrovlar (API) — toʻgʻridan-toʻgʻri tarmoqqa
    if (FONT_HOSTS.includes(url.hostname)) {
      event.respondWith(staleWhileRevalidate(req, FONT_CACHE));
    }
    return;
  }
  if (url.pathname.includes('/api/')) return;      // shu domendagi API ham keshlanmaydi

  if (req.mode === 'navigate') {
    event.respondWith(handleNavigation(req));
    return;
  }
  event.respondWith(staleWhileRevalidate(req, SHELL_CACHE));
});
