package com.pe.articulos.modules.reabastecimiento.service;

import com.pe.articulos.core.shared.dto.PageResponse;
import com.pe.articulos.modules.reabastecimiento.dto.OrdenReabastecimientoResponse;
import com.pe.articulos.modules.reabastecimiento.dto.ResumenReabastecimientoResponse;
import com.pe.articulos.modules.reabastecimiento.enums.EstadoOrdenReabastecimiento;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface ReabastecimientoService {

    List<OrdenReabastecimientoResponse> evaluarSucursal(Long idSucursal);

    List<OrdenReabastecimientoResponse> evaluarProducto(Long idCatalogo, Long idSucursal);

    PageResponse<OrdenReabastecimientoResponse> buscar(Long idSucursal, EstadoOrdenReabastecimiento estado, String q, Pageable pageable);

    ResumenReabastecimientoResponse resumen(Long idSucursal);

    OrdenReabastecimientoResponse aprobar(Long id, BigDecimal cantidadAprobada);

    OrdenReabastecimientoResponse rechazar(Long id, String motivo);

    OrdenReabastecimientoResponse convertirEnCompra(Long id, Long idProveedor);

    int vencerOrdenesSinAtencion();
}
