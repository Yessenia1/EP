package com.pe.articulos.core.observabilidad;

import com.pe.articulos.modules.reabastecimiento.enums.EstadoOrdenReabastecimiento;
import com.pe.articulos.modules.reabastecimiento.repository.OrdenReabastecimientoRepository;
import com.pe.articulos.service.ColaEventoRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetricasNegocio implements MeterBinder {

    private final ColaEventoRepository colaEventoRepository;
    private final OrdenReabastecimientoRepository ordenRepository;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("articulos.outbox.eventos", colaEventoRepository, r -> r.countByEstado("PENDIENTE"))
                .tag("estado", "PENDIENTE")
                .description("Eventos del Outbox aún no procesados")
                .register(registry);
        Gauge.builder("articulos.outbox.eventos", colaEventoRepository, r -> r.countByEstado("ERROR"))
                .tag("estado", "ERROR")
                .description("Eventos del Outbox que fallaron al procesarse")
                .register(registry);
        for (EstadoOrdenReabastecimiento estado : EstadoOrdenReabastecimiento.values()) {
            Gauge.builder("articulos.reabastecimiento.ordenes", ordenRepository, r -> r.countByEstado(estado))
                    .tag("estado", estado.name())
                    .description("Órdenes de reabastecimiento por estado")
                    .register(registry);
        }
    }
}
