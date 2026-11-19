import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type EstadoOrdenReabastecimiento = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'CONVERTIDA' | 'VENCIDA';

export interface OrdenReabastecimiento {
    id: number;
    idCatalogo: number;
    nombreProducto: string;
    idSucursal: number;
    idProveedor: number | null;
    stockAlGenerar: number;
    stockMinimo: number;
    stockMaximo: number | null;
    precioReferencia: number | null;
    cantidadSugerida: number;
    cantidadAprobada: number | null;
    estado: EstadoOrdenReabastecimiento;
    motivoRechazo: string | null;
    idCompra: number | null;
    fechaGeneracion: string;
    fechaAtencion: string | null;
    usuarioModificacion: string | null;
}

export interface ResumenReabastecimiento {
    pendientes: number;
    aprobadas: number;
    convertidas: number;
    productosBajoMinimo: number;
}

@Injectable({ providedIn: 'root' })
export class ReabastecimientoService {
    private apiUrl = `${environment.apiUrl}/reabastecimiento`;

    constructor(private http: HttpClient) { }

    buscar(idSucursal: number, estado: EstadoOrdenReabastecimiento | null, q: string, page = 0, size = 10): Observable<any> {
        let params = new HttpParams().set('idSucursal', idSucursal).set('page', page).set('size', size);
        if (estado) params = params.set('estado', estado);
        if (q) params = params.set('q', q);
        return this.http.get<any>(this.apiUrl, { params });
    }

    resumen(idSucursal: number): Observable<any> {
        return this.http.get<any>(`${this.apiUrl}/resumen`, { params: new HttpParams().set('idSucursal', idSucursal) });
    }

    evaluar(idSucursal: number): Observable<any> {
        return this.http.post<any>(`${this.apiUrl}/evaluar`, null, { params: new HttpParams().set('idSucursal', idSucursal) });
    }

    aprobar(id: number, cantidadAprobada: number): Observable<any> {
        return this.http.put<any>(`${this.apiUrl}/${id}/aprobar`, { cantidadAprobada });
    }

    rechazar(id: number, motivo: string): Observable<any> {
        return this.http.put<any>(`${this.apiUrl}/${id}/rechazar`, { motivo });
    }

    convertir(id: number, idProveedor: number | null): Observable<any> {
        return this.http.post<any>(`${this.apiUrl}/${id}/convertir`, { idProveedor });
    }
}
