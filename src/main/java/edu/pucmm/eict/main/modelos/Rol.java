package edu.pucmm.eict.main.modelos;

/**
 * Roles disponibles en el sistema de encuestas.
 *
 * ADMIN        - Acceso total: gestiona usuarios, ve todos los registros.
 * ENCUESTADOR  - Captura y sincroniza formularios desde el dispositivo.
 * VISUALIZADOR - Solo puede consultar registros y el mapa.
 */
public enum Rol {
    ADMIN,
    ENCUESTADOR,
    VISUALIZADOR
}