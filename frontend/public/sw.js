/**
 * Service Worker for PostmanXodja PWA.
 *
 * Strategy:
 *  - App shell (HTML, JS, CSS, images): Cache-first with network update.
 *  - API calls: Network-only (data must be fresh).
 *  - Navigation requests: Return cached index.html for SPA routing.
 */

const CACHE_NAME = 'postmanxodja-v1';

const PRECACHE_ASSETS = ['/', '/index.html'];

// Install — pre-cache the app shell
self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open(CACHE_NAME).then(function (cache) {
      return cache.addAll(PRECACHE_ASSETS);
    })
  );
  self.skipWaiting();
});

// Activate — clean up old caches
self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(
        keys
          .filter(function (key) { return key !== CACHE_NAME; })
          .map(function (key) { return caches.delete(key); })
      );
    })
  );
  self.clients.claim();
});

// Fetch handler
self.addEventListener('fetch', function (event) {
  var request = event.request;
  var url = new URL(request.url);

  // Never cache API requests or health checks
  if (url.pathname.startsWith('/api') || url.pathname === '/health') {
    return;
  }

  // For navigation requests, serve cached index.html (SPA routing)
  if (request.mode === 'navigate') {
    event.respondWith(
      caches.match('/index.html').then(function (cached) {
        return cached || fetch(request);
      })
    );
    return;
  }

  // For static assets: cache-first, then network
  event.respondWith(
    caches.match(request).then(function (cached) {
      var fetchPromise = fetch(request)
        .then(function (response) {
          if (response.ok && request.method === 'GET') {
            var clone = response.clone();
            caches.open(CACHE_NAME).then(function (cache) {
              cache.put(request, clone);
            });
          }
          return response;
        })
        .catch(function () {
          if (request.destination === 'document') {
            return caches.match('/index.html');
          }
          return new Response('', { status: 503, statusText: 'Offline' });
        });

      return cached || fetchPromise;
    })
  );
});
