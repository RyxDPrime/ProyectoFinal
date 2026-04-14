package edu.pucmm.eict.main.modelos;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.time.Instant;

public class Usuario {

    @BsonId
    private ObjectId id;

    @BsonProperty("nombre")
    private String nombre;

    @BsonProperty("email")
    private String email;

    @BsonProperty("password_hash")
    private String passwordHash;

    @BsonProperty("rol")
    private Rol rol;

    @BsonProperty("creado_en")
    private Instant creadoEn;

    public Usuario() {}

    public Usuario(String nombre, String email, String passwordHash, Rol rol) {
        this.id           = new ObjectId();
        this.nombre       = nombre;
        this.email        = email;
        this.passwordHash = passwordHash;
        this.rol          = rol;
        this.creadoEn     = Instant.now();
    }

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    @Override
    public String toString() {
        return "Usuario{" +
                "id="      + id      +
                ", nombre='" + nombre + '\'' +
                ", email='"  + email  + '\'' +
                ", rol="   + rol     +
                '}';
    }
}