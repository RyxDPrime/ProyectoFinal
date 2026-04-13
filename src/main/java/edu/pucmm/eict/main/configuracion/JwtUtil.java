package edu.pucmm.eict.main.configuracion;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

/**
 * Utilidad para generar y verificar JSON Web Tokens (JWT).
 *
 * - El secret se lee desde la variable de entorno JWT_SECRET.
 * - Los tokens tienen vigencia de 8 horas.
 * - Cada token lleva los claims: sub (usuarioId), email, rol.
 *
 * Uso típico:
 *   String token  = JwtUtil.generarToken(id, email, rol);
 *   DecodedJWT jwt = JwtUtil.verificarToken(token);
 *   String rol    = jwt.getClaim("rol").asString();
 */
public class JwtUtil {

    private static final long   EXPIRACION_MS = 1000L * 60 * 60 * 8; // 8 horas
    private static final String ISSUER        = "encuesta-pucmm";
    private static final String PROP_JWT_SECRET = "jwt.secret";

    // Constructor privado — clase de utilidad estática
    private JwtUtil() {}

    // -------------------------------------------------------------------
    // Algoritmo de firma (HMAC256 con el secret del entorno)
    // -------------------------------------------------------------------

    private static Algorithm algoritmo() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = System.getProperty(PROP_JWT_SECRET);
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET no definido. Configura una clave segura antes de iniciar la aplicación.");
        }
        return Algorithm.HMAC256(secret);
    }

    // -------------------------------------------------------------------
    // Generar
    // -------------------------------------------------------------------

    /**
     * Genera un token firmado con los datos del usuario.
     *
     * @param usuarioId id del usuario (ObjectId como String)
     * @param email     correo del usuario
     * @param rol       nombre del rol (ej. "ADMIN")
     */
    public static String generarToken(String usuarioId, String email, String rol) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(usuarioId)
                .withClaim("email", email)
                .withClaim("rol", rol)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRACION_MS))
                .sign(algoritmo());
    }

    // -------------------------------------------------------------------
    // Verificar
    // -------------------------------------------------------------------

    /**
     * Verifica la firma y la vigencia del token.
     * Lanza JWTVerificationException si es inválido o expirado.
     */
    public static DecodedJWT verificarToken(String token) throws JWTVerificationException {
        return JWT.require(algoritmo())
                .withIssuer(ISSUER)
                .build()
                .verify(token);
    }

    // -------------------------------------------------------------------
    // Extraer claims (sin reverificar firma — solo para uso interno)
    // -------------------------------------------------------------------

    public static String extraerUsuarioId(DecodedJWT jwt) {
        return jwt.getSubject();
    }

    public static String extraerEmail(DecodedJWT jwt) {
        return jwt.getClaim("email").asString();
    }

    public static String extraerRol(DecodedJWT jwt) {
        return jwt.getClaim("rol").asString();
    }
}