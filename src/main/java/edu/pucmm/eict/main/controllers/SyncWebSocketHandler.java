package edu.pucmm.eict.main.controllers;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.pucmm.eict.main.configuracion.JwtUtil;
import edu.pucmm.eict.main.modelos.Encuesta;
import edu.pucmm.eict.main.servicios.EncuestaService;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsErrorContext;
import io.javalin.websocket.WsMessageContext;
import io.javalin.websocket.WsContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.time.Instant;

/**
 * Handler WebSocket para sincronización - VERSIÓN CORREGIDA PARA JETTY 12
 *
 * CAMBIOS CLAVE:
 * - Evitar acceso a atributos/request en onConnect (bug Jetty 12 OPEN)
 * - Rastrear sesiones por identidad de Session WebSocket
 * - No acceder a upgradeRequest en ningún momento
 */
public class SyncWebSocketHandler implements Consumer<WsConfig> {

    private static class SesionWs {
        final String id;
        final WsContext ctx;
        DecodedJWT jwt;
        boolean autenticado = false;

        SesionWs(String id, WsContext ctx) {
            this.id = id;
            this.ctx = ctx;
        }
    }

    private static final ConcurrentHashMap<String, SesionWs> sesionesActivas = new ConcurrentHashMap<>();

    private static final ObjectMapper BROADCAST_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final EncuestaService encuestaService;
    private final ObjectMapper    mapper;

    public SyncWebSocketHandler(EncuestaService encuestaService) {
        this.encuestaService = encuestaService;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void accept(WsConfig ws) {
        ws.onMessage(this::onMessage);
        ws.onClose(this::onClose);
        ws.onError(this::onError);
    }

    private void onMessage(WsMessageContext ctx) {
        Object sessionKey = ctx.session;
        String sessionId = Integer.toHexString(System.identityHashCode(sessionKey));
        SesionWs sesion = sesionesActivas.computeIfAbsent(sessionId, id -> new SesionWs(id, ctx));

        String raw = ctx.message();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = mapper.readValue(raw, Map.class);
            String tipo = (String) msg.getOrDefault("tipo", "");

            switch (tipo.toUpperCase()) {
                case "AUTH" -> handleAuth(ctx, sesion, msg);
                case "SYNC" -> handleSync(ctx, sesion, msg);
                default -> enviarError(ctx, "Tipo desconocido: '" + tipo + "'");
            }
        } catch (Exception e) {
            System.err.println("[WS] Error procesando mensaje de " + sessionId + ": " + e.getMessage());
            enviarError(ctx, "Error: " + e.getMessage());
        }
    }

    private void handleAuth(WsMessageContext ctx, SesionWs sesion, Map<String, Object> msg) {
        String token = (String) msg.get("token");
        if (token == null || token.isBlank()) {
            enviarError(ctx, "Token JWT requerido");
            ctx.closeSession(1008, "Token requerido");
            return;
        }

        try {
            DecodedJWT jwt = JwtUtil.verificarToken(token);
            sesion.jwt = jwt;
            sesion.autenticado = true;

            ctx.send(mapper.writeValueAsString(Map.of(
                    "tipo", "AUTH_OK",
                    "usuarioId", JwtUtil.extraerUsuarioId(jwt),
                    "rol", JwtUtil.extraerRol(jwt)
            )));

            System.out.println("[WS] Autenticado: " + sesion.id + " → " + JwtUtil.extraerEmail(jwt));

        } catch (JWTVerificationException e) {
            enviarError(ctx, "Token inválido o expirado");
            ctx.closeSession(1008, "Token inválido");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleSync(WsMessageContext ctx, SesionWs sesion, Map<String, Object> msg) {
        if (!sesion.autenticado || sesion.jwt == null) {
            enviarError(ctx, "No autenticado. Envía AUTH primero.");
            return;
        }

        Object encuestasRaw = msg.get("encuestas");
        if (encuestasRaw == null) {
            enviarError(ctx, "Campo 'encuestas' requerido");
            return;
        }

        try {
            List<Encuesta> lote = mapper.convertValue(encuestasRaw, new TypeReference<>() {});

            String usuarioId = JwtUtil.extraerUsuarioId(sesion.jwt);
            lote.forEach(e -> {
                if (e.getUsuarioId() == null) {
                    e.setUsuarioId(new org.bson.types.ObjectId(usuarioId));
                }
                if (e.getUsuarioNombre() == null || e.getUsuarioNombre().isBlank()) {
                    e.setUsuarioNombre(JwtUtil.extraerEmail(sesion.jwt));
                }
            });

            int procesadas = encuestaService.sincronizarLote(lote);
            notificarCambioEncuestas("SYNC", procesadas);

            ctx.send(mapper.writeValueAsString(Map.of(
                    "tipo", "SYNC_OK",
                    "procesadas", procesadas,
                    "mensaje", procesadas + " encuesta(s) sincronizada(s)"
            )));

            System.out.println("[WS] Sincronizadas " + procesadas + " para: " + sesion.id);

        } catch (Exception e) {
            System.err.println("[WS] Error sincronizando: " + e.getMessage());
            enviarError(ctx, "Error sincronizando: " + e.getMessage());
        }
    }

    private void onClose(WsCloseContext ctx) {
        Object sessionKey = ctx.session;
        String sessionId = Integer.toHexString(System.identityHashCode(sessionKey));
        sesionesActivas.remove(sessionId);
        System.out.println("[WS] Desconectado: " + sessionId + " (código " + ctx.status() + ")");
    }

    private void onError(WsErrorContext ctx) {
        Object sessionKey = ctx.session;
        String sessionId = Integer.toHexString(System.identityHashCode(sessionKey));
        sesionesActivas.remove(sessionId);
        System.err.println("[WS] Error en sesión " + sessionId + ": " +
                (ctx.error() != null ? ctx.error().getMessage() : "desconocido"));
    }

    // -------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------
    private void enviarError(WsMessageContext ctx, String mensaje) {
        try {
            ctx.send(mapper.writeValueAsString(Map.of("tipo", "ERROR", "mensaje", mensaje)));
        } catch (Exception e) {
            System.err.println("[WS] No se pudo enviar error: " + e.getMessage());
        }
    }

    // Notificación broadcast (opcional)
    public static void notificarCambioEncuestas(String accion, int cantidad) {
        try {
            String payload = BROADCAST_MAPPER.writeValueAsString(Map.of(
                    "tipo", "ENCUESTAS_ACTUALIZADAS",
                    "accion", accion,
                    "cantidad", cantidad,
                    "timestamp", Instant.now().toString()
            ));

            sesionesActivas.values().forEach(sesion -> {
                if (sesion.autenticado) {
                    try {
                        sesion.ctx.send(payload);
                    } catch (Exception e) {
                        // Ignorar errores de envío
                    }
                }
            });
            long autenticadas = sesionesActivas.values().stream().filter(s -> s.autenticado).count();
            System.out.println("[WS] Broadcast " + accion + " (" + cantidad + ") enviado a " + autenticadas + " sesión(es)");
        } catch (Exception e) {
            System.err.println("[WS] Error notificando: " + e.getMessage());
        }
    }
}