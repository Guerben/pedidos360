import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';
import { Pedido, PedidosService } from './pedidos.service';

describe('PedidosService', () => {
  let service: PedidosService;
  let httpTestingController: HttpTestingController;

  const pedido: Pedido = {
    id: 12,
    producto: 'Notebook',
    imageUrl: 'https://example.com/notebook.jpg',
    cantidad: 1,
    stock: 5,
    estado: 'PENDIENTE'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: () => true,
            getAccessToken: () => 'valid-access-token'
          }
        }
      ]
    });

    service = TestBed.inject(PedidosService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  it('carga solo listas de pedidos y envía el Access Token', () => {
    let pedidos: Pedido[] | undefined;

    service.getPedidos().subscribe(result => pedidos = result);

    const request = httpTestingController.expectOne(environment.api.pedidosUrl);
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer valid-access-token');
    request.flush([pedido]);

    expect(pedidos).toEqual([pedido]);
  });

  it('rechaza respuestas GET fuera del contrato List<Pedido>', () => {
    let error: unknown;

    service.getPedidos().subscribe({ error: received => error = received });

    httpTestingController.expectOne(environment.api.pedidosUrl).flush({ content: [pedido] });

    expect(error).toBeInstanceOf(Error);
  });

  it('rechaza una creación si el backend no devuelve el ID persistido', () => {
    const payload = {
      producto: pedido.producto,
      imageUrl: pedido.imageUrl,
      cantidad: pedido.cantidad,
      stock: pedido.stock,
      estado: pedido.estado
    };
    let error: unknown;

    service.crearPedido(payload).subscribe({ error: received => error = received });

    const request = httpTestingController.expectOne(environment.api.pedidosUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush(payload);

    expect(error).toBeInstanceOf(Error);
  });
});
