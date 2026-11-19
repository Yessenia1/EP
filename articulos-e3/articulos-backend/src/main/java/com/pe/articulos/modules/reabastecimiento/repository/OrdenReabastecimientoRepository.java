package com.pe.articulos.modules.reabastecimiento.repository;

import com.pe.articulos.modules.reabastecimiento.entity.OrdenReabastecimiento;
import com.pe.articulos.modules.reabastecimiento.enums.EstadoOrdenReabastecimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrdenReabastecimientoRepository extends JpaRepository<OrdenReabastecimiento, Long> {

    boolean existsByIdCatalogoAndIdSucursalAndEstadoIn(Long idCatalogo, Long idSucursal, Collection<EstadoOrdenReabastecimiento> estados);

    @Query(value = "SELECT o FROM OrdenReabastecimiento o LEFT JOIN FETCH o.catalogo c WHERE o.idSucursal = :idSucursal "
            + "AND (:estado IS NULL OR o.estado = :estado) "
            + "AND LOWER(c.nombre) LIKE LOWER(CONCAT('%', :q, '%'))",
            countQuery = "SELECT COUNT(o) FROM OrdenReabastecimiento o LEFT JOIN o.catalogo c WHERE o.idSucursal = :idSucursal "
                    + "AND (:estado IS NULL OR o.estado = :estado) "
                    + "AND LOWER(c.nombre) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<OrdenReabastecimiento> buscar(@Param("idSucursal") Long idSucursal,
                                       @Param("estado") EstadoOrdenReabastecimiento estado,
                                       @Param("q") String q,
                                       Pageable pageable);

    List<OrdenReabastecimiento> findByEstadoAndFechaGeneracionBefore(EstadoOrdenReabastecimiento estado, LocalDateTime limite);

    long countByIdSucursalAndEstado(Long idSucursal, EstadoOrdenReabastecimiento estado);

    long countByEstado(EstadoOrdenReabastecimiento estado);
}
