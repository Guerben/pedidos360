package cl.duoc.pedidos360_backend.service;

import cl.duoc.pedidos360_backend.config.RabbitNames;
import cl.duoc.pedidos360_backend.model.OutboxEvento;
import cl.duoc.pedidos360_backend.model.Pedido;
import cl.duoc.pedidos360_backend.repository.OutboxEventoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventoRepository outboxRepository;

    private ObjectMapper objectMapper;
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        outboxService = new OutboxService(outboxRepository, objectMapper);
    }

    @Test
    void testRegistrarEventoInventarioGuardaEnOutbox() {
        Pedido pedido = new Pedido(10L, "Notebook Gamer", "https://img.com/nb.jpg", 1, 20, "DISPONIBLE");

        outboxService.registrarEventoInventario(pedido, "CREACION", RabbitNames.RK_PRODUCTO_CREADO);

        ArgumentCaptor<OutboxEvento> captor = ArgumentCaptor.forClass(OutboxEvento.class);
        verify(outboxRepository, times(1)).save(captor.capture());

        OutboxEvento guardado = captor.getValue();
        assertNotNull(guardado.getId());
        assertEquals("PRODUCTO", guardado.getAggregateType());
        assertEquals("10", guardado.getAggregateId());
        assertEquals(RabbitNames.RK_PRODUCTO_CREADO, guardado.getRoutingKey());
        assertEquals(RabbitNames.TOPIC_EXCHANGE, guardado.getExchange());
        assertEquals("PENDIENTE", guardado.getEstado());
        assertTrue(guardado.getPayload().contains("Notebook Gamer"));
        assertTrue(guardado.getPayload().contains("CREACION"));
    }

    @Test
    void testRegistrarEventoEliminacionPreservaSnapshotEnOutbox() {
        Pedido pedido = new Pedido(25L, "GPU RTX 4090", "https://img.com/gpu.jpg", 2, 5, "EN_PREPARACION");

        outboxService.registrarEventoEliminacion(pedido);

        ArgumentCaptor<OutboxEvento> captor = ArgumentCaptor.forClass(OutboxEvento.class);
        verify(outboxRepository, times(1)).save(captor.capture());

        OutboxEvento guardado = captor.getValue();
        assertNotNull(guardado.getId());
        assertEquals("25", guardado.getAggregateId());
        assertEquals(RabbitNames.RK_PRODUCTO_ELIMINADO, guardado.getRoutingKey());
        assertTrue(guardado.getPayload().contains("GPU RTX 4090"));
        assertTrue(guardado.getPayload().contains("ELIMINACION"));
    }
}

