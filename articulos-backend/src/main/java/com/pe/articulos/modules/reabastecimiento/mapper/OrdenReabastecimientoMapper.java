package com.pe.articulos.modules.reabastecimiento.mapper;

import com.pe.articulos.modules.reabastecimiento.dto.OrdenReabastecimientoResponse;
import com.pe.articulos.modules.reabastecimiento.entity.OrdenReabastecimiento;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrdenReabastecimientoMapper {

    @Mapping(target = "nombreProducto", source = "catalogo.nombre")
    OrdenReabastecimientoResponse toResponse(OrdenReabastecimiento orden);
}
