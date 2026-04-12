package edu.pucmm.eict.main.servicios;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import edu.pucmm.eict.main.modelos.Encuesta;
import edu.pucmm.eict.main.modelos.NivelEscolar;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de negocio para la entidad Encuesta.
 *
 * Responsabilidades:
 *   - Crear un formulario (req. 16.2)
 *   - Listar todos los formularios
 *   - Listar formularios de un usuario específico (req. 16.1)
 *   - Buscar por id
 *   - Actualizar un formulario antes de enviarlo (req. 11)
 *   - Eliminar un formulario antes de enviarlo (req. 11)
 *   - Sincronizar en lote desde el cliente offline (req. 8)
 *
 * Recibe MongoDatabase por constructor para facilitar pruebas.
 */
public class EncuestaService {

    private static final String COLECCION = "encuestas";

    private final MongoCollection<Encuesta> coleccion;

    public EncuestaService(MongoDatabase db) {
        this.coleccion = db.getCollection(COLECCION, Encuesta.class);
    }

    // -------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------

    private void crearIndices() {
        coleccion.createIndex(Indexes.ascending("usuario_id"));
        coleccion.createIndex(Indexes.descending("creado_en"));
    }

    // -------------------------------------------------------------------
    // Crear
    // -------------------------------------------------------------------

    /**
     * Persiste un formulario recibido desde el frontend o la API.
     * Si ya trae id (vino del buffer offline) se respeta; de lo contrario se genera uno nuevo.
     */
    public Encuesta crear(Encuesta encuesta) {
        if (encuesta.getId() == null) {
            encuesta.setId(new ObjectId());
        }
        encuesta.setSincronizado(true);
        coleccion.insertOne(encuesta);
        return encuesta;
    }

    // -------------------------------------------------------------------
    // Leer
    // -------------------------------------------------------------------

    /** Devuelve todos los formularios ordenados del más reciente al más antiguo. */
    public List<Encuesta> listarTodas() {
        return coleccion
                .find()
                .sort(Sorts.descending("creado_en"))
                .into(new ArrayList<>());
    }

    /**
     * Devuelve los formularios de un usuario específico.
     * Requerimiento 16.1 — operación expuesta por REST y gRPC.
     */
    public List<Encuesta> listarPorUsuario(String usuarioId) {
        if (!ObjectId.isValid(usuarioId)) return new ArrayList<>();
        return coleccion
                .find(Filters.eq("usuario_id", new ObjectId(usuarioId)))
                .sort(Sorts.descending("creado_en"))
                .into(new ArrayList<>());
    }

    public Optional<Encuesta> buscarPorId(String id) {
        if (!ObjectId.isValid(id)) return Optional.empty();
        return Optional.ofNullable(
                coleccion.find(Filters.eq("_id", new ObjectId(id))).first()
        );
    }

    // -------------------------------------------------------------------
    // Actualizar  (req. 11 — modificar antes de enviar al servidor)
    // -------------------------------------------------------------------

    /**
     * Reemplaza completamente un formulario existente.
     * Solo se permite si el formulario pertenece al usuario indicado.
     * Retorna true si se modificó.
     */
    public boolean actualizar(String id, String usuarioId, Encuesta datos) {
        if (!ObjectId.isValid(id) || !ObjectId.isValid(usuarioId)) return false;

        datos.setId(new ObjectId(id));
        datos.setSincronizado(true);

        var resultado = coleccion.replaceOne(
                Filters.and(
                        Filters.eq("_id",        new ObjectId(id)),
                        Filters.eq("usuario_id", new ObjectId(usuarioId))
                ),
                datos,
                new ReplaceOptions().upsert(false)
        );
        return resultado.getModifiedCount() > 0;
    }

    // -------------------------------------------------------------------
    // Eliminar  (req. 11 — borrar antes de enviar al servidor)
    // -------------------------------------------------------------------

    /**
     * Elimina un formulario validando que pertenezca al usuario indicado.
     * Los ADMIN pueden eliminar cualquier formulario (validación de rol en el controller).
     */
    public boolean eliminar(String id, String usuarioId) {
        if (!ObjectId.isValid(id) || !ObjectId.isValid(usuarioId)) return false;
        var resultado = coleccion.deleteOne(
                Filters.and(
                        Filters.eq("_id",        new ObjectId(id)),
                        Filters.eq("usuario_id", new ObjectId(usuarioId))
                )
        );
        return resultado.getDeletedCount() > 0;
    }

    /** Elimina por id sin restricción de usuario (uso ADMIN). */
    public boolean eliminarAdmin(String id) {
        if (!ObjectId.isValid(id)) return false;
        var resultado = coleccion.deleteOne(Filters.eq("_id", new ObjectId(id)));
        return resultado.getDeletedCount() > 0;
    }

    // -------------------------------------------------------------------
    // Sincronización en lote desde cliente offline  (req. 8)
    // -------------------------------------------------------------------

    /**
     * Recibe una lista de encuestas capturadas offline y las persiste
     * con upsert para evitar duplicados si se reintenta la sincronización.
     *
     * @return cantidad de documentos insertados o actualizados
     */
    public int sincronizarLote(List<Encuesta> encuestas) {
        int procesadas = 0;
        for (Encuesta e : encuestas) {
            if (e.getId() == null) {
                e.setId(new ObjectId());
            }
            e.setSincronizado(true);
            coleccion.replaceOne(
                    Filters.eq("_id", e.getId()),
                    e,
                    new ReplaceOptions().upsert(true)
            );
            procesadas++;
        }
        return procesadas;
    }

    // -------------------------------------------------------------------
    // Utilidades de validación
    // -------------------------------------------------------------------

    /**
     * Convierte el string del nivel escolar al enum correspondiente.
     * Lanza IllegalArgumentException si el valor no es válido.
     */
    public static NivelEscolar parsearNivel(String valor) {
        try {
            return NivelEscolar.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Nivel escolar inválido: '" + valor + "'. " +
                            "Valores válidos: BASICO, MEDIO, GRADO_UNIVERSITARIO, POSTGRADO, DOCTORADO"
            );
        }
    }
}