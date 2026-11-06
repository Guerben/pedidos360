package cl.duoc.pedidos360_consumer.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.pedidos360_consumer.config.RabbitNames;
import cl.duoc.pedidos360_consumer.model.RabbitFailureLog;
import cl.duoc.pedidos360_consumer.repository.RabbitFailureLogRepository;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class DeadLetterConsumerTest {

    @Mock
    private Channel channel;

    @Mock
    private RabbitFailureLogRepository repository;

    @Test
    void registrarFallo_persisteDatosDelMensajeAntesDeAck() throws Exception {
        DeadLetterConsumer consumer = new DeadLetterConsumer(repository);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(17L);
        properties.setMessageId("evento-123");
        properties.setHeader("x-first-death-queue", "cola.auditoria.inventario");
        properties.setHeader("x-first-death-exchange", "pedidos.auditoria.topic");
        properties.setHeader("x-first-death-routing-key", List.of("inventario.producto.creado"));
        properties.setHeader("x-first-death-reason", "rejected");
        Message message = new Message(
                "{\"eventoId\":\"evento-123\"}".getBytes(StandardCharsets.UTF_8),
                properties
        );
        when(repository.saveAndFlush(any(RabbitFailureLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        consumer.registrarFalloInventario(message, channel);

        ArgumentCaptor<RabbitFailureLog> savedLog = ArgumentCaptor.forClass(RabbitFailureLog.class);
        verify(repository).saveAndFlush(savedLog.capture());
        verify(channel).basicAck(17L, false);
        org.junit.jupiter.api.Assertions.assertEquals(RabbitNames.DLQ_INVENTARIO, savedLog.getValue().getDlqQueue());
        org.junit.jupiter.api.Assertions.assertEquals("cola.auditoria.inventario", savedLog.getValue().getOriginalQueue());
        org.junit.jupiter.api.Assertions.assertEquals("inventario.producto.creado", savedLog.getValue().getRoutingKey());
        org.junit.jupiter.api.Assertions.assertEquals("rejected", savedLog.getValue().getFailureReason());
        org.junit.jupiter.api.Assertions.assertEquals("{\"eventoId\":\"evento-123\"}", savedLog.getValue().getPayload());
    }
}
