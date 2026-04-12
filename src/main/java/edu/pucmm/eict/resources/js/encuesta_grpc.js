/**
 * encuesta_grpc.js
 *
 * Stub gRPC-Web para el servicio EncuestaService.
 * Generado a partir de encuesta.proto.
 *
 * Usa la librería grpc-web (importhable desde CDN via ESM shim o importado
 * directamente como script). Aquí usamos la versión UMD compatible con
 * script tag clásico.
 *
 * PROTO de referencia:
 *
 *   service EncuestaService {
 *     rpc ListarPorUsuario (UsuarioRequest) returns (EncuestaListResponse);
 *     rpc CrearEncuesta    (EncuestaRequest) returns (EncuestaResponse);
 *   }
 */

'use strict';

// ── Codificación/decodificación base64 de mensajes protobuf ──────
// Implementación manual del wire format de Protocol Buffers
// (solo los campos que usamos, suficiente para la demo)

const Proto = {
    /**
     * Codifica un mensaje simple como Protobuf binario.
     * Campo 1: string  (wire type 2 = length-delimited)
     */
    encodeString: function(fieldNumber, value) {
        const enc   = new TextEncoder();
        const bytes = enc.encode(value);
        const tag   = (fieldNumber << 3) | 2; // wire type 2
        return [tag, bytes.length, ...bytes];
    },

    /**
     * Codifica un double como Protobuf.
     * Campo N: double (wire type 1 = 64-bit)
     */
    encodeDouble: function(fieldNumber, value) {
        const tag = (fieldNumber << 3) | 1;
        const buf = new ArrayBuffer(8);
        new DataView(buf).setFloat64(0, value, true);
        return [tag, ...new Uint8Array(buf)];
    },

    /**
     * Codifica un entero positivo como Protobuf varint.
     * Campo N: int32/uint32 (wire type 0).
     */
    encodeVarint: function(fieldNumber, value) {
        const tag = (fieldNumber << 3) | 0;
        let n = Number(value);
        if (!Number.isFinite(n) || n < 0) n = 0;
        n = Math.floor(n);

        const bytes = [tag];
        do {
            let b = n & 0x7f;
            n >>>= 7;
            if (n !== 0) b |= 0x80;
            bytes.push(b);
        } while (n !== 0);
        return bytes;
    },

    /**
     * Envuelve bytes en el frame gRPC-Web:
     *   byte 0:     0 = datos, 1 = trailer
     *   bytes 1-4:  longitud en big-endian
     *   bytes 5+:   mensaje protobuf
     */
    frameGrpcWeb: function(msgBytes) {
        const len  = msgBytes.length;
        const frame = new Uint8Array(5 + len);
        frame[0] = 0; // flag: datos
        new DataView(frame.buffer).setUint32(1, len, false); // big-endian
        frame.set(msgBytes, 5);
        return frame;
    },

    /**
     * Parsea la respuesta gRPC-Web y extrae los bytes del mensaje.
     */
    parseGrpcWebResponse: function(buffer) {
        const view = new DataView(buffer);
        const flag = view.getUint8(0);
        if (flag === 1) return null; // es trailer, no datos
        const len = view.getUint32(1, false);
        return new Uint8Array(buffer, 5, len);
    },

    /**
     * Decodifica un mensaje protobuf simple a objeto JS.
     * Soporta: string (wire 2), double (wire 1), bool (wire 0).
     */
    decode: function(bytes) {
        const result = {};
        let i = 0;
        const dec = new TextDecoder();

        while (i < bytes.length) {
            const tag      = bytes[i++];
            const field    = tag >> 3;
            const wireType = tag & 0x7;

            if (wireType === 2) {
                // Length-delimited: string o bytes embebidos
                const len = bytes[i++];
                const val = bytes.slice(i, i + len);
                result[field] = dec.decode(val);
                i += len;
            } else if (wireType === 1) {
                // 64-bit: double
                const buf = bytes.slice(i, i + 8).buffer;
                result[field] = new DataView(buf).getFloat64(0, true);
                i += 8;
            } else if (wireType === 0) {
                // Varint: bool/int
                result[field] = bytes[i++];
            } else {
                break; // tipo no soportado, salir
            }
        }
        return result;
    },
};

