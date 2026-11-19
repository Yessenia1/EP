import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from '../../../shared/header/header.component';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { TablaGeneralComponent, Columna } from '../../../shared/components/tabla-general/tabla-general.component';
import { BreadcrumbComponent, BreadcrumbItem } from '../../../shared/components/breadcrumb/breadcrumb';
import { PrimaryButtonComponent } from '../../../shared/components/primary-button/primary-button';
import { SearchGenericComponent } from '../../../shared/components/reusable-search-selector/reusable-search-selector';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header';
import { PaginationComponent } from '../../../shared/components/pagination/pagination';
import { AlertService } from '../../../core/services/alert.service';
import { SidebarService } from '../../../shared/sidebar/sidebar.service';
import { AuthService } from '../../auth/services/auth.service';
import {
    EstadoOrdenReabastecimiento,
    OrdenReabastecimiento,
    ReabastecimientoService,
    ResumenReabastecimiento
} from '../services/reabastecimiento.service';

@Component({
    selector: 'app-reabastecimiento-list',
    standalone: true,
    imports: [
        CommonModule,
        HeaderComponent,
        SidebarComponent,
        TablaGeneralComponent,
        BreadcrumbComponent,
        PrimaryButtonComponent,
        SearchGenericComponent,
        PageHeaderComponent,
        PaginationComponent
    ],
    templateUrl: './reabastecimiento-list.component.html'
})
export class ReabastecimientoListComponent implements OnInit {
    currentPage = signal(0);
    pageSize = signal(10);
    totalElements = signal(0);
    totalPages = signal(0);
    searchTerm = signal('');
    searchType = signal('PRODUCTO');
    estadoFiltro = signal<EstadoOrdenReabastecimiento | null>('PENDIENTE');
    ordenes = signal<OrdenReabastecimiento[]>([]);
    resumen = signal<ResumenReabastecimiento>({ pendientes: 0, aprobadas: 0, convertidas: 0, productosBajoMinimo: 0 });
    loading = signal(false);
    evaluando = signal(false);

    private idSucursal = 0;

    filtros: { label: string; value: EstadoOrdenReabastecimiento | null }[] = [
        { label: 'Pendientes', value: 'PENDIENTE' },
        { label: 'Aprobadas', value: 'APROBADA' },
        { label: 'Convertidas', value: 'CONVERTIDA' },
        { label: 'Rechazadas', value: 'RECHAZADA' },
        { label: 'Vencidas', value: 'VENCIDA' },
        { label: 'Todas', value: null }
    ];

    searchOptions = [{ label: 'Producto', value: 'PRODUCTO' }];

    breadcrumbItems: BreadcrumbItem[] = [
        { label: 'Compra', route: '/compra/orden' },
        { label: 'Reabastecimiento' }
    ];

    columns: Columna[] = [
        { field: 'nombreProducto', header: 'Producto', tipo: 'text', subField: [] },
        { field: 'stockAlGenerar', header: 'Stock al generar', tipo: 'text', subField: [] },
        { field: 'stockMinimo', header: 'Mínimo', tipo: 'text', subField: [] },
        { field: 'cantidadSugerida', header: 'Sugerido', tipo: 'text', subField: [], badgeConfig: { from: 'text-indigo-600', to: 'text-indigo-600', text: 'font-bold' } },
        { field: 'cantidadAprobada', header: 'Aprobado', tipo: 'text', subField: [] },
        { field: 'estado', header: 'Estado', tipo: 'badge', subField: [] },
        { field: 'fechaGeneracion', header: 'Generada', tipo: 'date', subField: [] }
    ];

    constructor(
        private reabastecimientoService: ReabastecimientoService,
        private alertService: AlertService,
        private authService: AuthService,
        public sidebarService: SidebarService
    ) { }

    ngOnInit(): void {
        const sucursal = this.authService.getSucursalIdFromToken();
        if (!sucursal) {
            this.alertService.toast('No se pudo identificar la sucursal actual', 'error');
            return;
        }
        this.idSucursal = sucursal;
        this.cargarResumen();
        this.cargarItems();
    }

