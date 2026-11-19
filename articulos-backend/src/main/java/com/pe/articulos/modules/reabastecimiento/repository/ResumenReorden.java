package com.pe.articulos.modules.reabastecimiento.repository;

import java.math.BigDecimal;

public interface ResumenReorden {

    Long getIdCatalogo();

    BigDecimal getStockVigente();

    BigDecimal getStockMinimo();

    BigDecimal getStockMaximo();

    Long getIdProveedor();

    BigDecimal getPrecioCompra();
}
