import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from './services/auth.service';
import { PedidosService, Pedido, EstadoPedido } from './services/pedidos.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  public authService = inject(AuthService);
  private pedidosService = inject(PedidosService);

  usuario = '';
  autenticado = false;
  esAdmin = false;
  cargando = false;
  errorAutenticacion: string | null = null;
  mensajeAlerta: string | null = null;
  tipoAlerta: 'success' | 'danger' | 'info' = 'info';

  pedidos: Pedido[] = [];

  // Modales
  mostrarModalNuevo = false;
  mostrarModalEditar = false;

  // Modelo para Crear Pedido (POST)
  nuevoPedido: Pedido = {
    producto: '',
    cantidad: 1,
    stock: 10,
    imageUrl: '',
    estado: 'PENDIENTE'
  };

  // Modelo para Editar Pedido (PUT)
  pedidoSeleccionado: Pedido | null = null;

  ngOnInit(): void {
    // 1. Sincronización reactiva mediante el BehaviorSubject autenticado$ del AuthService
    this.authService.autenticado$.subscribe(isAuth => {
      console.log('[App] Notificación reactiva de autenticación recibida:', isAuth);
      if (isAuth && !this.autenticado) {
        this.verificarSesion();
      }
    });

    // 2. Suscripción a eventos de angular-oauth2-oidc para reactividad inmediata al canjear el código PKCE
    this.authService.events.subscribe(event => {
      console.log('[Auth Event]', event.type);
      if (
        event.type === 'token_received' ||
        event.type === 'token_refreshed' ||
        event.type === 'discovery_document_loaded'
      ) {
        console.log('[Auth Event] Evento de sesión detectado exitosamente:', event.type);
        this.verificarSesion();
      } else if (event.type === 'token_error') {
        console.error('[Auth Event] Error en el intercambio de token:', event);
        if (this.authService.isAuthenticated()) {
          this.verificarSesion();
        } else {
          this.errorAutenticacion = 'Error al procesar el token de autorización en AWS Cognito. Por favor intente nuevamente.';
        }
      } else if (event.type === 'token_expires') {
        console.warn('[Auth Event] La sesión ha expirado.');
        this.errorAutenticacion = 'Su sesión en AWS Cognito ha expirado. Por favor ingrese nuevamente.';
        this.autenticado = false;
        this.esAdmin = false;
      }
    });

    // 3. Verificación inmediata y comprobaciones escalonadas de seguridad
    this.verificarSesion();
    [150, 350, 600, 1000, 1500].forEach(delay => {
      setTimeout(() => {
        if (!this.autenticado) {
          this.verificarSesion();
        }
      }, delay);
    });

    // 4. Sincronización al resolver la promesa de login PKCE de Cognito
    if (this.authService.tryLoginPromise) {
      this.authService.tryLoginPromise
        .then(() => {
          this.verificarSesion();
        })
        .catch(error => {
          this.verificarSesion();
          this.errorAutenticacion = 'No fue posible validar la sesión con AWS Cognito. Intenta iniciar sesión nuevamente.';
          console.error('[App] Error al completar el flujo OIDC:', error);
        });
    }
  }

  login(): void {
    console.log('[App] Invocando inicio de sesión en AWS Cognito mediante this.authService.login()...');
    this.errorAutenticacion = null;
    try {
      this.authService.login();
    } catch (error) {
      this.errorAutenticacion = 'No fue posible iniciar el flujo seguro de inicio de sesión.';
      console.error('[App] No se pudo iniciar el flujo OIDC con PKCE:', error);
    }
  }

  logout(): void {
    console.log('[App] Cerrando sesión y limpiando estado local...');
    this.authService.logout();
    this.autenticado = false;
    this.usuario = '';
    this.esAdmin = false;
    this.errorAutenticacion = null;
  }

  /**
   * Verifica la validez de la sesión, diagnostica el token en consola y extrae roles
   */
  verificarSesion(): void {
    console.log('[Auth Debug] ==========================================');
    console.log('[Auth Debug] Evaluando estado de sesión con isAuthenticated()...');

    this.autenticado = this.authService.isAuthenticated();
    console.log('[Auth Debug] this.autenticado =', this.autenticado);

    // Revisar si Cognito envió un parámetro de error en la URL de redirección
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('error')) {
      const errorType = urlParams.get('error');
      const errorDesc = urlParams.get('error_description') || 'Error desconocido al autenticar en Cognito';
      console.error(`[Auth Debug] ERROR recibido de Cognito en URL: ${errorType} - ${errorDesc}`);
      this.errorAutenticacion = `Error de autenticación: ${errorDesc}`;
    }

    if (this.autenticado) {
      this.usuario = this.authService.getUsername() || 'Usuario Autenticado';
      this.esAdmin = this.authService.isAdmin();
      this.errorAutenticacion = null;

      const claims = this.authService.getIdentityClaims();
      const grupos = claims ? (claims['cognito:groups'] || []) : [];

      console.log('[Auth Debug] Token válido detectado.');
      console.log('[Auth Debug] Nombre / Correo de usuario:', this.usuario);
      console.log('[Auth Debug] Claims decodificados del ID Token:', claims);
      console.log('[Auth Debug] Grupos de Cognito encontrados:', grupos);
      console.log('[Auth Debug] ¿Pertenece al grupo \'Admin\' (isAdmin)?:', this.esAdmin);

      if (!this.esAdmin) {
        console.warn('[Auth Debug] ADVERTENCIA: Usuario autenticado sin rol Admin. Operaciones CRUD de modificación restringidas.');
        this.mostrarAlerta(`Bienvenido ${this.usuario}. Tu usuario no pertenece al grupo 'Admin'; operaciones CRUD limitadas a solo lectura.`, 'info');
      } else {
        console.log('[Auth Debug] ÉXITO: Usuario verificado con rol de Administrador. Operaciones CRUD habilitadas.');
      }

      // Cargar pedidos reales desde el backend Spring Boot a través de API Gateway
      this.cargarPedidos();
    } else {
      this.usuario = '';
      this.esAdmin = false;
      this.pedidos = [];
      console.log('[Auth Debug] No se encontró una sesión activa o el token no ha sido procesado aún.');
    }
    console.log('[Auth Debug] Estado final -> autenticado:', this.autenticado, '| esAdmin:', this.esAdmin);
    console.log('[Auth Debug] ==========================================');
  }

  // GET: Cargar pedidos desde el backend Spring Boot / API Gateway
  cargarPedidos(): void {
    if (!this.autenticado || !this.authService.isAuthenticated()) {
      console.warn('[App] Solicitud GET a backend bloqueada: esperando confirmación de sesión válida.');
      return;
    }

    this.cargando = true;
    this.pedidosService.getPedidos().subscribe({
      next: (data: Pedido[]) => {
        this.cargando = false;
        this.pedidos = data;
        if (data.length > 0) {
          this.mostrarAlerta(`Sincronizados ${data.length} pedidos desde el backend Spring Boot.`, 'success');
        } else {
          this.mostrarAlerta('Conectado con backend Spring Boot: No hay pedidos registrados en base de datos PostgreSQL.', 'info');
        }
      },
      error: (err) => {
        this.cargando = false;
        // Se limpia la lista para evitar que datos simulados oculten fallos de conexión o autorización
        this.pedidos = [];
        const mensaje = this.formatearMensajeError(err, 'cargar la lista de pedidos');
        console.error('[App] Error al consultar backend Spring Boot / API Gateway:', err);
        this.mostrarAlerta(mensaje, 'danger');
      }
    });
  }

  // POST: Apertura de modal y creación
  abrirModalNuevo(): void {
    if (!this.esAdmin) {
      this.mostrarAlerta('Acceso denegado (403): Se requiere rol de Administrador (Grupo Admin en Cognito) para crear pedidos.', 'danger');
      return;
    }
    this.nuevoPedido = {
      producto: '',
      cantidad: 1,
      stock: 10,
      imageUrl: '',
      estado: 'PENDIENTE'
    };
    this.mostrarModalNuevo = true;
  }

  cerrarModalNuevo(): void {
    this.mostrarModalNuevo = false;
  }

  guardarNuevoPedido(): void {
    if (!this.esAdmin) {
      this.mostrarAlerta('Acceso denegado (403): Operación de creación reservada exclusivamente para Administradores.', 'danger');
      return;
    }

    if (!this.nuevoPedido.producto || this.nuevoPedido.cantidad < 1 || !this.nuevoPedido.imageUrl) {
      this.mostrarAlerta('Por favor completa todos los campos requeridos, incluyendo nombre del producto, cantidad e imagen.', 'danger');
      return;
    }

    this.cargando = true;

    // Payload alineado al backend (sin descripcion / sin id local)
    const backendPayload: Pedido = {
      producto: this.nuevoPedido.producto.trim(),
      cantidad: Number(this.nuevoPedido.cantidad),
      stock: Number(this.nuevoPedido.stock) || 0,
      imageUrl: this.nuevoPedido.imageUrl.trim(),
      estado: this.nuevoPedido.estado || 'PENDIENTE'
    };

    this.pedidosService.crearPedido(backendPayload).subscribe({
      next: (res: Pedido) => {
        this.cargando = false;
        this.pedidos.unshift(res);
        this.cerrarModalNuevo();
        this.mostrarAlerta(`Pedido [${res.id}] registrado exitosamente en el backend Spring Boot (POST 201).`, 'success');
      },
      error: (err) => {
        this.cargando = false;
        const mensaje = this.formatearMensajeError(err, 'crear el pedido');
        console.error('[App] Error al registrar pedido en backend:', err);
        this.mostrarAlerta(mensaje, 'danger');
      }
    });
  }

  // PUT: Apertura de modal y edición de estado
  abrirModalEditar(pedido: Pedido): void {
    if (!this.esAdmin) {
      this.mostrarAlerta('Acceso denegado (403): Se requiere rol de Administrador (Grupo Admin en Cognito) para editar pedidos.', 'danger');
      return;
    }
    this.pedidoSeleccionado = { ...pedido };
    this.mostrarModalEditar = true;
  }

  cerrarModalEditar(): void {
    this.mostrarModalEditar = false;
    this.pedidoSeleccionado = null;
  }

  guardarEdicionEstado(): void {
    if (!this.esAdmin) {
      this.mostrarAlerta('Acceso denegado (403): Operación de edición reservada para Administradores.', 'danger');
      return;
    }
    if (!this.pedidoSeleccionado || this.pedidoSeleccionado.id === undefined) return;

    this.cargando = true;
    const id = this.pedidoSeleccionado.id;
    const body: Pedido = { ...this.pedidoSeleccionado };

    this.pedidosService.actualizarPedido(id, body).subscribe({
      next: (res: Pedido) => {
        this.cargando = false;
        const index = this.pedidos.findIndex(p => p.id === id);
        if (index !== -1) {
          this.pedidos[index] = res || body;
        }
        this.cerrarModalEditar();
        this.mostrarAlerta(`Estado del pedido [${id}] actualizado exitosamente a "${body.estado}" en backend (PUT 200).`, 'success');
      },
      error: (err) => {
        this.cargando = false;
        const mensaje = this.formatearMensajeError(err, 'actualizar el estado del pedido');
        console.error('[App] Error al actualizar pedido en backend:', err);
        this.mostrarAlerta(mensaje, 'danger');
      }
    });
  }

  // DELETE: Cancelar / Eliminar pedido
  cancelarPedido(id: number | undefined): void {
    if (id === undefined) return;
    if (!this.esAdmin) {
      this.mostrarAlerta('Acceso denegado (403): Se requiere rol de Administrador (Grupo Admin en Cognito) para cancelar pedidos.', 'danger');
      return;
    }

    const confirmar = confirm(`¿Estás seguro de cancelar y eliminar el pedido con ID ${id}?`);
    if (!confirmar) return;

    this.cargando = true;
    this.pedidosService.eliminarPedido(id).subscribe({
      next: () => {
        this.cargando = false;
        this.pedidos = this.pedidos.filter(p => p.id !== id);
        this.mostrarAlerta(`Pedido [${id}] cancelado y eliminado correctamente del backend (DELETE).`, 'info');
      },
      error: (err) => {
        this.cargando = false;
        const mensaje = this.formatearMensajeError(err, 'eliminar el pedido');
        console.error('[App] Error al eliminar pedido en backend:', err);
        this.mostrarAlerta(mensaje, 'danger');
      }
    });
  }

  /**
   * Genera mensajes de error comprensibles y específicos según el estado HTTP
   */
  formatearMensajeError(error: any, accion: string): string {
    if (!error) return `Error inesperado al ${accion}.`;
    const status = error.status;

    if (status === 0) {
      return `Error de conexión: No se pudo contactar el backend Spring Boot / API Gateway al ${accion}. Verifica que el servicio esté activo y las políticas CORS permitidas.`;
    }
    if (status === 401) {
      return `Error 401 (No Autorizado): Sesión inválida o token vencido al ${accion}. Por favor inicia sesión nuevamente en AWS Cognito.`;
    }
    if (status === 403) {
      return `Error 403 (Acceso Denegado): Tu usuario no posee permisos de Administrador (Grupo Admin en Cognito) para ${accion}.`;
    }
    if (status === 404) {
      return `Error 404 (No Encontrado): El recurso solicitado no existe en el backend Spring Boot.`;
    }
    if (status === 409) {
      return error.error?.error || error.error?.message || error.message || `Error 409 (Conflicto): Conflicto en el servidor backend al ${accion}.`;
    }
    if (status >= 500) {
      return error.error?.error || error.error?.message || `Error ${status} (Servidor): Ocurrió un error interno en el backend Spring Boot al ${accion}.`;
    }
    return error.error?.error || error.error?.message || error.message || `Error (${status || 'desconocido'}) al ${accion}.`;
  }

  etiquetaEstado(estado?: EstadoPedido): string {
    const etiquetas: Record<EstadoPedido, string> = {
      PENDIENTE: 'Pendiente',
      EN_PREPARACION: 'En preparación',
      DISPONIBLE: 'Disponible',
      ENTREGADO: 'Entregado'
    };
    return estado ? etiquetas[estado] : 'Sin estado';
  }

  mostrarAlerta(mensaje: string, tipo: 'success' | 'danger' | 'info'): void {
    this.mensajeAlerta = mensaje;
    this.tipoAlerta = tipo;
    setTimeout(() => {
      if (this.mensajeAlerta === mensaje) {
        this.mensajeAlerta = null;
      }
    }, 5000);
  }
}