    cargarResumen(): void {
        this.reabastecimientoService.resumen(this.idSucursal).subscribe({
            next: (res) => res.success && this.resumen.set(res.data)
        });
    }

    cargarItems(): void {
        this.loading.set(true);
        this.reabastecimientoService.buscar(this.idSucursal, this.estadoFiltro(), this.searchTerm(), this.currentPage(), this.pageSize()).subscribe({
            next: (res) => {
                if (res.success && res.data) {
                    this.ordenes.set(res.data.content || []);
                    this.totalElements.set(res.data.totalElements || 0);
                    this.totalPages.set(res.data.totalPages || 0);
                }
                this.loading.set(false);
            },
            error: () => {
                this.loading.set(false);
                this.alertService.toast('Error al cargar las órdenes de reabastecimiento', 'error');
            }
        });
    }

    cambiarFiltro(estado: EstadoOrdenReabastecimiento | null): void {
        this.estadoFiltro.set(estado);
        this.currentPage.set(0);
        this.cargarItems();
    }

    onSearch(event: { q: string; type: string }): void {
        this.searchTerm.set(event.q);
        this.searchType.set(event.type);
        this.currentPage.set(0);
        this.cargarItems();
    }

    cambiarPagina(page: any): void {
        this.currentPage.set(Number(page));
        this.cargarItems();
    }

    cambiarTamanoPagina(size: any): void {
        this.pageSize.set(Number(size));
        this.currentPage.set(0);
        this.cargarItems();
    }

    evaluarAhora(): void {
        this.evaluando.set(true);
        this.reabastecimientoService.evaluar(this.idSucursal).subscribe({
            next: (res) => {
                this.evaluando.set(false);
                this.alertService.toast(res.message || 'Evaluación completada', 'success');
                this.refrescar();
            },
            error: () => {
                this.evaluando.set(false);
                this.alertService.toast('No se pudo evaluar el punto de reorden', 'error');
            }
        });
    }

    aprobar(orden: OrdenReabastecimiento): void {
        this.alertService.prompt(`Aprobar ${orden.nombreProducto}`, `Cantidad sugerida: ${orden.cantidadSugerida}. Ingrese la cantidad a pedir.`, 'number').then(res => {
            const cantidad = Number(res.value);
            if (!res.isConfirmed || !(cantidad > 0)) return;
            this.reabastecimientoService.aprobar(orden.id, cantidad).subscribe({
                next: () => {
                    this.alertService.toast('Orden aprobada', 'success');
                    this.refrescar();
                },
                error: (err) => this.alertService.toast(err?.error?.message || 'No se pudo aprobar', 'error')
            });
        });
    }

    rechazar(orden: OrdenReabastecimiento): void {
        this.alertService.prompt(`Rechazar ${orden.nombreProducto}`, 'Indique el motivo del rechazo', 'text').then(res => {
            if (!res.isConfirmed || !res.value) return;
            this.reabastecimientoService.rechazar(orden.id, res.value).subscribe({
                next: () => {
                    this.alertService.toast('Orden rechazada', 'success');
                    this.refrescar();
                },
                error: (err) => this.alertService.toast(err?.error?.message || 'No se pudo rechazar', 'error')
            });
        });
    }

    convertir(orden: OrdenReabastecimiento): void {
        this.alertService.confirm('Generar orden de compra', `Se creará una orden de compra por ${orden.cantidadAprobada} unidades de ${orden.nombreProducto}.`).then(res => {
            if (!res.isConfirmed) return;
            this.reabastecimientoService.convertir(orden.id, orden.idProveedor).subscribe({
                next: (r) => {
                    this.alertService.toast(`Orden de compra #${r.data?.idCompra} generada`, 'success');
                    this.refrescar();
                },
                error: (err) => this.alertService.toast(err?.error?.message || 'No se pudo generar la orden de compra', 'error')
            });
        });
    }

    private refrescar(): void {
        this.cargarResumen();
        this.cargarItems();
    }
}
