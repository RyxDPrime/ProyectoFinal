/**
 * sw.js — Service Worker para modo offline
 *
 * Estrategias:
 *   - Recursos estáticos (CSS, JS, fonts): Cache First
 *   - API /api/*: Network First, sin cachear
 *   - Páginas HTML: Network First con fallback a caché
 */

const CACHE_NAME = 'encuesta-pucmm-v2';

const PRECACHE_ASSETS = [
    '/login',
    '/registro',
    '/css/style.css',
    '/js/app.js',
];

self.addEventListener('install', event => {
    console.log('[SW] Instalando...');
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => {
                console.log('[SW] Pre-cacheando assets');
                // Cachear de a uno para no fallar si alguno no existe
                return Promise.allSettled(
                    PRECACHE_ASSETS.map(url =>
                        cache.add(url).catch(err => console.warn('[SW] No se pudo cachear:', url, err))
                    )
                );
            })
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', event => {
    console.log('[SW] Activando...');
    event.waitUntil(
        caches.keys().then(keys =>
            Promise.all(
                keys
                    .filter(key => key !== CACHE_NAME)
                    .map(key => {
                        console.log('[SW] Eliminando caché obsoleto:', key);
                        return caches.delete(key);
                    })
            )
        ).then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', event => {
    const { request } = event;
    const url = new URL(request.url);

    if (request.url.startsWith('ws://') || request.url.startsWith('wss://')) return;

    if (url.pathname.startsWith('/api/')) {
        event.respondWith(networkOnly(request));
        return;
    }

    if (!url.hostname.includes(self.location.hostname)) {
        event.respondWith(cacheFirst(request));
        return;
    }

    event.respondWith(networkFirst(request));
});

/**
 * Network First: intenta la red, si falla usa caché.
 * Para páginas HTML y recursos de la app.
 */
async function networkFirst(request) {
    try {
        const networkRes = await fetch(request);
        if (networkRes.ok) {
            const cache = await caches.open(CACHE_NAME);
            cache.put(request, networkRes.clone());
        }
        return networkRes;
    } catch {
        const cached = await caches.match(request);
        if (cached) return cached;
        // Fallback: nunca devolver una vista protegida al perder conexión.
        if (request.headers.get('accept')?.includes('text/html')) {
            return caches.match('/login');
        }
        return new Response('Sin conexión', { status: 503 });
    }
}

/**
 * Cache First: busca en caché primero, si no está va a la red.
 * Para recursos externos y fuentes.
 */
async function cacheFirst(request) {
    const cached = await caches.match(request);
    if (cached) return cached;
    try {
        const networkRes = await fetch(request);
        if (networkRes.ok) {
            const cache = await caches.open(CACHE_NAME);
            cache.put(request, networkRes.clone());
        }
        return networkRes;
    } catch {
        return new Response('Sin conexión', { status: 503 });
    }
}

/**
 * Network Only: para peticiones de API, no cachear nunca.
 */
async function networkOnly(request) {
    try {
        return await fetch(request);
    } catch {
        return new Response(
            JSON.stringify({ mensaje: 'Sin conexión al servidor' }),
            { status: 503, headers: { 'Content-Type': 'application/json' } }
        );
    }
}

