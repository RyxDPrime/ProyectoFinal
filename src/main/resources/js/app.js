/**
 * app.js — Módulos centrales del frontend
 *
 * Contiene:
 *   Auth      — Autenticación con JWT y Web Storage
 *   API       — Cliente HTTP con token automático
 *   OfflineDB — Almacenamiento offline con IndexedDB
 *   SyncWS    — Sincronización via WebSocket
 *   toast()   — Notificaciones visuales
 */

'use strict';

// ════════════════════════════════════════════════════════════════════
// Configuración
// ════════════════════════════════════════════════════════════════════
const CONFIG = {
    API_BASE: '',             // Vacío = mismo origen donde sirve Javalin
    WS_PATH: '/ws/sync',
    STORAGE_KEY_TOKEN:   'enc_token',
    STORAGE_KEY_USUARIO: 'enc_usuario',
    STORAGE_KEY_SYNC_COUNTER: 'enc_sync_counter',
    DB_NAME:    'EncuestaPUCMM',
    DB_VERSION: 1,
    STORE_PENDIENTES: 'pendientes',
    AUTO_SYNC_INTERVAL_MS: 15000,
};

const ROUTE_ACCESS = {
    '/encuesta': ['ENCUESTADOR', 'ADMIN'],
    '/bandeja': ['ENCUESTADOR', 'ADMIN'],
    '/listado': ['ENCUESTADOR', 'VISUALIZADOR', 'ADMIN'],
    '/mapa': ['ENCUESTADOR', 'VISUALIZADOR', 'ADMIN'],
    '/usuarios': ['ADMIN'],
};

const FEATURE_ACCESS = {
    captura: ['ENCUESTADOR', 'ADMIN'],
    sync: ['ENCUESTADOR', 'ADMIN'],
    admin: ['ADMIN'],
};

// ════════════════════════════════════════════════════════════════════
// Auth — gestión de sesión con JWT en sessionStorage
// ════════════════════════════════════════════════════════════════════
const Auth = {
    /**
     * Realiza login contra la API y persiste el token en sessionStorage.
     * @returns {Promise<object>} datos del usuario
     */
    login: async function(email, password) {
        const res = await fetch(CONFIG.API_BASE + '/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }),
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.mensaje || 'Error de autenticación');

        // Persistir solo durante la sesión del navegador/pestaña.
        sessionStorage.setItem(CONFIG.STORAGE_KEY_TOKEN,   data.token);
        sessionStorage.setItem(CONFIG.STORAGE_KEY_USUARIO, JSON.stringify({
            usuarioId: data.usuarioId,
            nombre:    data.nombre,
            email:     email,
            rol:       data.rol,
        }));
        return data;
    },

    logout: function() {
        sessionStorage.removeItem(CONFIG.STORAGE_KEY_TOKEN);
        sessionStorage.removeItem(CONFIG.STORAGE_KEY_USUARIO);
        // Limpiar claves heredadas de versiones anteriores.
        localStorage.removeItem(CONFIG.STORAGE_KEY_TOKEN);
        localStorage.removeItem(CONFIG.STORAGE_KEY_USUARIO);
        try {
            sessionStorage.removeItem('enc_cache');
        } catch {}
        // replace() evita volver a una vista protegida con el botón "atrás".
        window.location.replace('/login');
    },

    getToken: function() {
        return sessionStorage.getItem(CONFIG.STORAGE_KEY_TOKEN);
    },

    getUsuario: function() {
        const raw = sessionStorage.getItem(CONFIG.STORAGE_KEY_USUARIO);
        try { return raw ? JSON.parse(raw) : null; } catch { return null; }
    },

    estaAutenticado: function() {
        const token = this.getToken();
        if (!token) return false;
        // Verificación básica de expiración sin librería
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            return payload.exp * 1000 > Date.now();
        } catch { return false; }
    },

    /**
     * Guard de autenticación: si no hay sesión válida, redirige al login.
     * Llamar al inicio de cada página protegida.
     */
    requerirAuth: function() {
        if (!this.estaAutenticado()) {
            window.location.href = '/login';
        }
    },

    getRutaInicioPorRol: function(rol = null) {
        const rolActual = (rol || this.getUsuario()?.rol || '').toUpperCase();
        if (rolActual === 'VISUALIZADOR') return '/listado';
        return '/encuesta';
    },

    tieneAccesoARuta: function(pathname = window.location.pathname) {
        const rolActual = (this.getUsuario()?.rol || '').toUpperCase();
        if (!rolActual) return false;

        const ruta = Object.keys(ROUTE_ACCESS)
            .find(base => pathname === base || pathname.startsWith(base + '/'));
        if (!ruta) return true;

        return ROUTE_ACCESS[ruta].includes(rolActual);
    },

    requerirAccesoRuta: function(pathname = window.location.pathname) {
        if (!this.tieneAccesoARuta(pathname)) {
            window.location.replace(this.getRutaInicioPorRol());
        }
    },

    aplicarVisibilidadPorRol: function() {
        const rolActual = (this.getUsuario()?.rol || '').toUpperCase();
        if (!rolActual) return;

        document.querySelectorAll('[data-feature]').forEach(el => {
            const feature = (el.dataset.feature || '').toLowerCase();
            const allowedRoles = FEATURE_ACCESS[feature];
            if (!allowedRoles) return;

            if (allowedRoles.includes(rolActual)) {
                el.style.removeProperty('display');
            } else {
                el.style.display = 'none';
            }
        });
    },
};

