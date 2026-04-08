package edu.pucmm.eict.main.controllers;

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

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // -------------------------------------------------------------------
    // GET /api/usuarios
    // -------------------------------------------------------------------
    public void listarTodos(Context ctx) {
        List<Usuario> usuarios = usuarioService.listarTodos();
        // No exponer el passwordHash en la respuesta
        usuarios.forEach(u -> u.setPasswordHash(null));
        ctx.status(HttpStatus.OK).json(usuarios);
    }

    // -------------------------------------------------------------------
    // GET /api/usuarios/{id}
    // -------------------------------------------------------------------
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

    // -------------------------------------------------------------------
    // PUT /api/usuarios/{id}/rol
    // -------------------------------------------------------------------
    public void cambiarRol(Context ctx) {
        String id   = ctx.pathParam("id");
        var    body = ctx.bodyAsClass(CambiarRolRequest.class);

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

    // -------------------------------------------------------------------
    // DELETE /api/usuarios/{id}
    // -------------------------------------------------------------------
    public void eliminar(Context ctx) {
        String id       = ctx.pathParam("id");
        boolean borrado = usuarioService.eliminar(id);

        if (borrado) {
            ctx.status(HttpStatus.OK).json(Map.of("mensaje", "Usuario eliminado"));
        } else {
            ctx.status(HttpStatus.NOT_FOUND)
                    .json(Map.of("mensaje", "Usuario no encontrado"));
        }
    }

    // -------------------------------------------------------------------
    // DTO interno
    // -------------------------------------------------------------------
    public static class CambiarRolRequest {
        public String rol;
    }
}