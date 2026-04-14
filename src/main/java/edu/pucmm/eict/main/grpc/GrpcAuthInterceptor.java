package edu.pucmm.eict.main.grpc;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import edu.pucmm.eict.main.configuracion.JwtUtil;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class GrpcAuthInterceptor implements ServerInterceptor {

    public static final Context.Key<DecodedJWT> JWT_CONTEXT_KEY = Context.key("jwt");
    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String authHeader = headers.get(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("Token JWT requerido"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        try {
            DecodedJWT jwt = JwtUtil.verificarToken(authHeader.substring(7));
            Context context = Context.current().withValue(JWT_CONTEXT_KEY, jwt);
            return Contexts.interceptCall(context, call, headers, next);
        } catch (JWTVerificationException e) {
            call.close(Status.UNAUTHENTICATED.withDescription("Token invalido o expirado"), new Metadata());
            return new ServerCall.Listener<>() {};
        } catch (IllegalStateException e) {
            call.close(Status.INTERNAL.withDescription("JWT_SECRET no definido"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }
}
