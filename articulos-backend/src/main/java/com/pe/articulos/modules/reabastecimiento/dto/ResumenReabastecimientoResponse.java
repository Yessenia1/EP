package com.pe.articulos.modules.reabastecimiento.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumenReabastecimientoResponse {
    private long pendientes;
    private long aprobadas;
    private long convertidas;
    private long productosBajoMinimo;
}
