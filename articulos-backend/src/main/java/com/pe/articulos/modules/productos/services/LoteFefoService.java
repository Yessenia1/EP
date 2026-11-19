package com.pe.articulos.modules.productos.services;

import com.pe.articulos.core.exception.ValidationException;
import com.pe.articulos.modules.productos.dto.LoteFefoResponse;
import com.pe.articulos.modules.productos.entity.Producto;
import com.pe.articulos.modules.productos.mapper.LoteFefoMapper;
import com.pe.articulos.modules.productos.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class LoteFefoService {

    private final ProductoRepository productoRepository;
    private final LoteFefoMapper loteFefoMapper;

    @Value("${ventas.fefo.estricto:true}")
    private boolean fefoEstricto;

    @Transactional(readOnly = true)
    public List<LoteFefoResponse> listarLotes(Long idCatalogo, Long idSucursal) {
        log.info("Listando lotes FEFO del catálogo {} en la sucursal {}", idCatalogo, idSucursal);
        List<Producto> lotes = productoRepository.findLotesFefo(idCatalogo, idSucursal, LocalDate.now());
        List<LoteFefoResponse> respuesta = new ArrayList<>();
        for (int i = 0; i < lotes.size(); i++) {
            LoteFefoResponse lote = loteFefoMapper.toResponse(lotes.get(i));
            lote.setOrdenFefo(i + 1);
            respuesta.add(lote);
        }
        return respuesta;
    }

    @Transactional(readOnly = true)
    public void validarSalida(Producto lote, BigDecimal cantidad) {
        LocalDate hoy = LocalDate.now();
        if (lote.getFechaVencimiento() != null && lote.getFechaVencimiento().isBefore(hoy)) {
            log.warn("Intento de vender el lote vencido {} del producto {}", lote.getNroLote(), lote.getIdProducto());
            throw new ValidationException("El lote " + descripcion(lote) + " venció el " + lote.getFechaVencimiento()
                    + " y no puede venderse.");
        }
        if (!fefoEstricto || lote.getIdCatalogo() == null || lote.getIdSucursal() == null) {
            return;
        }
        Optional<Producto> primero = productoRepository.findLotesFefo(lote.getIdCatalogo(), lote.getIdSucursal(), hoy).stream()
                .filter(l -> !l.getIdProducto().equals(lote.getIdProducto()))
                .filter(l -> venceAntes(l, lote))
                .filter(l -> l.getStock() != null && l.getStock().compareTo(cantidad) >= 0)
                .findFirst();
        if (primero.isPresent()) {
            Producto debeSalir = primero.get();
            log.warn("Venta del lote {} rechazada por FEFO: debe salir antes el lote {}", lote.getNroLote(), debeSalir.getNroLote());
            throw new ValidationException("Por FEFO debe venderse primero el lote " + descripcion(debeSalir) + " (vence el "
                    + debeSalir.getFechaVencimiento() + ", stock " + debeSalir.getStock().stripTrailingZeros().toPlainString() + ").");
        }
    }

    private boolean venceAntes(Producto candidato, Producto elegido) {
        if (candidato.getFechaVencimiento() == null) {
            return false;
        }
        return elegido.getFechaVencimiento() == null || candidato.getFechaVencimiento().isBefore(elegido.getFechaVencimiento());
    }

    private String descripcion(Producto lote) {
        return lote.getNroLote() != null ? lote.getNroLote() : "#" + lote.getIdProducto();
    }
}
