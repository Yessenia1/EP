package com.pe.articulos.modules.reabastecimiento.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobarOrdenRequest {

    @NotNull(message = "La cantidad aprobada es obligatoria")
    @Positive(message = "La cantidad aprobada debe ser mayor a cero")
    private BigDecimal cantidadAprobada;
}
