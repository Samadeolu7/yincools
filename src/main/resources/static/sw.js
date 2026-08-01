/*
 * Only the New Job screen needs to work with zero signal -- that's the
 * thing that's genuinely lost if it fails ("capturing the job"). Aggregate
 * screens (This Week, Who Owes Me) are reads over server data; offline they
 * just fail to load, which is an honest failure mode, not a broken one.
 *
 * Static assets (JS, JSON seed lists, icons) are precached on install.
 * /jobs/new itself is NOT precached at install time -- its HTML is
 * server-rendered per-session (CSRF token, recent customers, last entry)
 * and would go stale sitting in a cache. Instead: network-first, and only
 * the last successfully-loaded copy is kept as a fallback for when the
 * network genuinely isn't there. The offline job submission itself doesn't
 * depend on that page's CSRF token anyway -- it goes to the CSRF-exempt
 * /api/jobs JSON endpoint (see SecurityConfig, JobApiController).
 */

const CACHE_NAME = 'yincools-v3';
const NEW_JOB_PATH = '/jobs/new';

const PRECACHE_URLS = [
    '/css/tokens.css',
    '/css/components.css',
    '/vehicle-picker.js',
    '/parts-chips.js',
    '/offline-queue.js',
    '/vehicle-seed.json',
    '/parts-seed.json',
    '/manifest.webmanifest',
    '/icon-192.png',
    '/icon-512.png'
];

self.addEventListener('install', function (event) {
    event.waitUntil(
        caches.open(CACHE_NAME).then(function (cache) {
            return cache.addAll(PRECACHE_URLS);
        })
    );
    self.skipWaiting();
});

self.addEventListener('activate', function (event) {
    event.waitUntil(
        caches.keys().then(function (keys) {
            return Promise.all(
                keys.filter(function (key) { return key !== CACHE_NAME; })
                    .map(function (key) { return caches.delete(key); })
            );
        })
    );
    self.clients.claim();
});

self.addEventListener('fetch', function (event) {
    const request = event.request;
    if (request.method !== 'GET') return;

    const url = new URL(request.url);

    // Navigation to New Job: network-first, cache the good response, fall
    // back to the last cached copy if the network is unreachable.
    if (url.pathname === NEW_JOB_PATH && request.mode === 'navigate') {
        event.respondWith(
            fetch(request)
                .then(function (response) {
                    const copy = response.clone();
                    caches.open(CACHE_NAME).then(function (cache) { cache.put(request, copy); });
                    return response;
                })
                .catch(function () {
                    return caches.match(request);
                })
        );
        return;
    }

    // Precached static assets: cache-first, since they're versioned by the
    // cache name and don't change per-session.
    if (PRECACHE_URLS.indexOf(url.pathname) !== -1) {
        event.respondWith(
            caches.match(request).then(function (cached) {
                return cached || fetch(request);
            })
        );
    }
});
