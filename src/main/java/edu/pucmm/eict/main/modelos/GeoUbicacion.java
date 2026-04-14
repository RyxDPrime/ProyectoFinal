package edu.pucmm.eict.main.modelos;

import org.bson.codecs.pojo.annotations.BsonProperty;

public class GeoUbicacion {

    @BsonProperty("latitud")
    private Double latitud;

    @BsonProperty("longitud")
    private Double longitud;

    public GeoUbicacion() {}

    public GeoUbicacion(Double latitud, Double longitud) {
        this.latitud  = latitud;
        this.longitud = longitud;
    }

    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }

    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }

    public boolean esValida() {
        return latitud  != null && latitud  >= -90  && latitud  <= 90 &&
                longitud != null && longitud >= -180 && longitud <= 180;
    }

    @Override
    public String toString() {
        return "GeoUbicacion{lat=" + latitud + ", lng=" + longitud + '}';
    }
}