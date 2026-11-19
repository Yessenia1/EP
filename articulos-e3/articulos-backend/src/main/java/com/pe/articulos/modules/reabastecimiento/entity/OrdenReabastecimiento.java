package com.pe.articulos.modules.reabastecimiento.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pe.articulos.modules.catalogo.entity.Catalogo;
import com.pe.articulos.modules.reabastecimiento.enums.EstadoOrdenReabastecimiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orden_reabastecimiento", indexes = {
        @Index(name = "idx_orden_reab_sucursal", columnList = "id_sucursal"),
        @Index(name = "idx_orden_reab_catalogo", columnList = "id_catalogo"),
        @Index(name = "idx_orden_reab_estado", columnList = "estado")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenReabastecimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_catalogo", nullable = false)
    private Long idCatalogo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_catalogo", insertable = false, updatable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Catalogo catalogo;

    @Column(name = "id_sucursal", nullable = false)
    private Long idSucursal;

    @Column(name = "id_proveedor")
    private Long idProveedor;

    @Column(name = "stock_al_generar", precision = 10, scale = 2, nullable = false)
    private BigDecimal stockAlGenerar;

    @Column(name = "stock_minimo", precision = 10, scale = 2, nullable = false)
    private BigDecimal stockMinimo;

    @Column(name = "stock_maximo", precision = 10, scale = 2)
    private BigDecimal stockMaximo;

    @Column(name = "precio_referencia", precision = 12, scale = 2)
    private BigDecimal precioReferencia;

    @Column(name = "cantidad_sugerida", precision = 10, scale = 2, nullable = false)
    private BigDecimal cantidadSugerida;

    @Column(name = "cantidad_aprobada", precision = 10, scale = 2)
    private BigDecimal cantidadAprobada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoOrdenReabastecimiento estado;

    @Column(name = "motivo_rechazo", length = 255)
    private String motivoRechazo;

    @Column(name = "id_compra")
    private Long idCompra;

    @Column(name = "fecha_atencion")
    private LocalDateTime fechaAtencion;

    @CreatedBy
    @Column(name = "usuario_creacion", updatable = false, length = 50)
    private String usuarioCreacion;

    @LastModifiedBy
    @Column(name = "usuario_modificacion", length = 50)
    private String usuarioModificacion;

    @CreatedDate
    @Column(name = "fecha_generacion", updatable = false)
    private LocalDateTime fechaGeneracion;

    @LastModifiedDate
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;
}
