package com.pe.articulos.modules.reabastecimiento.service.impl;

import com.pe.articulos.core.exception.ConflictException;
import com.pe.articulos.core.exception.ResourceNotFoundException;
import com.pe.articulos.core.exception.ValidationException;
import com.pe.articulos.modules.catalogo.entity.Catalogo;
import com.pe.articulos.modules.catalogo.entity.UnidadMedida;
import com.pe.articulos.modules.catalogo.repository.CatalogoRepository;
import com.pe.articulos.modules.catalogo.repository.UnidadMedidaRepository;
import com.pe.articulos.modules.compras.dto.CompraResponse;
import com.pe.articulos.modules.compras.dto.OrdenCreateRequest;
import com.pe.articulos.modules.compras.service.CompraService;
import com.pe.articulos.modules.reabastecimiento.dto.OrdenReabastecimientoResponse;
import com.pe.articulos.modules.reabastecimiento.entity.OrdenReabastecimiento;
import com.pe.articulos.modules.reabastecimiento.enums.EstadoOrdenReabastecimiento;
import com.pe.articulos.modules.reabastecimiento.mapper.OrdenReabastecimientoMapper;
import com.pe.articulos.modules.reabastecimiento.repository.OrdenReabastecimientoRepository;
import com.pe.articulos.modules.reabastecimiento.repository.ReordenRepository;
import com.pe.articulos.modules.reabastecimiento.repository.ResumenReorden;
import com.pe.articulos.service.KafkaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReabastecimientoServiceImplTest {

    @Mock private OrdenReabastecimientoRepository ordenRepository;
    @Mock private ReordenRepository reordenRepository;
    @Mock private CatalogoRepository catalogoRepository;
    @Mock private UnidadMedidaRepository unidadMedidaRepository;
    @Mock private CompraService compraService;
    @Mock private KafkaProducerService notificador;

    private ReabastecimientoServiceImpl service;

    @BeforeEach
    void setUp() {
        OrdenReabastecimientoMapper mapper = Mappers.getMapper(OrdenReabastecimientoMapper.class);
        service = new ReabastecimientoServiceImpl(ordenRepository, reordenRepository, catalogoRepository, unidadMedidaRepository,
                mapper, compraService, notificador);
        ReflectionTestUtils.setField(service, "horasVencimiento", 72L);
    }

    private ResumenReorden resumen(long idCatalogo, String stock, String minimo, String maximo) {
        return new ResumenReorden() {
            public Long getIdCatalogo() { return idCatalogo; }
            public BigDecimal getStockVigente() { return new BigDecimal(stock); }
            public BigDecimal getStockMinimo() { return new BigDecimal(minimo); }
            public BigDecimal getStockMaximo() { return maximo == null ? null : new BigDecimal(maximo); }
            public Long getIdProveedor() { return null; }
            public BigDecimal getPrecioCompra() { return new BigDecimal("5.00"); }
        };
    }

    private OrdenReabastecimiento orden(EstadoOrdenReabastecimiento estado) {
        return OrdenReabastecimiento.builder().id(7L).idCatalogo(2L).idSucursal(2L).estado(estado)
                .stockAlGenerar(new BigDecimal("18")).stockMinimo(new BigDecimal("60")).cantidadSugerida(new BigDecimal("102"))
                .precioReferencia(new BigDecimal("5.00")).build();
    }

    @Test
    @DisplayName("RN-12: genera una orden por (máximo − stock) cuando el stock vigente llega al mínimo")
    void generaOrdenBajoPuntoDeReorden() {
        when(reordenRepository.bajoPuntoDeReorden(eq(2L), isNull(), any())).thenReturn(List.of(resumen(2, "18", "60", "120")));
        when(ordenRepository.existsByIdCatalogoAndIdSucursalAndEstadoIn(eq(2L), eq(2L), any())).thenReturn(false);
        when(ordenRepository.save(any())).thenAnswer(inv -> { OrdenReabastecimiento o = inv.getArgument(0); o.setId(1L); return o; });

        List<OrdenReabastecimientoResponse> generadas = service.evaluarSucursal(2L);

        assertThat(generadas).hasSize(1);
        assertThat(generadas.get(0).getCantidadSugerida()).isEqualByComparingTo("102");
        assertThat(generadas.get(0).getEstado()).isEqualTo(EstadoOrdenReabastecimiento.PENDIENTE);
        verify(notificador).enviarNotificacion(isNull(), eq("COMPRAS"), anyString(), any(), eq("INFO"));
    }

    @Test
    @DisplayName("RN-12: no duplica si ya existe una orden abierta del producto en la sede")
    void noDuplicaOrdenAbierta() {
        when(reordenRepository.bajoPuntoDeReorden(eq(2L), isNull(), any())).thenReturn(List.of(resumen(2, "18", "60", "120")));
        when(ordenRepository.existsByIdCatalogoAndIdSucursalAndEstadoIn(eq(2L), eq(2L), any())).thenReturn(true);

        assertThat(service.evaluarSucursal(2L)).isEmpty();
        verify(ordenRepository, never()).save(any());
        verify(notificador, never()).enviarNotificacion(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sin stock máximo configurado se sugiere hasta el doble del mínimo")
    void sinMaximoUsaDobleDelMinimo() {
        when(reordenRepository.bajoPuntoDeReorden(eq(1L), eq(5L), any())).thenReturn(List.of(resumen(5, "0", "30", null)));
        when(ordenRepository.save(any())).thenAnswer(inv -> { OrdenReabastecimiento o = inv.getArgument(0); o.setId(2L); return o; });

        List<OrdenReabastecimientoResponse> generadas = service.evaluarProducto(5L, 1L);

        assertThat(generadas.get(0).getCantidadSugerida()).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("Aprobar guarda la cantidad ajustada y pasa a APROBADA")
    void apruebaOrdenPendiente() {
        when(ordenRepository.findById(7L)).thenReturn(Optional.of(orden(EstadoOrdenReabastecimiento.PENDIENTE)));
        when(ordenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrdenReabastecimientoResponse r = service.aprobar(7L, new BigDecimal("120"));

        assertThat(r.getEstado()).isEqualTo(EstadoOrdenReabastecimiento.APROBADA);
        assertThat(r.getCantidadAprobada()).isEqualByComparingTo("120");
        assertThat(r.getFechaAtencion()).isNotNull();
    }

    @Test
    @DisplayName("No se puede aprobar una orden rechazada (409)")
    void noApruebaOrdenRechazada() {
        when(ordenRepository.findById(7L)).thenReturn(Optional.of(orden(EstadoOrdenReabastecimiento.RECHAZADA)));

        assertThatThrownBy(() -> service.aprobar(7L, BigDecimal.TEN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("RECHAZADA");
    }

    @Test
    @DisplayName("Rechazar guarda el motivo")
    void rechazaConMotivo() {
        when(ordenRepository.findById(7L)).thenReturn(Optional.of(orden(EstadoOrdenReabastecimiento.PENDIENTE)));
        when(ordenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrdenReabastecimientoResponse r = service.rechazar(7L, "Se cubrirá con transferencia");

        assertThat(r.getEstado()).isEqualTo(EstadoOrdenReabastecimiento.RECHAZADA);
        assertThat(r.getMotivoRechazo()).isEqualTo("Se cubrirá con transferencia");
    }

    @Test
    @DisplayName("Orden inexistente responde 404")
    void ordenInexistente() {
        when(ordenRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rechazar(99L, "x")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Convertir sin proveedor habitual ni indicado es un error de validación")
    void convertirSinProveedor() {
        OrdenReabastecimiento aprobada = orden(EstadoOrdenReabastecimiento.APROBADA);
        aprobada.setCantidadAprobada(new BigDecimal("120"));
        when(ordenRepository.findById(7L)).thenReturn(Optional.of(aprobada));

        assertThatThrownBy(() -> service.convertirEnCompra(7L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("proveedor");
        verify(compraService, never()).crearOrden(any(), any());
    }

    @Test
    @DisplayName("Convertir crea la orden de compra con la cantidad aprobada y la unidad base")
    void convierteEnOrdenDeCompra() {
        OrdenReabastecimiento aprobada = orden(EstadoOrdenReabastecimiento.APROBADA);
        aprobada.setCantidadAprobada(new BigDecimal("120"));
        Catalogo catalogo = new Catalogo();
        UnidadMedida und = new UnidadMedida();
        und.setId(1L);
        when(ordenRepository.findById(7L)).thenReturn(Optional.of(aprobada));
        when(catalogoRepository.findById(2L)).thenReturn(Optional.of(catalogo));
        when(unidadMedidaRepository.findByNombre("UNIDAD")).thenReturn(Optional.of(und));
        CompraResponse compra = new CompraResponse();
        compra.setId(55L);
        when(compraService.crearOrden(any(), any())).thenReturn(compra);
        when(ordenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrdenReabastecimientoResponse r = service.convertirEnCompra(7L, 3L);

        ArgumentCaptor<OrdenCreateRequest> captor = ArgumentCaptor.forClass(OrdenCreateRequest.class);
        verify(compraService).crearOrden(captor.capture(), any());
        assertThat(captor.getValue().getIdProveedor()).isEqualTo(3L);
        assertThat(captor.getValue().getNombreGrupo()).isEqualTo("REABASTECIMIENTO-7");
        assertThat(captor.getValue().getDetalles().get(0).getCantidad()).isEqualByComparingTo("120");
        assertThat(captor.getValue().getDetalles().get(0).getIdUnidadMedida()).isEqualTo(1L);
        assertThat(r.getEstado()).isEqualTo(EstadoOrdenReabastecimiento.CONVERTIDA);
        assertThat(r.getIdCompra()).isEqualTo(55L);
    }

    @Test
    @DisplayName("RN-13: las órdenes pendientes con más de 72 h pasan a VENCIDA y se notifica")
    void venceOrdenesSinAtencion() {
        OrdenReabastecimiento a = orden(EstadoOrdenReabastecimiento.PENDIENTE);
        OrdenReabastecimiento b = orden(EstadoOrdenReabastecimiento.PENDIENTE);
        b.setId(8L);
        when(ordenRepository.findByEstadoAndFechaGeneracionBefore(eq(EstadoOrdenReabastecimiento.PENDIENTE), any(LocalDateTime.class)))
                .thenReturn(List.of(a, b));

        int vencidas = service.vencerOrdenesSinAtencion();

        assertThat(vencidas).isEqualTo(2);
        assertThat(a.getEstado()).isEqualTo(EstadoOrdenReabastecimiento.VENCIDA);
        verify(notificador, times(2)).enviarNotificacion(isNull(), eq("COMPRAS"), anyString(), any(), eq("ERROR"));
    }
}
