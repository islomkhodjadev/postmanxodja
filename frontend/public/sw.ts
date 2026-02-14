/// <reference lib="webworker" />

/**
 * Service Worker for PostmanXodja PWA.
 *
 * Strategy:
 *  - App shell (HTML, JS, CSS, images): Cache-first with network update.
 *  - API calls: Network-first with no caching (data must be fresh).
 *  - Navigation requests: Return cached index.html for SPA routing.
 */

declare const self: ServiceWorkerGlobalScope;

const CACHE_NAME = 'postmanxodja-v1';

// Assets to pre-cache on install (app shell)
const PRECACHE_ASSETS = ['/', '/index.html'];

// Install — pre-cache the app shell
self.addEventListener('install', (event: ExtendableEvent) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(PRECACHE_ASSETS)),
  );
  // Activate immediately
  self.skipWaiting();
});

// Activate — clean up old caches
self.addEventListener('activate', (event: ExtendableEvent) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)),
      ),
    ),
  );
  // Take control of all clients immediately
  self.clients.claim();
});

// Fetch handler
self.addEventListener('fetch', (event: FetchEvent) => {
  const { request } = event;
  const url = new URL(request.url);

  // Never cache API requests
  if (url.pathname.startsWith('/api') || url.pathname === '/health') {
    return;
  }

  // For navigation requests, serve the cached index.html (SPA routing)
  if (request.mode === 'navigate') {
    event.respondWith(
      caches.match('/index.html').then((cached) => cached || fetch(request)),
    );
    return;
  }

  // For static assets: cache-first, then network
  event.respondWith(
    caches.match(request).then((cached) => {
      const fetchPromise = fetch(request)
        .then((response) => {
          // Cache successful GET responses
          if (response.ok && request.method === 'GET') {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, clone));
          }
          return response;
        })
        .catch(() => {
          // If both cache and network fail, return a basic offline page
          if (request.destination === 'document') {
            return caches.match('/index.html') as Promise<Response>;
          }
          return new Response('', { status: 503, statusText: 'Offline' });
        });

      return cached || fetchPromise;
    }),
  );
});

export {};
