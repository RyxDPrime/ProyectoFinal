package edu.pucmm.eict.main.modelos;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * Representa un formulario de encuesta capturado en campo.
 *
 * Colección MongoDB: encuestas
 *
 * Campos del encuestado (req. 6):
 *   nombre        - Nombre del encuestado
 *   sector        - Sector geográfico donde se realizó
 *   nivel_escolar - Nivel académico del encuestado
 *   edad          - Edad del encuestado
 *   genero        - Género del encuestado
 *   tipo_escuela  - Público o privado
 *   estado_estudio- Estado de estudio
 *
 * Trazabilidad (req. 6, 10):
 *   usuario_id    - ObjectId del usuario que registró el formulario
 *   usuario_nombre- Nombre del usuario (desnormalizado para evitar joins)
 *
 * Geolocalización (req. 7):
 *   latitud       - Latitud GPS al momento del registro
 *   longitud      - Longitud GPS al momento del registro
 *
 * Multimedia (req. 14):
 *   foto_base64   - Foto tomada desde el dispositivo, codificada en Base64
 *
 * Sincronización offline (req. 8, 11):
 *   sincronizado  - true cuando ya fue enviado al servidor
 *   creado_en     - Timestamp de captura (puede ser offline)
 */
public class Encuesta {

    @BsonId
    private ObjectId id;

    // Datos del encuestado
    @BsonProperty("nombre")
    private String nombre;

    @BsonProperty("sector")
    private String sector;

    @BsonProperty("nivel_escolar")
    private NivelEscolar nivelEscolar;

    @BsonProperty("edad")
    private Integer edad;

    @BsonProperty("genero")
    private String genero;

    @BsonProperty("tipo_escuela")
    private String tipoEscuela;

    @BsonProperty("estado_estudio")
    private String estadoEstudio;

    // Trazabilidad del usuario que registró
    @BsonProperty("usuario_id")
    private ObjectId usuarioId;

    @BsonProperty("usuario_nombre")
    private String usuarioNombre;

    // Geolocalización
    @BsonProperty("latitud")
    private Double latitud;

    @BsonProperty("longitud")
    private Double longitud;

    // Foto del dispositivo en Base64
    @BsonProperty("foto_base64")
    private String fotoBase64;

    // Control de sincronización offline→servidor
    @BsonProperty("sincronizado")
    private boolean sincronizado;

    @BsonProperty("creado_en")
    private Instant creadoEn;

    // -------------------------------------------------------------------
    // Constructores
    // -------------------------------------------------------------------

    public Encuesta() {}

    public Encuesta(String nombre, String sector, NivelEscolar nivelEscolar,
                    Integer edad, String genero, String tipoEscuela, String estadoEstudio,
                    ObjectId usuarioId, String usuarioNombre,
                    Double latitud, Double longitud, String fotoBase64) {
        this.id            = new ObjectId();
        this.nombre        = nombre;
        this.sector        = sector;
        this.nivelEscolar  = nivelEscolar;
        this.edad          = edad;
        this.genero        = genero;
        this.tipoEscuela   = tipoEscuela;
        this.estadoEstudio  = estadoEstudio;
        this.usuarioId     = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.latitud       = latitud;
        this.longitud      = longitud;
        this.fotoBase64    = fotoBase64;
        this.sincronizado  = true;
        this.creadoEn      = Instant.now();
    }

    // -------------------------------------------------------------------
    // Getters y Setters
    // -------------------------------------------------------------------

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public NivelEscolar getNivelEscolar() { return nivelEscolar; }
    public void setNivelEscolar(NivelEscolar nivelEscolar) { this.nivelEscolar = nivelEscolar; }

    public Integer getEdad() { return edad; }
    public void setEdad(Integer edad) { this.edad = edad; }

    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }

    public String getTipoEscuela() { return tipoEscuela; }
    public void setTipoEscuela(String tipoEscuela) { this.tipoEscuela = tipoEscuela; }

    public String getEstadoEstudio() { return estadoEstudio; }
    public void setEstadoEstudio(String estadoEstudio) { this.estadoEstudio = estadoEstudio; }

    public ObjectId getUsuarioId() { return usuarioId; }
    public void setUsuarioId(ObjectId usuarioId) { this.usuarioId = usuarioId; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }

    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }

    public String getFotoBase64() { return fotoBase64; }
    public void setFotoBase64(String fotoBase64) { this.fotoBase64 = fotoBase64; }

    public boolean isSincronizado() { return sincronizado; }
    public void setSincronizado(boolean sincronizado) { this.sincronizado = sincronizado; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    // -------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------

    @Override
    public String toString() {
        return "Encuesta{" +
                "id="           + id           +
                ", nombre='"    + nombre        + '\'' +
                ", sector='"    + sector        + '\'' +
                ", nivel="      + nivelEscolar  +
                ", edad="       + edad          +
                ", genero='"    + genero        + '\'' +
                ", escuela='"   + tipoEscuela   + '\'' +
                ", estado='"    + estadoEstudio  + '\'' +
                ", usuarioId="  + usuarioId     +
                ", lat="        + latitud       +
                ", lng="        + longitud      +
                ", sinc="       + sincronizado  +
                '}';
    }
}