// Exponer Auth para uso en handlers inline (onclick="Auth.logout()").
window.Auth = Auth;

// ════════════════════════════════════════════════════════════════════
// API — cliente HTTP con JWT automático
// ════════════════════════════════════════════════════════════════════
const API = {
    TIMEOUT_MS: 30000,  // 30 segundos de timeout

    _headers: function(extra = {}) {
        const token = Auth.getToken();
        return {
            'Content-Type': 'application/json',
            ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
            ...extra,
        };
    },

    _withTimeout: function(promise, ms) {
        return Promise.race([
            promise,
            new Promise((_, reject) =>
                setTimeout(() => reject(new Error('Timeout - servidor no responde')), ms)
            )
        ]);
    },

    _check: async function(res) {
        const data = await res.json().catch(() => ({}));
        if (res.status === 401) { Auth.logout(); return; }
        if (!res.ok) throw new Error(data.mensaje || `Error HTTP ${res.status}`);
        return data;
    },

    get: async function(path) {
        const res = await this._withTimeout(
            fetch(CONFIG.API_BASE + path, { headers: this._headers() }),
            this.TIMEOUT_MS
        );
        return this._check(res);
    },

    post: async function(path, body) {
        const res = await this._withTimeout(
            fetch(CONFIG.API_BASE + path, {
                method: 'POST',
                headers: this._headers(),
                body: JSON.stringify(body),
            }),
            this.TIMEOUT_MS
        );
        return this._check(res);
    },

    put: async function(path, body) {
        const res = await this._withTimeout(
            fetch(CONFIG.API_BASE + path, {
                method: 'PUT',
                headers: this._headers(),
                body: JSON.stringify(body),
            }),
            this.TIMEOUT_MS
        );
        return this._check(res);
    },

    delete: async function(path) {
        const res = await this._withTimeout(
            fetch(CONFIG.API_BASE + path, {
                method: 'DELETE',
                headers: this._headers(),
            }),
            this.TIMEOUT_MS
        );
        return this._check(res);
    },
};

// ════════════════════════════════════════════════════════════════════
// Utilidad de IDs Mongo — normaliza ObjectId serializados como objeto
// ════════════════════════════════════════════════════════════════════
function normalizarIdMongo(valor) {
    if (valor == null) return '';
    if (typeof valor === 'string' || typeof valor === 'number') {
        const str = String(valor).trim();
        const oidMatch = str.match(/^ObjectId\("?([a-fA-F0-9]{24})"?\)$/);
        return oidMatch ? oidMatch[1] : str;
    }

    if (typeof valor === 'object') {
        if (typeof valor.$oid === 'string') return valor.$oid;
        if (typeof valor.oid === 'string') return valor.oid;
        if (typeof valor.hexString === 'string') return valor.hexString;
        if (typeof valor._id === 'string') return valor._id;
        if (valor._id) return normalizarIdMongo(valor._id);
        if (valor.id) return normalizarIdMongo(valor.id);
        if (typeof valor.toHexString === 'function') {
            try { return valor.toHexString(); } catch {}
        }

        // Evita resultados inválidos como "[object Object]".
        return '';
    }

    return '';
}

window.normalizarIdMongo = normalizarIdMongo;

