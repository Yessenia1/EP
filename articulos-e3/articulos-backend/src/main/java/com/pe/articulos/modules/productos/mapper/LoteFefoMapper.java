package com.pe.articulos.modules.productos.mapper;

import com.pe.articulos.modules.productos.dto.LoteFefoResponse;
import com.pe.articulos.modules.productos.entity.Producto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = { ChronoUnit.class, LocalDate.class })
public interface LoteFefoMapper {

    @Mapping(target = "diasParaVencer", expression = "java(producto.getFechaVencimiento() == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), producto.getFechaVencimiento()))")
    @Mapping(target = "ordenFefo", ignore = true)
    LoteFefoResponse toResponse(Producto producto);
}
