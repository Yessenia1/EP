package com.pe.articulos.modules.reabastecimiento.controller;

import com.pe.articulos.core.shared.dto.ApiResponse;
import com.pe.articulos.core.shared.dto.PageResponse;
import com.pe.articulos.modules.reabastecimiento.dto.AprobarOrdenRequest;
import com.pe.articulos.modules.reabastecimiento.dto.ConvertirOrdenRequest;
import com.pe.articulos.modules.reabastecimiento.dto.OrdenReabastecimientoResponse;
import com.pe.articulos.modules.reabastecimiento.dto.RechazarOrdenRequest;
import com.pe.articulos.modules.reabastecimiento.dto.ResumenReabastecimientoResponse;
import com.pe.articulos.modules.reabastecimiento.enums.EstadoOrdenReabastecimiento;
import com.pe.articulos.modules.reabastecimiento.service.ReabastecimientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Tag(name = "Reabastecimiento", description = "Órdenes automáticas por punto de reorden")
@RestController
@RequestMapping("/api/reabastecimiento")
@RequiredArgsConstructor
public class ReabastecimientoController {

    private final ReabastecimientoService service;

    @Operation(summary = "Listar órdenes de reabastecimiento (paginado)")
    @GetMapping
    @PreAuthorize("hasAuthority('COMPRA_LEER')")
    public ResponseEntity<ApiResponse<PageResponse<OrdenReabastecimientoResponse>>> buscar(
            @RequestParam Long idSucursal,
            @RequestParam(required = false) EstadoOrdenReabastecimiento estado,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "fechaGeneracion"));
        return ResponseEntity.ok(ApiResponse.success(service.buscar(idSucursal, estado, q, pageable), "Órdenes obtenidas"));
    }

    @Operation(summary = "Indicadores de reabastecimiento de la sucursal")
    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('COMPRA_LEER')")
    public ResponseEntity<ApiResponse<ResumenReabastecimientoResponse>> resumen(@RequestParam Long idSucursal) {
        return ResponseEntity.ok(ApiResponse.success(service.resumen(idSucursal), "Resumen obtenido"));
    }

    @Operation(summary = "Evaluar ahora el punto de reorden de la sucursal")
    @PostMapping("/evaluar")
    @PreAuthorize("hasAuthority('COMPRA_CREAR')")
    public ResponseEntity<ApiResponse<List<OrdenReabastecimientoResponse>>> evaluar(@RequestParam Long idSucursal) {
        log.info("Evaluación manual de reabastecimiento para la sucursal {}", idSucursal);
        List<OrdenReabastecimientoResponse> generadas = service.evaluarSucursal(idSucursal);
        return ResponseEntity.ok(ApiResponse.success(generadas, generadas.size() + " órdenes generadas"));
    }

    @Operation(summary = "Aprobar orden (permite ajustar la cantidad)")
    @PutMapping("/{id}/aprobar")
    @PreAuthorize("hasAuthority('COMPRA_CREAR')")
    public ResponseEntity<ApiResponse<OrdenReabastecimientoResponse>> aprobar(@PathVariable Long id, @Valid @RequestBody AprobarOrdenRequest request) {
        log.info("Aprobando orden de reabastecimiento {}", id);
        return ResponseEntity.ok(ApiResponse.success(service.aprobar(id, request.getCantidadAprobada()), "Orden aprobada"));
    }

    @Operation(summary = "Rechazar orden")
    @PutMapping("/{id}/rechazar")
    @PreAuthorize("hasAuthority('COMPRA_CREAR')")
    public ResponseEntity<ApiResponse<OrdenReabastecimientoResponse>> rechazar(@PathVariable Long id, @Valid @RequestBody RechazarOrdenRequest request) {
        log.info("Rechazando orden de reabastecimiento {}", id);
        return ResponseEntity.ok(ApiResponse.success(service.rechazar(id, request.getMotivo()), "Orden rechazada"));
    }

    @Operation(summary = "Convertir orden aprobada en orden de compra")
    @PostMapping("/{id}/convertir")
    @PreAuthorize("hasAuthority('COMPRA_CREAR')")
    public ResponseEntity<ApiResponse<OrdenReabastecimientoResponse>> convertir(@PathVariable Long id, @Valid @RequestBody(required = false) ConvertirOrdenRequest request) {
        log.info("Convirtiendo orden de reabastecimiento {} en orden de compra", id);
        Long idProveedor = request != null ? request.getIdProveedor() : null;
        return ResponseEntity.ok(ApiResponse.success(service.convertirEnCompra(id, idProveedor), "Orden de compra generada"));
    }
}