function escapeHtml(valor) {
    if (valor == null) return '';
    return String(valor)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

window.escapeHtml = escapeHtml;

function safeImageSrc(valor) {
    if (typeof valor !== 'string') return '';
    return /^data:image\/(png|jpeg|jpg|gif|webp);base64,/i.test(valor) ? valor : '';
}

window.safeImageSrc = safeImageSrc;

// ════════════════════════════════════════════════════════════════════
// SyncStats — contador persistente de sincronizaciones por usuario/día
// ════════════════════════════════════════════════════════════════════
const SyncStats = {
    _hoyClave: function() {
        return new Date().toISOString().slice(0, 10);
    },

    _usuarioKey: function() {
        const usuarioId = Auth.getUsuario()?.usuarioId || 'anon';
        return `${usuarioId}:${this._hoyClave()}`;
    },

    _leerTodo: function() {
        try {
            return JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEY_SYNC_COUNTER) || '{}');
        } catch {
            return {};
        }
    },

    _guardarTodo: function(data) {
        localStorage.setItem(CONFIG.STORAGE_KEY_SYNC_COUNTER, JSON.stringify(data));
    },

    obtenerSincronizadasHoy: function() {
        const data = this._leerTodo();
        return Number(data[this._usuarioKey()] || 0);
    },

    sumarSincronizadas: function(cantidad) {
        const n = Number(cantidad) || 0;
        if (n <= 0) return;
        const data = this._leerTodo();
        const key = this._usuarioKey();
        data[key] = Number(data[key] || 0) + n;
        this._guardarTodo(data);
    },
};

window.SyncStats = SyncStats;

