package com.pe.articulos.modules.reabastecimiento.listener;

import com.pe.articulos.modules.productos.event.StockDisminuidoEvent;
import com.pe.articulos.modules.reabastecimiento.config.ReabastecimientoAsyncConfig;
import com.pe.articulos.modules.reabastecimiento.repository.ReordenRepository;
import com.pe.articulos.modules.reabastecimiento.service.ReabastecimientoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReabastecimientoListener {

    private final ReabastecimientoService reabastecimientoService;
    private final ReordenRepository reordenRepository;

    @Async(ReabastecimientoAsyncConfig.EJECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alDisminuirStock(StockDisminuidoEvent evento) {
        try {
            reabastecimientoService.evaluarProducto(evento.idCatalogo(), evento.idSucursal());
        } catch (Exception e) {
            log.error("No se pudo evaluar el reabastecimiento del catálogo {} en la sucursal {}", evento.idCatalogo(), evento.idSucursal(), e);
        }
    }

    @Scheduled(cron = "${reabastecimiento.cron:0 0 * * * *}")
    public void evaluacionPeriodica() {
        reordenRepository.sucursalesConPuntoDeReorden().forEach(reabastecimientoService::evaluarSucursal);
        reabastecimientoService.vencerOrdenesSinAtencion();
    }
}
