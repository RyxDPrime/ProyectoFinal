package edu.pucmm.eict.main.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.pucmm.eict.main.configuracion.JwtUtil;
import edu.pucmm.eict.main.modelos.Encuesta;
import edu.pucmm.eict.main.modelos.NivelEscolar;
import edu.pucmm.eict.main.modelos.Rol;
import edu.pucmm.eict.main.servicios.EncuestaService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.bson.types.ObjectId;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller REST de encuestas.
 *
 * GET    /api/encuestas                    → listar todas (ADMIN / VISUALIZADOR)
 * GET    /api/encuestas/usuario/{id}       → listar por usuario (req. 16.1)
 * GET    /api/encuestas/{id}               → buscar una
 * POST   /api/encuestas                    → crear (req. 16.2)
 * PUT    /api/encuestas/{id}               → actualizar (req. 11)
 * DELETE /api/encuestas/{id}               → eliminar  (req. 11)
 * POST   /api/encuestas/sync               → sincronizar lote offline (req. 8)
 *
 * Todos los endpoints requieren JWT válido en el header:
 *   Authorization: Bearer <token>
 */
public class EncuestaController {

    private final EncuestaService encuestaService;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public EncuestaController(EncuestaService encuestaService) {
        this.encuestaService = encuestaService;
    }

