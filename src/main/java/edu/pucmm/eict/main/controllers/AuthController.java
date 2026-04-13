package edu.pucmm.eict.main.controllers;

import edu.pucmm.eict.main.configuracion.JwtUtil;
import edu.pucmm.eict.main.modelos.Rol;
import edu.pucmm.eict.main.modelos.Usuario;
import edu.pucmm.eict.main.servicios.UsuarioService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Map;
import java.util.Optional;

/**
 * Controller de autenticación.
 *
 * POST /api/auth/login    → devuelve JWT
 * POST /api/auth/registro → crea usuario (solo ADMIN puede asignar rol)
 */
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // -------------------------------------------------------------------
    // POST /api/auth/login
    // -------------------------------------------------------------------
    public void login(Context ctx) {
        LoginRequest body;
        try {
            body = ctx.bodyAsClass(LoginRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("mensaje", "Solicitud de login inválida"));
            return;
        }

        if (body == null || body.email == null || body.email.isBlank() || body.password == null || body.password.isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("mensaje", "Email y contraseña son obligatorios"));
            return;
        }

        Optional<Usuario> resultado = usuarioService.autenticar(body.email, body.password);

        if (resultado.isEmpty()) {
            ctx.status(HttpStatus.UNAUTHORIZED)
                    .json(Map.of("mensaje", "Credenciales incorrectas"));
            return;
        }

        Usuario u     = resultado.get();
        String  token = JwtUtil.generarToken(
                u.getId().toHexString(),
                u.getEmail(),
                u.getRol().name()
        );

        ctx.status(HttpStatus.OK).json(Map.of(
                "token",      token,
                "nombre",     u.getNombre(),
                "rol",        u.getRol().name(),
                "usuarioId",  u.getId().toHexString()
        ));
    }

    // -------------------------------------------------------------------
    // POST /api/auth/registro
    // -------------------------------------------------------------------
    public void registro(Context ctx) {
        RegistroRequest body;
        try {
            body = ctx.bodyAsClass(RegistroRequest.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("mensaje", "Solicitud de registro inválida"));
            return;
        }

        if (body == null || body.nombre == null || body.nombre.isBlank() ||
                body.email == null || body.email.isBlank() ||
                body.password == null || body.password.isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("mensaje", "Nombre, email y contraseña son obligatorios"));
            return;
        }

        Rol rol;
        try {
            rol = esAdminSolicitante(ctx) && body.rol != null
                    ? Rol.valueOf(body.rol.toUpperCase())
                    : Rol.ENCUESTADOR;
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("mensaje", "Rol inválido: " + body.rol));
            return;
        }

        try {
            Usuario u = usuarioService.crear(body.nombre, body.email, body.password, rol);
            ctx.status(HttpStatus.CREATED).json(Map.of(
                    "mensaje",    "Usuario creado exitosamente",
                    "usuarioId",  u.getId().toHexString(),
                    "email",      u.getEmail(),
                    "rol",        u.getRol().name()
            ));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.CONFLICT)
                    .json(Map.of("mensaje", e.getMessage()));
        }
    }


    private boolean esAdminSolicitante(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }

        try {
            var jwt = JwtUtil.verificarToken(header.substring(7));
            return Rol.ADMIN.name().equals(JwtUtil.extraerRol(jwt));
        } catch (Exception e) {
            return false;
        }
    }
    // -------------------------------------------------------------------
    // DTOs internos
    // -------------------------------------------------------------------
    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static class RegistroRequest {
        public String nombre;
        public String email;
        public String password;
        public String rol; // opcional, default ENCUESTADOR
    }
}