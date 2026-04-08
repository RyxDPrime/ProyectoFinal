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

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    // -------------------------------------------------------------------
    // GET /api/encuestas
    // -------------------------------------------------------------------
    public void listarTodas(Context ctx) {
        List<Encuesta> encuestas = encuestaService.listarTodas();
        ctx.status(HttpStatus.OK).json(encuestas);
    }

    // -------------------------------------------------------------------
    // GET /api/encuestas/usuario/{id}
    // -------------------------------------------------------------------
    public void listarPorUsuario(Context ctx) {
        String usuarioId = ctx.pathParam("id");
        List<Encuesta> encuestas = encuestaService.listarPorUsuario(usuarioId);
        ctx.status(HttpStatus.OK).json(encuestas);
    }

    // -------------------------------------------------------------------
    // GET /api/encuestas/{id}
    // -------------------------------------------------------------------
    public void buscarPorId(Context ctx) {
        String id = ctx.pathParam("id");
        encuestaService.buscarPorId(id)
                .ifPresentOrElse(
                        e  -> ctx.status(HttpStatus.OK).json(e),
                        () -> ctx.status(HttpStatus.NOT_FOUND)
                                .json(Map.of("mensaje", "Encuesta no encontrada"))
                );
    }

    // -------------------------------------------------------------------
    // POST /api/encuestas
    // -------------------------------------------------------------------
    public void crear(Context ctx) {
        var body = ctx.bodyAsClass(EncuestaRequest.class);

        // Extraer usuario del token
        var jwt       = JwtUtil.verificarToken(extraerToken(ctx));
        String usuarioId     = JwtUtil.extraerUsuarioId(jwt);
        String usuarioNombre = JwtUtil.extraerEmail(jwt); // se usa email como nombre en el token

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

    // -------------------------------------------------------------------
    // PUT /api/encuestas/{id}
    // -------------------------------------------------------------------
    public void actualizar(Context ctx) {
        String id   = ctx.pathParam("id");
        var    body = ctx.bodyAsClass(EncuestaRequest.class);

        var    jwt       = JwtUtil.verificarToken(extraerToken(ctx));
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

    // -------------------------------------------------------------------
    // DELETE /api/encuestas/{id}
    // -------------------------------------------------------------------
    public void eliminar(Context ctx) {
        String id        = ctx.pathParam("id");
        var    jwt       = JwtUtil.verificarToken(extraerToken(ctx));
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
            var jwt = JwtUtil.verificarToken(extraerToken(ctx));
            String usuarioId = JwtUtil.extraerUsuarioId(jwt);
            String usuarioNombre = JwtUtil.extraerEmail(jwt);

            String body = ctx.body();
            List<Encuesta> lote = mapper.readValue(body, new TypeReference<>() {});

            // Asignar usuario a cada encuesta del lote
            for (Encuesta e : lote) {
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
    private String extraerToken(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token JWT requerido");
        }
        return header.substring(7);
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