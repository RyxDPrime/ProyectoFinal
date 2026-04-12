package edu.pucmm.eict.main.servicios;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import edu.pucmm.eict.main.modelos.Rol;
import edu.pucmm.eict.main.modelos.Usuario;
import org.bson.types.ObjectId;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de negocio para la entidad Usuario.
 *
 * Responsabilidades:
 *   - Crear usuarios con contraseña hasheada (BCrypt)
 *   - Autenticar por email + contraseña
 *   - Buscar por id y por email
 *   - Listar todos los usuarios (solo ADMIN)
 *   - Cambiar rol
 *   - Eliminar usuario
 *   - Inicializar admin por defecto al primer arranque
 *
 * Recibe MongoDatabase por constructor para facilitar pruebas.
 */
public class UsuarioService {

    private static final String COLECCION = "usuarios";
    private static final int    BCRYPT_ROUNDS = 12;
    private static final String ADMIN_EMAIL = "admin@pucmm.edu.do";
    private static final String ADMIN_PASSWORD = "Admin1234!";

    private final MongoCollection<Usuario> coleccion;

    public UsuarioService(MongoDatabase db) {
        this.coleccion = db.getCollection(COLECCION, Usuario.class);
        crearIndices();
    }

    // -------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------

    private void crearIndices() {
        coleccion.createIndex(
                Indexes.ascending("email"),
                new IndexOptions().unique(true)
        );
    }

    // -------------------------------------------------------------------
    // Crear
    // -------------------------------------------------------------------

    /**
     * Registra un usuario nuevo.
     * Lanza IllegalArgumentException si el email ya está en uso.
     */
    public Usuario crear(String nombre, String email, String passwordPlano, Rol rol) {
        if (buscarPorEmail(email).isPresent()) {
            throw new IllegalArgumentException("El email ya está registrado: " + email);
        }
        String hash = BCrypt.hashpw(passwordPlano, BCrypt.gensalt(BCRYPT_ROUNDS));
        Usuario u = new Usuario(nombre, email, hash, rol);
        coleccion.insertOne(u);
        return u;
    }

    // -------------------------------------------------------------------
    // Leer
    // -------------------------------------------------------------------

    public Optional<Usuario> buscarPorId(String id) {
        if (!ObjectId.isValid(id)) return Optional.empty();
        return Optional.ofNullable(
                coleccion.find(Filters.eq("_id", new ObjectId(id))).first()
        );
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return Optional.ofNullable(
                coleccion.find(Filters.eq("email", email)).first()
        );
    }

    public List<Usuario> listarTodos() {
        return coleccion.find().into(new ArrayList<>());
    }

    // -------------------------------------------------------------------
    // Autenticación
    // -------------------------------------------------------------------

    /**
     * Verifica email y contraseña.
     * Retorna el Usuario si las credenciales son correctas, vacío si no.
     */
    public Optional<Usuario> autenticar(String email, String passwordPlano) {
        if (email == null || email.isBlank() || passwordPlano == null || passwordPlano.isBlank()) {
            return Optional.empty();
        }

        try {
            return buscarPorEmail(email.trim())
                    .filter(u -> u.getPasswordHash() != null && !u.getPasswordHash().isBlank())
                    .filter(u -> {
                        try {
                            return BCrypt.checkpw(passwordPlano, u.getPasswordHash());
                        } catch (IllegalArgumentException e) {
                            return false;
                        }
                    });
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------
    // Actualizar
    // -------------------------------------------------------------------

    /**
     * Cambia el rol de un usuario existente.
     * Retorna true si se modificó al menos un documento.
     */
    public boolean cambiarRol(String id, Rol nuevoRol) {
        if (!ObjectId.isValid(id)) return false;
        var resultado = coleccion.updateOne(
                Filters.eq("_id", new ObjectId(id)),
                Updates.set("rol", nuevoRol.name())
        );
        return resultado.getModifiedCount() > 0;
    }

    // -------------------------------------------------------------------
    // Eliminar
    // -------------------------------------------------------------------

    public boolean eliminar(String id) {
        if (!ObjectId.isValid(id)) return false;
        var resultado = coleccion.deleteOne(
                Filters.eq("_id", new ObjectId(id))
        );
        return resultado.getDeletedCount() > 0;
    }

    // -------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------

    /**
     * Si la colección está vacía, crea un ADMIN por defecto.
     * Se llama una sola vez desde Main al arrancar el servidor.
     */
    public void inicializarAdminDefecto() {
        var admin = buscarPorEmail(ADMIN_EMAIL);

        if (admin.isEmpty()) {
            String hash = BCrypt.hashpw(ADMIN_PASSWORD, BCrypt.gensalt(BCRYPT_ROUNDS));
            Usuario u = new Usuario("Administrador", ADMIN_EMAIL, hash, Rol.ADMIN);
            coleccion.insertOne(u);
            System.out.println("[UsuarioService] Admin por defecto creado → " + ADMIN_EMAIL + " / " + ADMIN_PASSWORD);
            return;
        }

        // Si ya existe, no se vuelve a sincronizar ni a sobrescribir la cuenta.
        System.out.println("[UsuarioService] Admin por defecto ya existe; no se modifica.");
    }
}