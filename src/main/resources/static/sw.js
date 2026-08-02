/*
 * New Job and New Quote are the two screens that need to work with zero
 * signal -- those are the things genuinely lost if they fail ("capturing
 * the job", "capturing the quote"). Aggregate screens (This Week, Who Owes
 * Me) and the letterhead-styled quote/receipt previews are reads over
 * server data; offline they just fail to load, which is an honest failure
 * mode, not a broken one -- only capturing new data was worth the
 * complexity, not styling it while offline.
 *
 * Static assets (JS, JSON seed lists, icons) are precached on install.
 * Neither /jobs/new nor /quotes/new is precached at install time -- their
 * HTML is server-rendered per-session (CSRF token, recent customers/quotes,
 * last entry) and would go stale sitting in a cache. Instead: network-
 * first, and only the last successfully-loaded copy is kept as a fallback
 * for when the network genuinely isn't there. Neither page's offline
 * submission depends on that per-session HTML anyway -- both go to
 * CSRF-exempt JSON endpoints (see SecurityConfig, JobApiController,
 * QuoteApiController).
 */

const CACHE_NAME = 'yincools-v5';
const OFFLINE_ENTRY_PATHS = ['/jobs/new', '/quotes/new'];

const PRECACHE_URLS = [
    '/css/tokens.css',
    '/css/components.css',
    '/vehicle-picker.js',
    '/parts-chips.js',
    '/offline-queue.js',
    '/quote-items.js',
    '/offline-quote-queue.js',
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

    // Navigation to New Job or New Quote: network-first, cache the good
    // response, fall back to the last cached copy if the network is
    // unreachable.
    if (OFFLINE_ENTRY_PATHS.indexOf(url.pathname) !== -1 && request.mode === 'navigate') {
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