// ════════════════════════════════════════════════════════════════════
// OfflineDB — IndexedDB para encuestas pendientes
// ════════════════════════════════════════════════════════════════════
const OfflineDB = (() => {
    let db = null;
    const PENDIENTES_EVENT = 'enc-pendientes-changed';

    function notificarCambioPendientes() {
        window.dispatchEvent(new CustomEvent(PENDIENTES_EVENT));
    }

    function abrirDB() {
        return new Promise((resolve, reject) => {
            if (db) { resolve(db); return; }

            const req = indexedDB.open(CONFIG.DB_NAME, CONFIG.DB_VERSION);

            req.onupgradeneeded = e => {
                const base = e.target.result;
                if (!base.objectStoreNames.contains(CONFIG.STORE_PENDIENTES)) {
                    const store = base.createObjectStore(CONFIG.STORE_PENDIENTES, {
                        keyPath: '_localId', autoIncrement: true
                    });
                    store.createIndex('creadoEn', 'creadoEn', { unique: false });
                }
            };

            req.onsuccess = e => { db = e.target.result; resolve(db); };
            req.onerror   = e => reject(e.target.error);
        });
    }

    // Fallback a localStorage si IndexedDB no está disponible
    const LS_KEY = 'enc_pendientes';
    const usarLS = !window.indexedDB;

    const lsFallback = {
        _cargar: () => { try { return JSON.parse(localStorage.getItem(LS_KEY) || '[]'); } catch { return []; } },
        _guardar: (arr) => localStorage.setItem(LS_KEY, JSON.stringify(arr)),
        guardar: (enc) => {
            const a = lsFallback._cargar();
            const localId = Date.now() + Math.floor(Math.random() * 1000);
            a.push({ ...enc, _localId: localId });
            lsFallback._guardar(a);
            notificarCambioPendientes();
        },
        obtenerTodas: () => lsFallback._cargar(),
        obtenerPorLocalId: (localId) => lsFallback._cargar().find(item => item && item._localId === localId) || null,
        eliminar: (i) => { const a = lsFallback._cargar(); a.splice(i, 1); lsFallback._guardar(a); notificarCambioPendientes(); },
        eliminarPorLocalId: (localId) => {
            const a = lsFallback._cargar();
            const idx = a.findIndex(item => item && item._localId === localId);
            if (idx >= 0) {
                a.splice(idx, 1);
                lsFallback._guardar(a);
                notificarCambioPendientes();
            }
        },
        actualizar: (i, cambios) => { const a = lsFallback._cargar(); a[i] = {...a[i], ...cambios}; lsFallback._guardar(a); notificarCambioPendientes(); },
        contarPendientes: () => lsFallback._cargar().length,
        limpiarSincronizadas: () => { localStorage.removeItem(LS_KEY); notificarCambioPendientes(); },
    };

    if (usarLS) return lsFallback;

    return {
        /**
         * Guarda una encuesta en IndexedDB.
         * @param {object} encuesta
         */
        guardar: function(encuesta) {
            abrirDB().then(base => {
                const tx    = base.transaction(CONFIG.STORE_PENDIENTES, 'readwrite');
                const store = tx.objectStore(CONFIG.STORE_PENDIENTES);
                store.add({ ...encuesta, _savedAt: Date.now() });
                tx.oncomplete = () => {
                    this._actualizarCache();
                    notificarCambioPendientes();
                };
            }).catch(console.error);
        },

        /**
         * Obtiene todas las encuestas pendientes (síncronamente desde caché).
         * Para sincronización async usa obtenerTodasAsync().
         */
        obtenerTodas: function() {
            // Versión síncrona simplificada usando sessionStorage como caché de lectura
            try {
                const cached = sessionStorage.getItem('enc_cache');
                return cached ? JSON.parse(cached) : [];
            } catch { return []; }
        },

        obtenerTodasAsync: function() {
            return new Promise((resolve, reject) => {
                abrirDB().then(base => {
                    const tx    = base.transaction(CONFIG.STORE_PENDIENTES, 'readonly');
                    const store = tx.objectStore(CONFIG.STORE_PENDIENTES);
                    const req   = store.getAll();
                    req.onsuccess = () => {
                        const result = req.result || [];
                        // Actualizar caché de sesión
                        try { sessionStorage.setItem('enc_cache', JSON.stringify(result)); } catch {}
                        resolve(result);
                    };
                    req.onerror = () => reject(req.error);
                }).catch(reject);
            });
        },

        /**
         * Elimina un registro por su índice local.
         */
        eliminar: function(idx) {
            abrirDB().then(base => {
                const tx    = base.transaction(CONFIG.STORE_PENDIENTES, 'readwrite');
                const store = tx.objectStore(CONFIG.STORE_PENDIENTES);
                const req   = store.getAll();
                req.onsuccess = () => {
                    const todos = req.result;
                    if (todos[idx]) {
                        store.delete(todos[idx]._localId);
                        this._actualizarCache();
                        notificarCambioPendientes();
                    }
                };
            }).catch(console.error);
        },

        obtenerPorLocalId: function(localId) {
            return new Promise((resolve, reject) => {
                abrirDB().then(base => {
                    const tx = base.transaction(CONFIG.STORE_PENDIENTES, 'readonly');
                    const store = tx.objectStore(CONFIG.STORE_PENDIENTES);
                    const req = store.get(localId);
                    req.onsuccess = () => resolve(req.result || null);
                    req.onerror = () => reject(req.error);
                }).catch(reject);
            });
        },

        eliminarPorLocalId: function(localId) {
            abrirDB().then(base => {
                const tx = base.transaction(CONFIG.STORE_PENDIENTES, 'readwrite');
                tx.objectStore(CONFIG.STORE_PENDIENTES).delete(localId);
                tx.oncomplete = () => {
                    this._actualizarCache();
                    notificarCambioPendientes();
                };
            }).catch(console.error);
        },

        /**
         * Actualiza campos de un registro por índice.
         */
        actualizar: function(idx, cambios) {
            abrirDB().then(base => {
                const tx    = base.transaction(CONFIG.STORE_PENDIENTES, 'readwrite');
                const store = tx.objectStore(CONFIG.STORE_PENDIENTES);
                const req   = store.getAll();
                req.onsuccess = () => {
                    const todos = req.result;
                    if (todos[idx]) {
                        const actualizado = { ...todos[idx], ...cambios };
                        store.put(actualizado);
                        this._actualizarCache();
                        notificarCambioPendientes();
                    }
                };
            }).catch(console.error);
        },

        /**
         * Cuenta cuántos registros hay pendientes.
         */
        contarPendientes: function() {
            try {
                const cached = sessionStorage.getItem('enc_cache');
                return cached ? JSON.parse(cached).length : 0;
            } catch { return 0; }
        },

        /**
         * Elimina todos los registros (llamado tras sincronización exitosa).
         */
        limpiarSincronizadas: function() {
            abrirDB().then(base => {
                const tx    = base.transaction(CONFIG.STORE_PENDIENTES, 'readwrite');
                tx.objectStore(CONFIG.STORE_PENDIENTES).clear();
                try { sessionStorage.removeItem('enc_cache'); } catch {}
                notificarCambioPendientes();
            }).catch(console.error);
        },

        _actualizarCache: function() {
            this.obtenerTodasAsync().catch(console.error);
        },

        /**
         * Inicializa la caché de sesión al cargar la página.
         */
        inicializar: async function() {
            try {
                await this.obtenerTodasAsync();
                notificarCambioPendientes();
            } catch (err) {
                console.warn('[OfflineDB] Error inicializando:', err);
            }
        },
    };
})();