/**
 * Cliente gRPC-Web para EncuestaService.
 *
 * @param {string} proxyUrl URL del proxy Envoy, ej: http://localhost:8080
 */
class EncuestaGrpcClient {
    constructor(proxyUrl) {
        this.baseUrl = proxyUrl.replace(/\/$/, '');
        this.service = 'edu.pucmm.eict.main.grpc.EncuestaService';
    }

    /**
     * Realiza una llamada unaria gRPC-Web.
     *
     * @param {string} method Nombre del método RPC
     * @param {Uint8Array} msgBytes Mensaje protobuf serializado
     * @param {string} token JWT Bearer
     * @returns {Promise<Uint8Array>} Bytes de la respuesta
     */
    async _llamar(method, msgBytes, token) {
        const url = `${this.baseUrl}/${this.service}/${method}`;
        const body = Proto.frameGrpcWeb(msgBytes);

        const res = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type':   'application/grpc-web+proto',
                'X-Grpc-Web':     '1',
                'Authorization':  `Bearer ${token}`,
            },
            body,
        });

        if (!res.ok) {
            const texto = await res.text();
            throw new Error(`gRPC error ${res.status}: ${texto}`);
        }

        const buffer = await res.arrayBuffer();
        return Proto.parseGrpcWebResponse(buffer);
    }

    /**
     * ListarPorUsuario(UsuarioRequest) → EncuestaListResponse
     *
     * UsuarioRequest: { usuarioId: string (field 1) }
     */
    async listarPorUsuario(usuarioId, token) {
        const msgBytes = new Uint8Array(Proto.encodeString(1, usuarioId));
        const resBytes = await this._llamar('ListarPorUsuario', msgBytes, token);

        // Parsear EncuestaListResponse: campo 1 = repeated EncuestaResponse
        // Para la demo, devolvemos los bytes raw y los interpretamos como texto
        if (!resBytes) return [];

        // Intento de decodificación simple
        const dec = new TextDecoder();
        const raw = dec.decode(resBytes);
        return [{ _raw: raw, _bytes: resBytes.length + ' bytes recibidos' }];
    }

    /**
     * CrearEncuesta(EncuestaRequest) → EncuestaResponse
     *
     * EncuestaRequest campos:
     *   1: usuarioId (string)
     *   2: nombre    (string)
     *   3: sector    (string)
     *   4: nivelEscolar (string)
     *   5: latitud   (double)
     *   6: longitud  (double)
     *   7: edad      (varint)
     *   8: género    (string)
     *   9: tipoEscuela (string)
     *  10: estadoEstudio (string)
     */
    async crearEncuesta(encuesta, token) {
        const bytes = [
            ...Proto.encodeString(1, encuesta.usuarioId  || ''),
            ...Proto.encodeString(2, encuesta.nombre     || ''),
            ...Proto.encodeString(3, encuesta.sector     || ''),
            ...Proto.encodeString(4, encuesta.nivelEscolar || ''),
            ...(encuesta.latitud  != null ? Proto.encodeDouble(5, encuesta.latitud)  : []),
            ...(encuesta.longitud != null ? Proto.encodeDouble(6, encuesta.longitud) : []),
            ...(encuesta.edad     != null ? Proto.encodeVarint(7, encuesta.edad) : []),
            ...Proto.encodeString(8, encuesta.genero || ''),
            ...Proto.encodeString(9, encuesta.tipoEscuela || ''),
            ...Proto.encodeString(10, encuesta.estadoEstudio || ''),
        ];

        const resBytes = await this._llamar('CrearEncuesta', new Uint8Array(bytes), token);
        if (!resBytes) return null;
        return Proto.decode(resBytes);
    }
}

// Exportar para uso en el HTML
window.EncuestaGrpcClient = EncuestaGrpcClient;