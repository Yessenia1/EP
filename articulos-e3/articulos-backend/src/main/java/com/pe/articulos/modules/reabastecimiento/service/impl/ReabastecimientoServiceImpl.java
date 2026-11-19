package com.pe.articulos.modules.reabastecimiento.service.impl;

import com.pe.articulos.core.exception.ConflictException;
import com.pe.articulos.core.exception.ResourceNotFoundException;
import com.pe.articulos.core.exception.ValidationException;
import com.pe.articulos.core.security.SecurityUtils;
import com.pe.articulos.core.shared.dto.PageResponse;
import com.pe.articulos.modules.catalogo.repository.CatalogoRepository;
import com.pe.articulos.modules.catalogo.repository.UnidadMedidaRepository;
import com.pe.articulos.modules.compras.dto.CompraResponse;
import com.pe.articulos.modules.compras.dto.DetalleCompraRequest;
import com.pe.articulos.modules.compras.dto.OrdenCreateRequest;
import com.pe.articulos.modules.compras.service.CompraService;
import com.pe.articulos.modules.reabastecimiento.dto.OrdenReabastecimientoResponse;
import com.pe.articulos.modules.reabastecimiento.dto.ResumenReabastecimientoResponse;
import com.pe.articulos.modules.reabastecimiento.entity.OrdenReabastecimiento;
import com.pe.articulos.modules.reabastecimiento.enums.EstadoOrdenReabastecimiento;
import com.pe.articulos.modules.reabastecimiento.mapper.OrdenReabastecimientoMapper;
import com.pe.articulos.modules.reabastecimiento.repository.OrdenReabastecimientoRepository;
import com.pe.articulos.modules.reabastecimiento.repository.ReordenRepository;
import com.pe.articulos.modules.reabastecimiento.repository.ResumenReorden;
import com.pe.articulos.modules.reabastecimiento.service.ReabastecimientoService;
import com.pe.articulos.service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ReabastecimientoServiceImpl implements ReabastecimientoService {

    private static final Set<EstadoOrdenReabastecimiento> ABIERTAS = EnumSet.of(EstadoOrdenReabastecimiento.PENDIENTE, EstadoOrdenReabastecimiento.APROBADA);
    private static final String MODULO = "COMPRAS";

    private final OrdenReabastecimientoRepository ordenRepository;
    private final ReordenRepository reordenRepository;
    private final CatalogoRepository catalogoRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final OrdenReabastecimientoMapper mapper;
    private final CompraService compraService;
    private final KafkaProducerService notificador;

    @Value("${reabastecimiento.horas-vencimiento:72}")
    private long horasVencimiento;

    @Override
    @Transactional
    public List<OrdenReabastecimientoResponse> evaluarSucursal(Long idSucursal) {
        log.info("Evaluando punto de reorden de la sucursal {}", idSucursal);
        return generar(idSucursal, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OrdenReabastecimientoResponse> evaluarProducto(Long idCatalogo, Long idSucursal) {
        return generar(idSucursal, idCatalogo);
    }

    private List<OrdenReabastecimientoResponse> generar(Long idSucursal, Long idCatalogo) {
        List<OrdenReabastecimientoResponse> generadas = new ArrayList<>();
        for (ResumenReorden r : reordenRepository.bajoPuntoDeReorden(idSucursal, idCatalogo, LocalDate.now())) {
            if (ordenRepository.existsByIdCatalogoAndIdSucursalAndEstadoIn(r.getIdCatalogo(), idSucursal, ABIERTAS)) {
                continue;
            }
            BigDecimal stock = r.getStockVigente() != null ? r.getStockVigente() : BigDecimal.ZERO;
            BigDecimal maximo = r.getStockMaximo() != null && r.getStockMaximo().compareTo(r.getStockMinimo()) > 0
                    ? r.getStockMaximo()
                    : r.getStockMinimo().multiply(BigDecimal.valueOf(2));
            BigDecimal sugerida = maximo.subtract(stock).max(BigDecimal.ONE);
            OrdenReabastecimiento orden = ordenRepository.save(OrdenReabastecimiento.builder()
                    .idCatalogo(r.getIdCatalogo())
                    .catalogo(catalogoRepository.getReferenceById(r.getIdCatalogo()))
                    .idSucursal(idSucursal)
                    .idProveedor(r.getIdProveedor())
                    .stockAlGenerar(stock)
                    .stockMinimo(r.getStockMinimo())
                    .stockMaximo(r.getStockMaximo())
                    .precioReferencia(r.getPrecioCompra())
                    .cantidadSugerida(sugerida)
                    .estado(EstadoOrdenReabastecimiento.PENDIENTE)
                    .build());
            log.info("Orden de reabastecimiento {} generada: catálogo {} sucursal {} stock {} mínimo {} sugerido {}",
                    orden.getId(), r.getIdCatalogo(), idSucursal, stock, r.getStockMinimo(), sugerida);
            notificador.enviarNotificacion(null, MODULO, "Orden de reabastecimiento generada",
                    Map.of("idOrden", orden.getId(), "idCatalogo", r.getIdCatalogo(), "idSucursal", idSucursal,
                            "stock", stock, "cantidadSugerida", sugerida),
                    "INFO");
            generadas.add(mapper.toResponse(orden));
        }
        return generadas;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrdenReabastecimientoResponse> buscar(Long idSucursal, EstadoOrdenReabastecimiento estado, String q, Pageable pageable) {
        String termino = q != null ? q.trim() : "";
        return PageResponse.fromPage(ordenRepository.buscar(idSucursal, estado, termino, pageable).map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenReabastecimientoResponse resumen(Long idSucursal) {
        return ResumenReabastecimientoResponse.builder()
                .pendientes(ordenRepository.countByIdSucursalAndEstado(idSucursal, EstadoOrdenReabastecimiento.PENDIENTE))
                .aprobadas(ordenRepository.countByIdSucursalAndEstado(idSucursal, EstadoOrdenReabastecimiento.APROBADA))
                .convertidas(ordenRepository.countByIdSucursalAndEstado(idSucursal, EstadoOrdenReabastecimiento.CONVERTIDA))
                .productosBajoMinimo(reordenRepository.bajoPuntoDeReorden(idSucursal, null, LocalDate.now()).size())
                .build();
    }

    @Override
    @Transactional
    public OrdenReabastecimientoResponse aprobar(Long id, BigDecimal cantidadAprobada) {
        OrdenReabastecimiento orden = obtener(id);
        exigirEstado(orden, EstadoOrdenReabastecimiento.PENDIENTE);
        orden.setCantidadAprobada(cantidadAprobada);
        orden.setEstado(EstadoOrdenReabastecimiento.APROBADA);
        orden.setFechaAtencion(LocalDateTime.now());
        log.info("Orden de reabastecimiento {} aprobada por {} unidades", id, cantidadAprobada);
        return mapper.toResponse(ordenRepository.save(orden));
    }

    @Override
    @Transactional
    public OrdenReabastecimientoResponse rechazar(Long id, String motivo) {
        OrdenReabastecimiento orden = obtener(id);
        exigirEstado(orden, EstadoOrdenReabastecimiento.PENDIENTE);
        orden.setMotivoRechazo(motivo);
        orden.setEstado(EstadoOrdenReabastecimiento.RECHAZADA);
        orden.setFechaAtencion(LocalDateTime.now());
        log.info("Orden de reabastecimiento {} rechazada: {}", id, motivo);
        return mapper.toResponse(ordenRepository.save(orden));
    }

    @Override
    @Transactional
    public OrdenReabastecimientoResponse convertirEnCompra(Long id, Long idProveedor) {
        OrdenReabastecimiento orden = obtener(id);
        exigirEstado(orden, EstadoOrdenReabastecimiento.APROBADA);
        Long proveedor = idProveedor != null ? idProveedor : orden.getIdProveedor();
        if (proveedor == null) {
            throw new ValidationException("El producto no tiene proveedor habitual: indique el proveedor para generar la orden de compra.");
        }
        DetalleCompraRequest detalle = new DetalleCompraRequest();
        detalle.setIdProducto(orden.getIdCatalogo());
        detalle.setCantidad(orden.getCantidadAprobada());
        detalle.setPrecioUnitario(orden.getPrecioReferencia() != null ? orden.getPrecioReferencia() : BigDecimal.ZERO);
        detalle.setUnidad("UND");
        detalle.setIdUnidadMedida(unidadBase(orden.getIdCatalogo()));
        detalle.setFactorConversion(1);
        detalle.setTipoAfectacion("GRAVADO_ONEROSO");
        OrdenCreateRequest request = new OrdenCreateRequest();
        request.setIdProveedor(proveedor);
        request.setIdSucursal(orden.getIdSucursal());
        request.setNombreGrupo("REABASTECIMIENTO-" + orden.getId());
        request.setDetalles(List.of(detalle));
        CompraResponse compra = compraService.crearOrden(request, String.valueOf(SecurityUtils.getCurrentUserId()));
        orden.setIdProveedor(proveedor);
        orden.setIdCompra(compra.getId());
        orden.setEstado(EstadoOrdenReabastecimiento.CONVERTIDA);
        log.info("Orden de reabastecimiento {} convertida en la orden de compra {}", id, compra.getId());
        return mapper.toResponse(ordenRepository.save(orden));
    }

    @Override
    @Transactional
    public int vencerOrdenesSinAtencion() {
        List<OrdenReabastecimiento> vencidas = ordenRepository.findByEstadoAndFechaGeneracionBefore(
                EstadoOrdenReabastecimiento.PENDIENTE, LocalDateTime.now().minusHours(horasVencimiento));
        for (OrdenReabastecimiento orden : vencidas) {
            orden.setEstado(EstadoOrdenReabastecimiento.VENCIDA);
            orden.setFechaAtencion(LocalDateTime.now());
            notificador.enviarNotificacion(null, MODULO, "Orden de reabastecimiento vencida sin atención",
                    Map.of("idOrden", orden.getId(), "idSucursal", orden.getIdSucursal()), "ERROR");
        }
        ordenRepository.saveAll(vencidas);
        if (!vencidas.isEmpty()) {
            log.warn("{} órdenes de reabastecimiento vencieron sin atención", vencidas.size());
        }
        return vencidas.size();
    }

    private Long unidadBase(Long idCatalogo) {
        return catalogoRepository.findById(idCatalogo)
                .map(c -> c.getIdUnidadBase())
                .or(() -> unidadMedidaRepository.findByNombre("UNIDAD").map(u -> u.getId()))
                .orElseThrow(() -> new ValidationException("No existe la unidad de medida base para el producto " + idCatalogo));
    }

    private OrdenReabastecimiento obtener(Long id) {
        return ordenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de reabastecimiento", "id", id));
    }

    private void exigirEstado(OrdenReabastecimiento orden, EstadoOrdenReabastecimiento esperado) {
        if (orden.getEstado() != esperado) {
            throw new ConflictException("La orden " + orden.getId() + " está " + orden.getEstado() + " y debe estar " + esperado + ".");
        }
    }
}
