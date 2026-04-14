package edu.pucmm.eict.main;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.pucmm.eict.main.configuracion.JwtUtil;
import edu.pucmm.eict.main.configuracion.MongoConfig;
import edu.pucmm.eict.main.controllers.AuthController;
import edu.pucmm.eict.main.controllers.EncuestaController;
import edu.pucmm.eict.main.controllers.SyncWebSocketHandler;
import edu.pucmm.eict.main.controllers.UsuarioController;
import edu.pucmm.eict.main.grpc.GrpcServerManager;
import edu.pucmm.eict.main.modelos.Rol;
import edu.pucmm.eict.main.servicios.EncuestaService;
import edu.pucmm.eict.main.servicios.UsuarioService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.HandlerType;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import io.javalin.rendering.template.JavalinThymeleaf;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Main {

    public static void main(String[] args) {

        // Carga opcional de secrets desde .env local (no versionado).
        cargarSecretsDesdeDotEnv();

        // ---------------------------------------------------------------
        // 1. MongoDB + Servicios
        // ---------------------------------------------------------------
        var db              = MongoConfig.getInstance().getBaseDatos();
        var usuarioService  = new UsuarioService(db);
        var encuestaService = new EncuestaService(db);

        GrpcServerManager grpcServer;
        try {
            grpcServer = new GrpcServerManager(encuestaService);
            grpcServer.start();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo iniciar el servidor gRPC", e);
        }

        try {
            usuarioService.inicializarAdminDefecto();
        } catch (Exception e) {
            System.err.println("[Main] No se pudo inicializar el admin por defecto: " + e.getMessage());
        }

        // ---------------------------------------------------------------
        // 2. Controllers y handlers
        // ---------------------------------------------------------------
        var authController     = new AuthController(usuarioService);
        var encuestaController = new EncuestaController(encuestaService);
        var usuarioController  = new UsuarioController(usuarioService);
        var syncWsHandler      = new SyncWebSocketHandler(encuestaService);

        // ---------------------------------------------------------------
        // 3. Jackson — Instant como ISO-8601
        // ---------------------------------------------------------------
        var bsonModule = new SimpleModule();
        bsonModule.addSerializer(ObjectId.class, new com.fasterxml.jackson.databind.JsonSerializer<>() {
            @Override
            public void serialize(ObjectId value, com.fasterxml.jackson.core.JsonGenerator gen,
                                  com.fasterxml.jackson.databind.SerializerProvider serializers) throws IOException {
                gen.writeString(value != null ? value.toHexString() : null);
            }
        });

        var mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(bsonModule)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ---------------------------------------------------------------
        // 4. Thymeleaf — resuelve plantillas desde /templates/*.html
        // ---------------------------------------------------------------
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false); // cambiar a true en producción

        var engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);

        // ---------------------------------------------------------------
        // 5. Javalin
        // ---------------------------------------------------------------
        var app = Javalin.create(config -> {

            config.staticFiles.add("/css", Location.CLASSPATH);
            config.staticFiles.add("/js", Location.CLASSPATH);
            config.staticFiles.add("/templates", Location.CLASSPATH);

            // JSON mapper
            config.jsonMapper(new JavalinJackson(mapper, false));

            // Thymeleaf como motor de plantillas
            config.fileRenderer(new JavalinThymeleaf(engine));

            // CORS no es necesario para las vistas servidas por el mismo origen.
            // El proxy gRPC-Web se encarga de su propia política de CORS.

            // Log de requests
            config.bundledPlugins.enableDevLogging();

            // ----------------------------------------------------------
            // Middleware JWT — solo aplica a rutas /api/**
            // Las rutas de vistas son libres; el JS verifica la sesión
            // en el cliente y redirige a login si no hay token.
            // ----------------------------------------------------------
            config.routes.before(ctx -> {
                String path = ctx.path();
                HandlerType method = ctx.method();

                // Solo interceptar rutas de API
                if (!path.startsWith("/api/")) return;

                // Dejar pasar el preflight CORS para PUT/DELETE/PATCH
                if (method == HandlerType.OPTIONS) return;

                // Rutas API públicas — sin token
                if (path.equals("/api/auth/login") ||
                        path.equals("/api/auth/registro")) return;

                // Todo lo demás bajo /api/ requiere JWT
                String header = ctx.header("Authorization");
                if (header == null || !header.startsWith("Bearer ")) {
                    ctx.status(HttpStatus.UNAUTHORIZED)
                            .json(Map.of("mensaje", "Token JWT requerido"))
                            .skipRemainingHandlers();
                    return;
                }
                try {
                    var jwt = JwtUtil.verificarToken(header.substring(7));

                    // Rutas solo ADMIN
                    if (path.startsWith("/api/usuarios") &&
                            !JwtUtil.extraerRol(jwt).equals(Rol.ADMIN.name())) {
                        ctx.status(HttpStatus.FORBIDDEN)
                                .json(Map.of("mensaje", "Acceso denegado: se requiere rol ADMIN"))
                                .skipRemainingHandlers();
                    }
                } catch (JWTVerificationException e) {
                    ctx.status(HttpStatus.UNAUTHORIZED)
                            .json(Map.of("mensaje", "Token inválido o expirado"))
                            .skipRemainingHandlers();
                }
            });

            // ----------------------------------------------------------
            // Vistas — Thymeleaf con rutas limpias
            // ----------------------------------------------------------

            config.routes.get("/css/style.css", ctx -> servirRecurso(ctx, "/css/style.css", "text/css; charset=UTF-8"));
            config.routes.get("/js/app.js", ctx -> servirRecurso(ctx, "/js/app.js", "application/javascript; charset=UTF-8"));
            config.routes.get("/js/sw.js", ctx -> servirRecurso(ctx, "/js/sw.js", "application/javascript; charset=UTF-8"));
            config.routes.get("/js/encuesta_grpc.js", ctx -> servirRecurso(ctx, "/js/encuesta_grpc.js", "application/javascript; charset=UTF-8"));

            // Raíz → redirige a /login
            config.routes.get("/", ctx -> ctx.redirect("/login"));

            // Páginas públicas
            config.routes.get("/login",    ctx -> ctx.render("login"));
            config.routes.get("/registro", ctx -> ctx.render("registro"));

            // Páginas protegidas
            // (Auth.requerirAuth() en app.js redirige al cliente si no hay token)
            config.routes.get("/encuesta",    ctx -> ctx.render("encuesta"));
            config.routes.get("/bandeja",     ctx -> ctx.render("bandeja"));
            config.routes.get("/listado",     ctx -> ctx.render("listado"));
            config.routes.get("/dashboard",   ctx -> ctx.render("dashboard"));
            config.routes.get("/mapa",        ctx -> ctx.render("mapa"));
            config.routes.get("/usuarios",    ctx -> ctx.render("usuarios"));
            config.routes.get("/cliente-rest",ctx -> ctx.render("cliente-rest"));
            config.routes.get("/cliente-grpc",ctx -> ctx.render("cliente-grpc"));

            // ----------------------------------------------------------
            // API Auth — pública
            // ----------------------------------------------------------
            config.routes.post("/api/auth/login",    authController::login);
            config.routes.post("/api/auth/registro", authController::registro);

            // ----------------------------------------------------------
            // API Encuestas — requieren JWT
            // ----------------------------------------------------------
            config.routes.get("/api/encuestas",              encuestaController::listarTodas);
            config.routes.post("/api/encuestas",             encuestaController::crear);
            config.routes.post("/api/encuestas/sync",        encuestaController::sincronizar);
            config.routes.get("/api/encuestas/usuario/{id}", encuestaController::listarPorUsuario);
            config.routes.get("/api/encuestas/{id}",         encuestaController::buscarPorId);
            config.routes.put("/api/encuestas/{id}",         encuestaController::actualizar);
            config.routes.delete("/api/encuestas/{id}",      encuestaController::eliminar);

            // ----------------------------------------------------------
            // API Usuarios — solo ADMIN (validado en el middleware)
            // ----------------------------------------------------------
            config.routes.get("/api/usuarios",           usuarioController::listarTodos);
            config.routes.get("/api/usuarios/{id}",      usuarioController::buscarPorId);
            config.routes.put("/api/usuarios/{id}/rol",  usuarioController::cambiarRol);
            config.routes.delete("/api/usuarios/{id}",   usuarioController::eliminar);

            // ----------------------------------------------------------
            // WebSocket — sincronización offline (req. 8)
            // ----------------------------------------------------------
            // Deshabilitado temporalmente por inestabilidad de Jetty 12 en OPEN (error 1011).
            // config.routes.ws("/ws/sync", syncWsHandler);

            // ----------------------------------------------------------
            // Excepciones globales
            // ----------------------------------------------------------
            config.routes.exception(IllegalArgumentException.class, (e, ctx) ->
                    ctx.status(HttpStatus.BAD_REQUEST)
                            .json(Map.of("mensaje", e.getMessage()))
            );

            config.routes.exception(Exception.class, (e, ctx) -> {
                e.printStackTrace();
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(Map.of("mensaje", "Error interno del servidor"));
            });

            config.routes.error(HttpStatus.NOT_FOUND, ctx ->
                    ctx.json(Map.of("mensaje", "Recurso no encontrado"))
            );

            // Mantener mensajes específicos ya enviados por controllers/middleware.
            // Solo usar mensaje genérico si no existe cuerpo en la respuesta.
            config.routes.error(HttpStatus.UNAUTHORIZED, ctx -> {
                String actual = ctx.result();
                if (actual == null || actual.isBlank()) {
                    ctx.json(Map.of("mensaje", "No autorizado"));
                }
            });

            config.routes.error(HttpStatus.FORBIDDEN, ctx -> {
                String actual = ctx.result();
                if (actual == null || actual.isBlank()) {
                    ctx.json(Map.of("mensaje", "Acceso denegado"));
                }
            });

        }).start(leerPuerto());

        // ---------------------------------------------------------------
        // 6. Shutdown hook
        // ---------------------------------------------------------------
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Main] Apagando servidor...");
            grpcServer.stop();
            MongoConfig.getInstance().cerrar();
            app.stop();
        }));

        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println(" Servidor en  → http://localhost:" + leerPuerto());
        System.out.println(" Login        → http://localhost:" + leerPuerto() + "/login");
        System.out.println(" Encuesta     → http://localhost:" + leerPuerto() + "/encuesta");
        System.out.println(" Mapa         → http://localhost:" + leerPuerto() + "/mapa");
        System.out.println(" Listado      → http://localhost:" + leerPuerto() + "/listado");
        System.out.println(" Usuarios     → http://localhost:" + leerPuerto() + "/usuarios");
        System.out.println(" Cliente REST → http://localhost:" + leerPuerto() + "/cliente-rest");
        System.out.println(" Cliente gRPC → http://localhost:" + leerPuerto() + "/cliente-grpc");
        System.out.println(" gRPC Server  → localhost:" + grpcServer.getPort());
        System.out.println(" WebSocket    → ws://localhost:"   + leerPuerto() + "/ws/sync");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // -------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------
    private static int leerPuerto() {
        String env = System.getenv("PORT");
        try {
            return (env != null && !env.isBlank()) ? Integer.parseInt(env.trim()) : 8000;
        } catch (NumberFormatException e) {
            System.err.println("[Main] PORT inválido '" + env + "', usando 7070.");
            return 7070;
        }
    }

    private static void cargarSecretsDesdeDotEnv() {
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) return;

        try {
            for (String linea : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String l = linea.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;

                int idx = l.indexOf('=');
                if (idx <= 0) continue;

                String key = l.substring(0, idx).trim();
                String value = limpiarValorEnv(l.substring(idx + 1).trim());
                if (value.isBlank()) continue;

                if ("JWT_SECRET".equals(key)
                        && isBlank(System.getenv("JWT_SECRET"))
                        && isBlank(System.getProperty("jwt.secret"))) {
                    System.setProperty("jwt.secret", value);
                }

                if ("MONGO_URI".equals(key)
                        && isBlank(System.getenv("MONGO_URI"))
                        && isBlank(System.getProperty("mongo.uri"))) {
                    System.setProperty("mongo.uri", value);
                }
            }
        } catch (IOException e) {
            System.err.println("[Main] No se pudo leer .env: " + e.getMessage());
        }
    }

    private static String limpiarValorEnv(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    private static void servirRecurso(Context ctx, String ruta, String contentType) {
        try (InputStream in = Main.class.getResourceAsStream(ruta)) {
            if (in == null) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("mensaje", "Recurso no encontrado"));
                return;
            }
            ctx.contentType(contentType);
            ctx.result(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("mensaje", "Error leyendo recurso estático"));
        }
    }
}