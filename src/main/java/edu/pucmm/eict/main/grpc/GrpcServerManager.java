package edu.pucmm.eict.main.grpc;

import edu.pucmm.eict.main.servicios.EncuestaService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;

import java.io.IOException;

public class GrpcServerManager {

    private final int port;
    private final Server server;

    public GrpcServerManager(EncuestaService encuestaService) {
        this.port = leerPuertoGrpc();
        this.server = ServerBuilder
                .forPort(port)
                .addService(ServerInterceptors.intercept(
                        new EncuestaGrpcService(encuestaService),
                        new GrpcAuthInterceptor()
                ))
                .build();
    }

    public void start() throws IOException {
        server.start();
        System.out.println("[gRPC] Servidor iniciado en puerto " + port);
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
            System.out.println("[gRPC] Servidor detenido");
        }
    }

    public int getPort() {
        return port;
    }

    private static int leerPuertoGrpc() {
        String env = System.getenv("GRPC_PORT");
        try {
            return (env != null && !env.isBlank()) ? Integer.parseInt(env.trim()) : 9090;
        } catch (NumberFormatException e) {
            System.err.println("[gRPC] GRPC_PORT invalido '" + env + "', usando 9090.");
            return 9090;
        }
    }
}
