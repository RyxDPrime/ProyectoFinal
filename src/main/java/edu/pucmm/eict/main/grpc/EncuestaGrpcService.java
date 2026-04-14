package edu.pucmm.eict.main.grpc;

import com.auth0.jwt.interfaces.DecodedJWT;
import edu.pucmm.eict.main.configuracion.JwtUtil;
import edu.pucmm.eict.main.modelos.Encuesta;
import edu.pucmm.eict.main.modelos.Rol;
import edu.pucmm.eict.main.servicios.EncuestaService;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.bson.types.ObjectId;

import java.time.Instant;

public class EncuestaGrpcService extends EncuestaServiceGrpc.EncuestaServiceImplBase {

    private final EncuestaService encuestaService;

    public EncuestaGrpcService(EncuestaService encuestaService) {
        this.encuestaService = encuestaService;
    }

    @Override
    public void listarPorUsuario(UsuarioRequest request, StreamObserver<EncuestaListResponse> responseObserver) {
        try {
            DecodedJWT jwt = jwtRequerido();
            String usuarioToken = JwtUtil.extraerUsuarioId(jwt);
            String rol = JwtUtil.extraerRol(jwt);
            String usuarioRequest = request.getUsuarioId();

            if (isBlank(usuarioRequest)) {
                throw Status.INVALID_ARGUMENT.withDescription("usuarioId es requerido").asRuntimeException();
            }
            if (!ObjectId.isValid(usuarioRequest)) {
                throw Status.INVALID_ARGUMENT.withDescription("usuarioId invalido").asRuntimeException();
            }

            // Igual que en REST: solo ADMIN puede consultar otros usuarios.
            if (!Rol.ADMIN.name().equals(rol) && !usuarioRequest.equals(usuarioToken)) {
                throw Status.PERMISSION_DENIED.withDescription("Acceso denegado").asRuntimeException();
            }

            EncuestaListResponse.Builder response = EncuestaListResponse.newBuilder();
            encuestaService.listarPorUsuario(usuarioRequest)
                    .forEach(e -> response.addEncuestas(toProto(e)));

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Error interno del servidor")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void crearEncuesta(EncuestaRequest request, StreamObserver<EncuestaResponse> responseObserver) {
        try {
            DecodedJWT jwt = jwtRequerido();
            String usuarioToken = JwtUtil.extraerUsuarioId(jwt);
            String usuarioRequest = request.getUsuarioId();

            if (!ObjectId.isValid(usuarioToken)) {
                throw Status.UNAUTHENTICATED.withDescription("Token sin subject valido").asRuntimeException();
            }

            if (isBlank(request.getNombre()) || isBlank(request.getSector()) || isBlank(request.getNivelEscolar())) {
                throw Status.INVALID_ARGUMENT.withDescription("nombre, sector y nivelEscolar son requeridos").asRuntimeException();
            }

            if (!isBlank(usuarioRequest) && !usuarioToken.equals(usuarioRequest)) {
                throw Status.PERMISSION_DENIED.withDescription("No puedes crear encuestas para otro usuario").asRuntimeException();
            }

            Encuesta encuesta = new Encuesta();
            encuesta.setUsuarioId(new ObjectId(usuarioToken));
            encuesta.setNombre(request.getNombre().trim());
            encuesta.setSector(request.getSector().trim());
            encuesta.setNivelEscolar(EncuestaService.parsearNivel(request.getNivelEscolar()));

            encuesta.setEdad(request.getEdad() > 0 ? request.getEdad() : null);
            encuesta.setGenero(blankToNull(request.getGenero()));
            encuesta.setTipoEscuela(blankToNull(request.getTipoEscuela()));
            encuesta.setEstadoEstudio(blankToNull(request.getEstadoEstudio()));
            encuesta.setLatitud(request.getLatitud());
            encuesta.setLongitud(request.getLongitud());
            encuesta.setCreadoEn(Instant.now());

            Encuesta creada = encuestaService.crear(encuesta);

            responseObserver.onNext(toProto(creada));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (RuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Error interno del servidor")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    private static DecodedJWT jwtRequerido() {
        DecodedJWT jwt = GrpcAuthInterceptor.JWT_CONTEXT_KEY.get();
        if (jwt == null) {
            throw Status.UNAUTHENTICATED.withDescription("Token JWT requerido").asRuntimeException();
        }
        return jwt;
    }

    private static EncuestaResponse toProto(Encuesta e) {
        EncuestaResponse.Builder b = EncuestaResponse.newBuilder();
        if (e.getId() != null) b.setId(e.getId().toHexString());
        if (e.getNombre() != null) b.setNombre(e.getNombre());
        if (e.getSector() != null) b.setSector(e.getSector());
        if (e.getNivelEscolar() != null) b.setNivelEscolar(e.getNivelEscolar().name());
        if (e.getLatitud() != null) b.setLatitud(e.getLatitud());
        if (e.getLongitud() != null) b.setLongitud(e.getLongitud());
        if (e.getUsuarioId() != null) b.setUsuarioId(e.getUsuarioId().toHexString());
        if (e.getCreadoEn() != null) b.setCreadoEn(e.getCreadoEn().toString());
        if (e.getEdad() != null) b.setEdad(e.getEdad());
        if (e.getGenero() != null) b.setGenero(e.getGenero());
        if (e.getTipoEscuela() != null) b.setTipoEscuela(e.getTipoEscuela());
        if (e.getEstadoEstudio() != null) b.setEstadoEstudio(e.getEstadoEstudio());
        return b.build();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