// Inicializar caché al cargar la página
if (OfflineDB.inicializar) {
    OfflineDB.inicializar();
}

// ════════════════════════════════════════════════════════════════════
// SyncWS — Sincronización via WebSocket (req. 8)
// ════════════════════════════════════════════════════════════════════
const SyncWS = {
    sincronizar: async function(encuestas) {
        const token = Auth.getToken();
        if (!token) throw new Error('Sin token JWT');

        const limpias = encuestas.map(({ _savedAt, _localId, ...resto }) => resto);

        const res = await fetch('/api/encuestas/sync', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify(limpias)
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.mensaje || 'Error sincronizando');
        }

        return await res.json();
    }
};
// Cliente WebSocket para eventos realtime de encuestas (sincronización y CRUD)
const RealtimeWS = (() => {
    const FORCE_POLLING_ONLY = true;
    let ws = null;
    let onEncuestasActualizadas = null;
    let reconnectTimer = null;
    let fallbackTimer = null;
    let closedManually = false;

    function arrancarFallback() {
        if (fallbackTimer || typeof onEncuestasActualizadas !== 'function') return;
        onEncuestasActualizadas({ tipo: 'ENCUESTAS_ACTUALIZADAS', origen: 'polling-init' });
        fallbackTimer = setInterval(() => {
            onEncuestasActualizadas({ tipo: 'ENCUESTAS_ACTUALIZADAS', origen: 'polling' });
        }, 4000);
    }

    function detenerFallback() {
        if (fallbackTimer) {
            clearInterval(fallbackTimer);
            fallbackTimer = null;
        }
    }

    function programarReconexion() {
        if (closedManually || reconnectTimer) return;
        reconnectTimer = setTimeout(() => {
            reconnectTimer = null;
            conectar();
        }, 2500);
    }

    function conectar() {
        if (FORCE_POLLING_ONLY) {
            arrancarFallback();
            return;
        }
        if (!Auth.estaAutenticado()) {
            arrancarFallback();
            return;
        }
        if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return;

        try {
            const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
            ws = new WebSocket(`${proto}://${window.location.host}${CONFIG.WS_PATH}`);
        } catch {
            arrancarFallback();
            programarReconexion();
            return;
        }

        ws.onopen = () => {
            const token = Auth.getToken();
            if (!token) {
                ws.close(1008, 'Sin token');
                return;
            }
            detenerFallback();
            ws.send(JSON.stringify({ tipo: 'AUTH', token }));
        };

        ws.onmessage = (event) => {
            let msg;
            try { msg = JSON.parse(event.data); } catch { return; }

            if (msg.tipo === 'ENCUESTAS_ACTUALIZADAS' && typeof onEncuestasActualizadas === 'function') {
                onEncuestasActualizadas(msg);
            }
        };

        ws.onerror = () => {
            arrancarFallback();
        };

        ws.onclose = () => {
            ws = null;
            arrancarFallback();
            programarReconexion();
        };
    }

    return {
        suscribirEncuestas: function(callback) {
            onEncuestasActualizadas = callback;
            closedManually = false;
            conectar();
        },
        detener: function() {
            closedManually = true;
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }
            detenerFallback();
            if (ws) {
                try { ws.close(1000, 'Cierre de vista'); } catch {}
                ws = null;
            }
        }
    };
})();

window.SyncWS = SyncWS;
window.RealtimeWS = RealtimeWS;

// ════════════════════════════════════════════════════════════════════
// toast() — Notificaciones visuales
// ════════════════════════════════════════════════════════════════════
function toast(mensaje, tipo = 'info', duracion = 4000) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const iconos = { success: '✓', error: '✕', warning: '⚠', info: 'ℹ' };

    const el = document.createElement('div');
    el.className = `toast toast-${tipo}`;
    el.innerHTML = `
    <span class="toast-icon">${iconos[tipo] || 'ℹ'}</span>
    <span>${mensaje}</span>`;

    container.appendChild(el);

    setTimeout(() => {
        el.classList.add('removing');
        setTimeout(() => el.remove(), 220);
    }, duracion);
}

// ════════════════════════════════════════════════════════════════════
// Service Worker — registro (el sw.js maneja la lógica offline)
// ════════════════════════════════════════════════════════════════════
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/js/sw.js')
            .then(reg => console.log('[SW] Registrado, scope:', reg.scope))
            .catch(err => console.warn('[SW] Error al registrar:', err));
    });
}