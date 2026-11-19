package com.pe.articulos.modules.reabastecimiento.repository;

import com.pe.articulos.modules.productos.entity.Producto;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReordenRepository extends Repository<Producto, Long> {

    @Query("SELECT p.idCatalogo AS idCatalogo, "
            + "SUM(CASE WHEN p.fechaVencimiento IS NULL OR p.fechaVencimiento >= :hoy THEN p.stock ELSE 0 END) AS stockVigente, "
            + "MAX(p.stockMinimo) AS stockMinimo, MAX(p.stockMaximo) AS stockMaximo, "
            + "MAX(p.idProveedor) AS idProveedor, MAX(p.precioCompra) AS precioCompra "
            + "FROM Producto p WHERE p.idSucursal = :idSucursal AND (:idCatalogo IS NULL OR p.idCatalogo = :idCatalogo) "
            + "GROUP BY p.idCatalogo "
            + "HAVING MAX(p.stockMinimo) > 0 AND "
            + "SUM(CASE WHEN p.fechaVencimiento IS NULL OR p.fechaVencimiento >= :hoy THEN p.stock ELSE 0 END) <= MAX(p.stockMinimo)")
    List<ResumenReorden> bajoPuntoDeReorden(@Param("idSucursal") Long idSucursal,
                                            @Param("idCatalogo") Long idCatalogo,
                                            @Param("hoy") LocalDate hoy);

    @Query("SELECT DISTINCT p.idSucursal FROM Producto p WHERE p.stockMinimo > 0")
    List<Long> sucursalesConPuntoDeReorden();
}
