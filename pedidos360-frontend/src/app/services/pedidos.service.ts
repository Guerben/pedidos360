import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import { Observable, throwError } from 'rxjs';
import { map } from 'rxjs/operators';

export const ESTADOS_PEDIDO = ['PENDIENTE', 'EN_PREPARACION', 'DISPONIBLE', 'ENTREGADO'] as const;
export type EstadoPedido = typeof ESTADOS_PEDIDO[number];

/**
 * Contrato alineado con la entidad Pedido del backend Spring Boot
 * (id, producto, imageUrl, cantidad, stock, estado).
 */
export interface Pedido {
  id?: number;
  producto: string;
  imageUrl: string;
  cantidad: number;
  stock: number;
  estado: EstadoPedido;
}

@Injectable({
  providedIn: 'root'
})
export class PedidosService {
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private apiUrl = environment.api.pedidosUrl;

  /**
   * Genera los encabezados HTTP para la API protegida.
   * En la arquitectura con Spring Boot Resource Server y AWS API Gateway,
   * se envía prioritariamente el Access Token (con los scopes OAuth2 y claims de acceso).
   */
  private getHeaders(): HttpHeaders {
    const token = this.authService.getAccessToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  /**
   * GET: Obtener la lista de pedidos desde el backend Spring Boot.
   * El contrato del backend devuelve directamente una List<Pedido>.
   */
  getPedidos(): Observable<Pedido[]> {
    if (!this.authService.isAuthenticated()) {
      return throwError(() => ({
        status: 401,
        message: 'Sesión no autenticada en el cliente.'
      }));
    }

    return this.http.get<unknown>(this.apiUrl, { headers: this.getHeaders() }).pipe(
      map(response => {
        if (Array.isArray(response) && response.every(PedidosService.esPedido)) {
          return response;
        }
        throw new Error('El backend respondió con un formato de pedidos no válido.');
      })
    );
  }

  /**
   * POST: Crear un nuevo pedido en la base de datos PostgreSQL a través de Spring Boot.
   * Retorna el pedido con su ID generado por la base de datos.
   */
  crearPedido(pedido: Omit<Pedido, 'id'>): Observable<Pedido> {
    if (!this.authService.isAuthenticated()) {
      return throwError(() => ({
        status: 401,
        message: 'Sesión no autenticada. No se puede crear el pedido.'
      }));
    }
    return this.http.post<Pedido>(this.apiUrl, pedido, { headers: this.getHeaders() }).pipe(
      map(response => {
        if (!PedidosService.esPedido(response)) {
          throw new Error('El backend no devolvió un pedido persistido válido con su identificador.');
        }
        return response;
      })
    );
  }

  /**
   * PUT: Actualizar un pedido existente en el backend Spring Boot.
   */
  actualizarPedido(id: number, pedido: Pedido): Observable<Pedido> {
    if (!this.authService.isAuthenticated()) {
      return throwError(() => ({
        status: 401,
        message: 'Sesión no autenticada. No se puede actualizar el pedido.'
      }));
    }
    return this.http.put<Pedido>(`${this.apiUrl}/${id}`, pedido, { headers: this.getHeaders() }).pipe(
      map(response => {
        if (!PedidosService.esPedido(response)) {
          throw new Error('El backend no devolvió el pedido actualizado en el formato esperado.');
        }
        return response;
      })
    );
  }

  /**
   * DELETE: Eliminar un pedido en el backend Spring Boot.
   */
  eliminarPedido(id: number): Observable<void> {
    if (!this.authService.isAuthenticated()) {
      return throwError(() => ({
        status: 401,
        message: 'Sesión no autenticada. No se puede eliminar el pedido.'
      }));
    }
    return this.http.delete<void>(`${this.apiUrl}/${id}`, { headers: this.getHeaders() });
  }

  private static esPedido(value: unknown): value is Pedido {
    if (typeof value !== 'object' || value === null) {
      return false;
    }

    const pedido = value as Record<string, unknown>;
    return typeof pedido['id'] === 'number'
      && typeof pedido['producto'] === 'string'
      && typeof pedido['imageUrl'] === 'string'
      && typeof pedido['cantidad'] === 'number'
      && typeof pedido['stock'] === 'number'
      && typeof pedido['estado'] === 'string'
      && ESTADOS_PEDIDO.some(estado => estado === pedido['estado']);
  }
}
