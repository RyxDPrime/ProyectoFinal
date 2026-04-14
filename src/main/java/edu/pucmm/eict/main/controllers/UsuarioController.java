package edu.pucmm.eict.main.controllers;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import edu.pucmm.eict.main.configuracion.JwtUtil;
import edu.pucmm.eict.main.modelos.Rol;
import edu.pucmm.eict.main.modelos.Usuario;
import edu.pucmm.eict.main.servicios.UsuarioService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Controller de gestión de usuarios.
 * Todos los endpoints requieren rol ADMIN.
 *
 * GET    /api/usuarios          → listar todos
 * GET    /api/usuarios/{id}     → buscar uno
 * PUT    /api/usuarios/{id}/rol → cambiar rol
 * DELETE /api/usuarios/{id}     → eliminar
 */
public class UsuarioController {

    private static final String ADMIN_SUPERIOR_EMAIL = "admin@pucmm.edu.do";

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    public void listarTodos(Context ctx) {
        List<Usuario> usuarios = usuarioService.listarTodos();
        usuarios.forEach(u -> u.setPasswordHash(null));
        ctx.status(HttpStatus.OK).json(usuarios);
    }

    public void buscarPorId(Context ctx) {
        String id = ctx.pathParam("id");
        usuarioService.buscarPorId(id)
                .ifPresentOrElse(u -> {
                            u.setPasswordHash(null);
                            ctx.status(HttpStatus.OK).json(u);
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND)
                                .json(Map.of("mensaje", "Usuario no encontrado"))
                );
    }

    public void cambiarRol(Context ctx) {
        String id   = ctx.pathParam("id");
        var    body = ctx.bodyAsClass(CambiarRolRequest.class);

        DecodedJWT jwt;
        try {
            jwt = obtenerJwt(ctx);
        } catch (IllegalArgumentException | JWTVerificationException e) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("mensaje", "Token inválido o expirado"));
            return;
        }
        String actorId = JwtUtil.extraerUsuarioId(jwt);

        var target = usuarioService.buscarPorId(id);
        if (target.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("mensaje", "Usuario no encontrado"));
            return;
        }

        if (esAdminSuperior(target.get())) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("mensaje", "El ADMIN superior no puede ser modificado"));
            return;
        }

        if (target.get().getId() != null && target.get().getId().toHexString().equals(actorId)) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("mensaje", "No puedes modificar tu propia cuenta"));
            return;
        }

        Rol nuevoRol;
        try {
            nuevoRol = Rol.valueOf(body.rol.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("mensaje", "Rol inválido: " + body.rol));
            return;
        }

        boolean actualizado = usuarioService.cambiarRol(id, nuevoRol);
        if (actualizado) {
            ctx.status(HttpStatus.OK).json(Map.of("mensaje", "Rol actualizado a " + nuevoRol));
        } else {
            ctx.status(HttpStatus.NOT_FOUND)
                    .json(Map.of("mensaje", "Usuario no encontrado"));
        }
    }

    public void eliminar(Context ctx) {
        String id = ctx.pathParam("id");

        DecodedJWT jwt;
        try {
            jwt = obtenerJwt(ctx);
        } catch (IllegalArgumentException | JWTVerificationException e) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("mensaje", "Token inválido o expirado"));
            return;
        }
        String actorId = JwtUtil.extraerUsuarioId(jwt);

        var target = usuarioService.buscarPorId(id);
        if (target.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("mensaje", "Usuario no encontrado"));
            return;
        }

        if (esAdminSuperior(target.get())) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("mensaje", "El ADMIN superior no puede ser eliminado"));
            return;
        }

        if (target.get().getId() != null && target.get().getId().toHexString().equals(actorId)) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("mensaje", "No puedes eliminar tu propia cuenta"));
            return;
        }

        boolean borrado = usuarioService.eliminar(id);

        if (borrado) {
            ctx.status(HttpStatus.OK).json(Map.of("mensaje", "Usuario eliminado"));
        } else {
            ctx.status(HttpStatus.NOT_FOUND)
                    .json(Map.of("mensaje", "Usuario no encontrado"));
        }
    }

    public static class CambiarRolRequest {
        public String rol;
    }

    private DecodedJWT obtenerJwt(Context ctx) {
        return JwtUtil.verificarToken(extraerToken(ctx));
    }

    private String extraerToken(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token JWT requerido");
        }
        return header.substring(7);
    }

    private boolean esAdminSuperior(Usuario usuario) {
        return usuario != null && ADMIN_SUPERIOR_EMAIL.equals(usuario.getEmail());
    }
}