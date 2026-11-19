package com.pe.articulos.modules.reabastecimiento.dto;

import com.pe.articulos.modules.reabastecimiento.enums.EstadoOrdenReabastecimiento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenReabastecimientoResponse {
    private Long id;
    private Long idCatalogo;
    private String nombreProducto;
    private Long idSucursal;
    private Long idProveedor;
    private BigDecimal stockAlGenerar;
    private BigDecimal stockMinimo;
    private BigDecimal stockMaximo;
    private BigDecimal precioReferencia;
    private BigDecimal cantidadSugerida;
    private BigDecimal cantidadAprobada;
    private EstadoOrdenReabastecimiento estado;
    private String motivoRechazo;
    private Long idCompra;
    private LocalDateTime fechaGeneracion;
    private LocalDateTime fechaAtencion;
    private String usuarioModificacion;
}
