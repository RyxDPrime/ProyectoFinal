package edu.pucmm.eict.main.configuracion;

import com.mongodb.ConnectionString;
import com.mongodb.MongoException;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.util.concurrent.TimeUnit;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Singleton que gestiona la conexión a MongoDB Atlas.
 *
 * - Lee MONGO_URI desde variables de entorno.
 * - Registra los POJOs (Usuario, Encuesta, GeoUbicacion) y los enums
 *   (Rol, NivelEscolar) para que el driver los mapee automáticamente.
 * - Expone getBaseDatos() para que los servicios obtengan
 *   su MongoDatabase sin conocer los detalles de conexión.
 */
public class MongoConfig {

    private static final String NOMBRE_BD = "encuesta_user";
    private static final String ENV_MONGO_URI = "MONGO_URI";
    private static final String PROP_MONGO_URI = "mongo.uri";
    private static final String URI_POR_DEFECTO = "mongodb://localhost:27017";

    private static MongoConfig instancia;

    private final MongoClient   cliente;
    private final MongoDatabase baseDatos;

    // -------------------------------------------------------------------
    // Constructor privado (Singleton)
    // -------------------------------------------------------------------

    private MongoConfig() {
        String uri = resolverMongoUri();

        // Codec que convierte automáticamente los POJOs anotados con @BsonId / @BsonProperty
        CodecRegistry pojoRegistry = fromProviders(
                PojoCodecProvider.builder()
                        .automatic(true)
                        .register(
                                edu.pucmm.eict.main.modelos.Usuario.class,
                                edu.pucmm.eict.main.modelos.Encuesta.class,
                                edu.pucmm.eict.main.modelos.GeoUbicacion.class
                        )
                        .build()
        );

        CodecRegistry registry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                pojoRegistry
        );

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .codecRegistry(registry)
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .applyToSocketSettings(socket -> socket.connectTimeout(5, TimeUnit.SECONDS))
                .build();

        this.cliente    = MongoClients.create(settings);
        this.baseDatos  = cliente.getDatabase(NOMBRE_BD).withCodecRegistry(registry);

        // No forzar un ping aquí: el servidor web debe poder arrancar
        // aunque MongoDB no esté disponible todavía.
        try {
            baseDatos.runCommand(new Document("ping", 1));
        } catch (MongoException e) {
            System.err.println("[MongoConfig] MongoDB no disponible al arrancar; se continuará con conexión perezosa.");
        }
    }

    private String resolverMongoUri() {
        String uri = System.getenv(ENV_MONGO_URI);
        if (uri != null && !uri.isBlank()) {
            return uri;
        }

        uri = System.getProperty(PROP_MONGO_URI);
        if (uri != null && !uri.isBlank()) {
            return uri;
        }

        System.err.println("[MongoConfig] No se encontro MONGO_URI ni -Dmongo.uri; se usara mongodb local por defecto.");
        return URI_POR_DEFECTO;
    }

    // -------------------------------------------------------------------
    // Acceso global
    // -------------------------------------------------------------------

    public static synchronized MongoConfig getInstance() {
        if (instancia == null) {
            instancia = new MongoConfig();
        }
        return instancia;
    }

    public MongoDatabase getBaseDatos() {
        return baseDatos;
    }

    // -------------------------------------------------------------------
    // Cierre limpio de la conexión
    // -------------------------------------------------------------------

    public void cerrar() {
        if (cliente != null) {
            cliente.close();
        }
    }
}