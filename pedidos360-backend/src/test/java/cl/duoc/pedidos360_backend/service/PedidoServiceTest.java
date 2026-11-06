package cl.duoc.pedidos360_backend.service;

import cl.duoc.pedidos360_backend.config.RabbitNames;
import cl.duoc.pedidos360_backend.exception.ResourceNotFoundException;
import cl.duoc.pedidos360_backend.model.Pedido;
import cl.duoc.pedidos360_backend.repository.PedidoRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private PedidoService pedidoService;

    private Pedido pedidoMock;

    @BeforeEach
    void setUp() {
        pedidoMock = new Pedido(
                1L,
                "Servidor Dell PowerEdge",
                "https://ejemplo.com/servidor.jpg",
                2,
                10,
                "EN_PREPARACION"
        );
    }

    @Test
    void testCrearPedidoExitosoGeneraEventoOutbox() {
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoMock);

        Pedido resultado = pedidoService.crearPedido(pedidoMock);

        assertNotNull(resultado);
        assertEquals("Servidor Dell PowerEdge", resultado.getProducto());
        verify(pedidoRepository, times(1)).save(pedidoMock);
        verify(outboxService, times(1)).registrarEventoInventario(
                eq(pedidoMock),
                eq("CREACION"),
                eq(RabbitNames.RK_PRODUCTO_CREADO)
        );
    }

    @Test
    void testCrearPedidoSinImageUrlLanzaExcepcion() {
        Pedido invalido = new Pedido("Notebook", "   ", 1, 5, "PENDIENTE");

        assertThrows(IllegalArgumentException.class, () -> pedidoService.crearPedido(invalido));
        verify(pedidoRepository, never()).save(any());
        verify(outboxService, never()).registrarEventoInventario(any(), any(), any());
    }

    @Test
    void testCrearPedidoConEstadoNoSoportadoLanzaExcepcion() {
        Pedido invalido = new Pedido("Notebook", "https://ejemplo.com/notebook.jpg", 1, 5, "ENVIADO");

        assertThrows(IllegalArgumentException.class, () -> pedidoService.crearPedido(invalido));
        verify(pedidoRepository, never()).save(any());
        verify(outboxService, never()).registrarEventoInventario(any(), any(), any());
    }

    @Test
    void testActualizarPedidoExitosoGeneraEventoOutbox() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoMock));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedidoMock);

        Pedido detalles = new Pedido("Servidor Dell Actualizado", "https://ejemplo.com/nuevo.jpg", 3, 8, "DISPONIBLE");
        Pedido actualizado = pedidoService.actualizarPedido(1L, detalles);

        assertNotNull(actualizado);
        assertEquals("Servidor Dell Actualizado", actualizado.getProducto());
        assertEquals("DISPONIBLE", actualizado.getEstado());
        verify(pedidoRepository, times(1)).save(pedidoMock);
        verify(outboxService, times(1)).registrarEventoInventario(
                eq(pedidoMock),
                eq("MODIFICACION"),
                eq(RabbitNames.RK_PRODUCTO_MODIFICADO)
        );
    }

    @Test
    void testEliminarPedidoCapturaSnapshotAntesDeEliminar() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedidoMock));

        pedidoService.eliminarPedido(1L);

        // Se verifica que outboxService recibio el snapshot completo del producto
        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(outboxService, times(1)).registrarEventoEliminacion(captor.capture());

        Pedido snapshotCapturado = captor.getValue();
        assertEquals(1L, snapshotCapturado.getId());
        assertEquals("Servidor Dell PowerEdge", snapshotCapturado.getProducto());
        assertEquals(10, snapshotCapturado.getStock());

        // Se verifica que la entidad fue eliminada posteriormente del repositorio
        verify(pedidoRepository, times(1)).delete(pedidoMock);
    }

    @Test
    void testEliminarPedidoInexistenteLanzaNotFound() {
        when(pedidoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pedidoService.eliminarPedido(99L));
        verify(outboxService, never()).registrarEventoEliminacion(any());
        verify(pedidoRepository, never()).delete(any());
    }
}
