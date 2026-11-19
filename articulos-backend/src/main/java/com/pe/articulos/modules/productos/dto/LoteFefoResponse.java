package com.pe.articulos.modules.productos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoteFefoResponse {
    private Long idProducto;
    private Long idCatalogo;
    private Long idSucursal;
    private String nroLote;
    private LocalDate fechaVencimiento;
    private BigDecimal stock;
    private Long diasParaVencer;
    private Integer ordenFefo;
}
