package com.pe.articulos.modules.productos.services;

import com.pe.articulos.core.exception.ValidationException;
import com.pe.articulos.modules.productos.dto.LoteFefoResponse;
import com.pe.articulos.modules.productos.entity.Producto;
import com.pe.articulos.modules.productos.mapper.LoteFefoMapper;
import com.pe.articulos.modules.productos.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoteFefoServiceTest {

    private static final LocalDate HOY = LocalDate.now();

    @Mock
    private ProductoRepository productoRepository;

    private LoteFefoService service;

    @BeforeEach
    void setUp() {
        LoteFefoMapper mapper = Mappers.getMapper(LoteFefoMapper.class);
        service = new LoteFefoService(productoRepository, mapper);
        ReflectionTestUtils.setField(service, "fefoEstricto", true);
    }

    private Producto lote(long id, String nroLote, LocalDate vence, int stock) {
        Producto p = new Producto();
        p.setIdProducto(id);
        p.setIdCatalogo(1L);
        p.setIdSucursal(1L);
        p.setNroLote(nroLote);
        p.setFechaVencimiento(vence);
        p.setStock(BigDecimal.valueOf(stock));
        return p;
    }

    @Test
    @DisplayName("RN-11: un lote vencido no puede venderse")
    void rechazaLoteVencido() {
        Producto vencido = lote(14, "L-7714", HOY.minusDays(5), 12);

        assertThatThrownBy(() -> service.validarSalida(vencido, BigDecimal.ONE))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("L-7714")
                .hasMessageContaining("no puede venderse");
        verify(productoRepository, never()).findLotesFefo(any(), any(), any());
    }

    @Test
    @DisplayName("RN-10: si otro lote vence antes y tiene stock, se exige venderlo primero")
    void exigeLoteQueVencePrimero() {
        Producto primero = lote(11, "L-2105", HOY.plusDays(40), 80);
        Producto elegido = lote(1, "L-2291", HOY.plusDays(55), 500);
        when(productoRepository.findLotesFefo(1L, 1L, HOY)).thenReturn(List.of(primero, elegido));

        assertThatThrownBy(() -> service.validarSalida(elegido, BigDecimal.TEN))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Por FEFO debe venderse primero el lote L-2105");
    }

    @Test
    @DisplayName("El lote que vence primero se vende sin observaciones")
    void aceptaLoteFefo() {
        Producto primero = lote(11, "L-2105", HOY.plusDays(40), 80);
        Producto posterior = lote(1, "L-2291", HOY.plusDays(55), 500);
        when(productoRepository.findLotesFefo(1L, 1L, HOY)).thenReturn(List.of(primero, posterior));

        assertThatCode(() -> service.validarSalida(primero, BigDecimal.TEN)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Si el lote anterior no alcanza para la cantidad pedida, se permite el siguiente")
    void permiteLotePosteriorSiElAnteriorNoAlcanza() {
        Producto primero = lote(11, "L-2105", HOY.plusDays(40), 5);
        Producto elegido = lote(1, "L-2291", HOY.plusDays(55), 500);
        when(productoRepository.findLotesFefo(1L, 1L, HOY)).thenReturn(List.of(primero, elegido));

        assertThatCode(() -> service.validarSalida(elegido, BigDecimal.TEN)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un lote sin fecha de vencimiento cede ante cualquier lote con fecha")
    void loteSinFechaCedeAnteLoteConFecha() {
        Producto conFecha = lote(11, "L-2105", HOY.plusDays(40), 80);
        Producto sinFecha = lote(20, null, null, 100);
        when(productoRepository.findLotesFefo(1L, 1L, HOY)).thenReturn(List.of(conFecha, sinFecha));

        assertThatThrownBy(() -> service.validarSalida(sinFecha, BigDecimal.ONE))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("L-2105");
    }

    @Test
    @DisplayName("Con ventas.fefo.estricto=false solo se bloquean los lotes vencidos")
    void modoNoEstrictoSoloBloqueaVencidos() {
        ReflectionTestUtils.setField(service, "fefoEstricto", false);
        Producto posterior = lote(1, "L-2291", HOY.plusDays(55), 500);

        assertThatCode(() -> service.validarSalida(posterior, BigDecimal.TEN)).doesNotThrowAnyException();
        verify(productoRepository, never()).findLotesFefo(any(), any(), any());
    }

    @Test
    @DisplayName("listarLotes numera los lotes en orden FEFO y calcula los días para vencer")
    void listarLotesNumeraEnOrden() {
        when(productoRepository.findLotesFefo(eq(1L), eq(1L), any()))
                .thenReturn(List.of(lote(11, "L-2105", HOY.plusDays(40), 80), lote(1, "L-2291", HOY.plusDays(55), 500)));

        List<LoteFefoResponse> lotes = service.listarLotes(1L, 1L);

        assertThat(lotes).extracting(LoteFefoResponse::getOrdenFefo).containsExactly(1, 2);
        assertThat(lotes).extracting(LoteFefoResponse::getNroLote).containsExactly("L-2105", "L-2291");
        assertThat(lotes.get(0).getDiasParaVencer()).isEqualTo(40L);
    }
}
