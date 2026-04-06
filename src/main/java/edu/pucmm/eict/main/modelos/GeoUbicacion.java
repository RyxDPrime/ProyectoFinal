package edu.pucmm.eict.main.modelos;

import org.bson.codecs.pojo.annotations.BsonProperty;

/**
 * Representa una coordenada geográfica GPS.
 *
 * Se usa como objeto embebido dentro de Encuesta para poder
 * crear índices geoespaciales en MongoDB si se requiere en el futuro
 * (req. 7 y req. 9 - presentación en mapa).
 *
 * Ejemplo de uso:
 *   encuesta.setUbicacion(new GeoUbicacion(18.4861, -69.9312));
 */
public class GeoUbicacion {

    @BsonProperty("latitud")
    private Double latitud;

    @BsonProperty("longitud")
    private Double longitud;

    // -------------------------------------------------------------------
    // Constructores
    // -------------------------------------------------------------------

    public GeoUbicacion() {}

    public GeoUbicacion(Double latitud, Double longitud) {
        this.latitud  = latitud;
        this.longitud = longitud;
    }

    // -------------------------------------------------------------------
    // Getters y Setters
    // -------------------------------------------------------------------

    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }

    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }

    // -------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------

    public boolean esValida() {
        return latitud  != null && latitud  >= -90  && latitud  <= 90 &&
                longitud != null && longitud >= -180 && longitud <= 180;
    }

    @Override
    public String toString() {
        return "GeoUbicacion{lat=" + latitud + ", lng=" + longitud + '}';
    }
}