    public void listarTodas(Context ctx) {
        DecodedJWT jwt;
        try {
            jwt = obtenerJwt(ctx);
        } catch (IllegalArgumentException | JWTVerificationException e) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("mensaje", "Token inválido o expirado"));
            return;
        }

        String usuarioId = JwtUtil.extraerUsuarioId(jwt);
        String scope = Objects.toString(ctx.queryParam("scope"), "").trim().toLowerCase();

        List<Encuesta> encuestas;
        boolean quiereTodos = "all".equals(scope);
        if ("mine".equals(scope)) {
            encuestas = encuestaService.listarPorUsuario(usuarioId);
        } else if (quiereTodos) {
            encuestas = encuestaService.listarTodas();
        } else if (esPrivilegiado(jwt)) {
            encuestas = encuestaService.listarTodas();
        } else {
            encuestas = encuestaService.listarPorUsuario(usuarioId);
        }
        ctx.status(HttpStatus.OK).json(encuestas);
    }

    public void listarPorUsuario(Context ctx) {
        DecodedJWT jwt;
        try {
            jwt = obtenerJwt(ctx);
        } catch (IllegalArgumentException | JWTVerificationException e) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("mensaje", "Token inválido o expirado"));
            return;
        }

        String usuarioId = ctx.pathParam("id");
        if (!esPrivilegiado(jwt) && !usuarioId.equals(JwtUtil.extraerUsuarioId(jwt))) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("mensaje", "Acceso denegado"));
            return;
        }

        List<Encuesta> encuestas = encuestaService.listarPorUsuario(usuarioId);
        ctx.status(HttpStatus.OK).json(encuestas);
    }

    public void buscarPorId(Context ctx) {
        DecodedJWT jwt;
        try {
            jwt = obtenerJwt(ctx);
        } catch (IllegalArgumentException | JWTVerificationException e) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("mensaje", "Token inválido o expirado"));
            return;
        }

        String id = ctx.pathParam("id");
        encuestaService.buscarPorId(id)
                .ifPresentOrElse(e -> {
                            if (esPrivilegiado(jwt) || (e.getUsuarioId() != null && JwtUtil.extraerUsuarioId(jwt).equals(e.getUsuarioId().toHexString()))) {
                                ctx.status(HttpStatus.OK).json(e);
                            } else {
                                ctx.status(HttpStatus.FORBIDDEN).json(Map.of("mensaje", "Acceso denegado"));
                            }
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND)
                                .json(Map.of("mensaje", "Encuesta no encontrada"))
                );
    }

    public void crear(Context ctx) {
        var body = ctx.bodyAsClass(EncuestaRequest.class);

        if (!fotoValida(body.fotoBase64)) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("mensaje", "La fotografía es obligatoria"));
            return;
        }

        DecodedJWT jwt;
        try {
            jwt = obtenerJwt(ctx);
        } catch (IllegalArgumentException | JWTVerificationException e) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("mensaje", "Token inválido o expirado"));
            return;
        }
        String usuarioId     = JwtUtil.extraerUsuarioId(jwt);
        String usuarioNombre = JwtUtil.extraerEmail(jwt);

        NivelEscolar nivel;
        try {
            nivel = EncuestaService.parsearNivel(body.nivelEscolar);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("mensaje", e.getMessage()));
            return;
        }

        Encuesta encuesta = new Encuesta(
                body.nombre,
                body.sector,
                nivel,
                body.edad,
                body.genero,
                body.tipoEscuela,
                body.estadoEstudio,
                new ObjectId(usuarioId),
                usuarioNombre,
                body.latitud,
                body.longitud,
                body.fotoBase64
        );

        Encuesta creada = encuestaService.crear(encuesta);
        SyncWebSocketHandler.notificarCambioEncuestas("CREAR", 1);
        ctx.status(HttpStatus.CREATED).json(creada);
    }

    public void actualizar(Context ctx) {
        String id   = ctx.pathParam("id");
        var    body = ctx.bodyAsClass(EncuestaRequest.class);

        if (!fotoValida(body.fotoBase64)) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("mensaje", "La fotografía es obligatoria"));
            return;
        }

        DecodedJWT jwt;
        try {
            jwt = obtenerJwt(ctx);
        } catch (IllegalArgumentException | JWTVerificationException e) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("mensaje", "Token inválido o expirado"));
            return;
        }
        String usuarioId = JwtUtil.extraerUsuarioId(jwt);

        NivelEscolar nivel;
        try {
            nivel = EncuestaService.parsearNivel(body.nivelEscolar);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("mensaje", e.getMessage()));
            return;
        }

        Encuesta datos = new Encuesta();
        datos.setNombre(body.nombre);
        datos.setSector(body.sector);
        datos.setNivelEscolar(nivel);
        datos.setEdad(body.edad);
        datos.setGenero(body.genero);
        datos.setTipoEscuela(body.tipoEscuela);
        datos.setEstadoEstudio(body.estadoEstudio);
        datos.setUsuarioId(new ObjectId(usuarioId));
        datos.setLatitud(body.latitud);
        datos.setLongitud(body.longitud);
        datos.setFotoBase64(body.fotoBase64);
        datos.setCreadoEn(Instant.now());

        boolean actualizado = encuestaService.actualizar(id, usuarioId, datos);

        if (actualizado) {
            SyncWebSocketHandler.notificarCambioEncuestas("ACTUALIZAR", 1);
            ctx.status(HttpStatus.OK).json(Map.of("mensaje", "Encuesta actualizada"));
        } else {
            ctx.status(HttpStatus.NOT_FOUND)
                    .json(Map.of("mensaje", "Encuesta no encontrada o sin permiso"));
        }
    }

    public void eliminar(Context ctx) {
        String id        = ctx.pathParam("id");
        DecodedJWT jwt;
        try {
            jwt = obtenerJwt(ctx);
        } catch (IllegalArgumentException | JWTVerificationException e) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("mensaje", "Token inválido o expirado"));
            return;
        }
        String usuarioId = JwtUtil.extraerUsuarioId(jwt);
        String rol       = JwtUtil.extraerRol(jwt);

        boolean eliminado = rol.equals(Rol.ADMIN.name())
                ? encuestaService.eliminarAdmin(id)
                : encuestaService.eliminar(id, usuarioId);

        if (eliminado) {
            SyncWebSocketHandler.notificarCambioEncuestas("ELIMINAR", 1);
            ctx.status(HttpStatus.OK).json(Map.of("mensaje", "Encuesta eliminada"));
        } else {
            ctx.status(HttpStatus.NOT_FOUND)
                    .json(Map.of("mensaje", "Encuesta no encontrada o sin permiso"));
        }
    }

    // -------------------------------------------------------------------
    // POST /api/encuestas/sync  — lote offline (req. 8)
    // -------------------------------------------------------------------
    public void sincronizar(Context ctx) {
        try {
            // Extraer usuario del token
            var jwt = obtenerJwt(ctx);
            String usuarioId = JwtUtil.extraerUsuarioId(jwt);
            String usuarioNombre = JwtUtil.extraerEmail(jwt);

            String body = ctx.body();
            List<Encuesta> lote = mapper.readValue(body, new TypeReference<>() {});

            // Asignar usuario a cada encuesta del lote
            for (Encuesta e : lote) {
                if (!fotoValida(e.getFotoBase64())) {
                    ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("mensaje", "Todas las encuestas deben incluir fotografía"));
                    return;
                }
                if (e.getUsuarioId() == null) {
                    e.setUsuarioId(new ObjectId(usuarioId));
                }
                if (e.getUsuarioNombre() == null || e.getUsuarioNombre().isBlank()) {
                    e.setUsuarioNombre(usuarioNombre);
                }
            }

            int total = encuestaService.sincronizarLote(lote);

            SyncWebSocketHandler.notificarCambioEncuestas("SYNC", total);
            ctx.status(HttpStatus.OK).json(Map.of(
                    "mensaje", "Sincronización completada",
                    "procesadas", total
            ));
        } catch (IllegalArgumentException | JWTVerificationException e) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of(
                    "mensaje", "Token inválido o expirado"
            ));
        } catch (Exception e) {
            System.err.println("[SYNC] ERROR: " + e.getMessage());
            System.err.println("[SYNC] Detalle: " + e);
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                    "mensaje", "Error: " + e.getMessage()
            ));
        }
    }

    // -------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------
    private DecodedJWT obtenerJwt(Context ctx) {
        return JwtUtil.verificarToken(extraerToken(ctx));
    }

    private boolean esPrivilegiado(DecodedJWT jwt) {
        String rol = JwtUtil.extraerRol(jwt);
        return Rol.ADMIN.name().equals(rol) || Rol.VISUALIZADOR.name().equals(rol);
    }

    private String extraerToken(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token JWT requerido");
        }
        return header.substring(7);
    }

    private boolean fotoValida(String base64) {
        return base64 != null && base64.startsWith("data:image/") && base64.contains(";base64,");
    }

    // -------------------------------------------------------------------
    // DTO interno
    // -------------------------------------------------------------------
    public static class EncuestaRequest {
        public String nombre;
        public String sector;
        public String nivelEscolar;
        public Integer edad;
        public String genero;
        public String tipoEscuela;
        public String estadoEstudio;
        public Double latitud;
        public Double longitud;
        public String fotoBase64;
    }
}