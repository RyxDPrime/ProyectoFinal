package edu.pucmm.eict.main.modelos;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Encuesta {

    @BsonId
    private ObjectId id;

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

    @BsonProperty("usuario_id")
    private ObjectId usuarioId;

    @BsonProperty("usuario_nombre")
    private String usuarioNombre;

    @BsonProperty("latitud")
    private Double latitud;

    @BsonProperty("longitud")
    private Double longitud;

    @BsonProperty("foto_base64")
    private String fotoBase64;

    @BsonProperty("sincronizado")
    private boolean sincronizado;

    @BsonProperty("creado_en")
    private Instant creadoEn;

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