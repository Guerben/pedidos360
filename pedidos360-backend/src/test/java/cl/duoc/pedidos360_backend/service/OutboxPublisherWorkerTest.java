package cl.duoc.pedidos360_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.pedidos360_backend.model.OutboxEvento;
import cl.duoc.pedidos360_backend.repository.OutboxEventoRepository;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherWorkerTest {

    @Mock
    private OutboxEventoRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxPublisherWorker worker;

    @BeforeEach
    void setUp() {
        worker = new OutboxPublisherWorker(outboxRepository, rabbitTemplate);
    }

    @Test
    void procesarEventosPendientes_cuandoNoHayEventos_noPublicaNada() {
        when(outboxRepository.findTop50ByEstadoOrderByCreadoEnAsc("PENDIENTE"))
                .thenReturn(Collections.emptyList());

        worker.procesarEventosPendientes();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void procesarEventosPendientes_cuandoHayEventos_publicaYMarcaPublicado() {
        OutboxEvento evento = new OutboxEvento();
        evento.setId(UUID.randomUUID());
        evento.setTipoEvento("PRODUCTO_CREADO");
        evento.setExchange("pedidos.auditoria.topic");
        evento.setRoutingKey("inventario.producto.creado");
        evento.setPayload("{\"eventoId\":\"abc-123\"}");
        evento.setEstado("PENDIENTE");
        evento.setReintentos(0);

        when(outboxRepository.findTop50ByEstadoOrderByCreadoEnAsc("PENDIENTE"))
                .thenReturn(List.of(evento));

        // Simulamos respuesta ACK en el CorrelationData (argumento 4)
        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(4);
            if (cd != null) {
                CorrelationData.Confirm confirm = new CorrelationData.Confirm(true, null);
                cd.getFuture().complete(confirm);
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(evento.getExchange()),
                eq(evento.getRoutingKey()),
                eq(evento.getPayload()),
                any(),
                any(CorrelationData.class)
        );

        worker.procesarEventosPendientes();

        assertEquals("PUBLICADO", evento.getEstado());
        verify(outboxRepository).save(evento);
    }

    @Test
    void procesarEventosPendientes_cuandoFallaPublicacion_incrementaReintentos() {
        OutboxEvento evento = new OutboxEvento();
        evento.setId(UUID.randomUUID());
        evento.setTipoEvento("PRODUCTO_ACTUALIZADO");
        evento.setExchange("pedidos.auditoria.topic");
        evento.setRoutingKey("inventario.producto.actualizado");
        evento.setPayload("{\"eventoId\":\"def-456\"}");
        evento.setEstado("PENDIENTE");
        evento.setReintentos(1);

        when(outboxRepository.findTop50ByEstadoOrderByCreadoEnAsc("PENDIENTE"))
                .thenReturn(List.of(evento));

        doThrow(new RuntimeException("Error simulado de conexión con RabbitMQ"))
                .when(rabbitTemplate).convertAndSend(
                        eq(evento.getExchange()),
                        eq(evento.getRoutingKey()),
                        eq(evento.getPayload()),
                        any(),
                        any(CorrelationData.class)
                );

        worker.procesarEventosPendientes();

        assertEquals(2, evento.getReintentos());
        assertEquals("PENDIENTE", evento.getEstado());
        verify(outboxRepository).save(evento);
    }

    @Test
    void procesarEventosPendientes_cuandoMensajeNoTieneRuta_noMarcaPublicado() {
        OutboxEvento evento = new OutboxEvento();
        evento.setId(UUID.randomUUID());
        evento.setTipoEvento("PRODUCTO_CREADO");
        evento.setExchange("pedidos.auditoria.topic");
        evento.setRoutingKey("inventario.producto.sin_binding");
        evento.setPayload("{\"eventoId\":\"jkl-012\"}");
        evento.setEstado("PENDIENTE");
        evento.setReintentos(0);

        when(outboxRepository.findTop50ByEstadoOrderByCreadoEnAsc("PENDIENTE"))
                .thenReturn(List.of(evento));

        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.setReturned(new ReturnedMessage(
                    new Message(evento.getPayload().getBytes(), new MessageProperties()),
                    312,
                    "NO_ROUTE",
                    evento.getExchange(),
                    evento.getRoutingKey()
            ));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(evento.getExchange()),
                eq(evento.getRoutingKey()),
                eq(evento.getPayload()),
                any(),
                any(CorrelationData.class)
        );

        worker.procesarEventosPendientes();

        assertEquals("PENDIENTE", evento.getEstado());
        assertEquals(1, evento.getReintentos());
        verify(outboxRepository).save(evento);
    }

    @Test
    void procesarEventosPendientes_cuandoSuperaMaxReintentos_marcaFallido() {
        OutboxEvento evento = new OutboxEvento();
        evento.setId(UUID.randomUUID());
        evento.setTipoEvento("PRODUCTO_ELIMINADO");
        evento.setExchange("pedidos.auditoria.topic");
        evento.setRoutingKey("bajas.producto.eliminado");
        evento.setPayload("{\"eventoId\":\"ghi-789\"}");
        evento.setEstado("PENDIENTE");
        evento.setReintentos(4); // Al incrementar alcanzará 5 (MAX_REINTENTOS)

        when(outboxRepository.findTop50ByEstadoOrderByCreadoEnAsc("PENDIENTE"))
                .thenReturn(List.of(evento));

        doThrow(new RuntimeException("Broker no disponible repetidamente"))
                .when(rabbitTemplate).convertAndSend(
                        eq(evento.getExchange()),
                        eq(evento.getRoutingKey()),
                        eq(evento.getPayload()),
                        any(),
                        any(CorrelationData.class)
                );

        worker.procesarEventosPendientes();

        assertEquals(5, evento.getReintentos());
        assertEquals("FALLIDO", evento.getEstado());
        verify(outboxRepository).save(evento);
    }
}
