package cl.duoc.pedidos360_consumer.consumer;

import cl.duoc.pedidos360_consumer.config.RabbitNames;
import cl.duoc.pedidos360_consumer.model.RabbitFailureLog;
import cl.duoc.pedidos360_consumer.repository.RabbitFailureLogRepository;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);
    private static final String DEFAULT_REASON = "Mensaje rechazado y enrutado a una DLQ";

    private final RabbitFailureLogRepository repository;

    public DeadLetterConsumer(RabbitFailureLogRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = RabbitNames.DLQ_INVENTARIO)
    public void registrarFalloInventario(Message message, Channel channel) throws IOException {
        registrarFallo(RabbitNames.DLQ_INVENTARIO, message, channel);
    }

    @RabbitListener(queues = RabbitNames.DLQ_ELIMINACIONES)
    public void registrarFalloEliminaciones(Message message, Channel channel) throws IOException {
        registrarFallo(RabbitNames.DLQ_ELIMINACIONES, message, channel);
    }

    @RabbitListener(queues = RabbitNames.DLQ_SEGURIDAD)
    public void registrarFalloSeguridad(Message message, Channel channel) throws IOException {
        registrarFallo(RabbitNames.DLQ_SEGURIDAD, message, channel);
    }

    private void registrarFallo(String dlqQueue, Message message, Channel channel) throws IOException {
        var properties = message.getMessageProperties();
        Map<String, Object> headers = properties.getHeaders();

        String messageId = properties.getMessageId();
        RabbitFailureLog failure = messageId == null
                ? new RabbitFailureLog()
                : repository.findByMessageId(messageId).orElseGet(RabbitFailureLog::new);
        failure.setDlqQueue(dlqQueue);
        String originalQueue = header(headers, "x-first-death-queue");
        if (originalQueue != null) {
            failure.setOriginalQueue(originalQueue);
        }
        String exchangeName = header(headers, "x-first-death-exchange");
        if (exchangeName != null) {
            failure.setExchangeName(exchangeName);
        }
        String routingKey = firstRoutingKey(headers.get("x-first-death-routing-key"));
        if (routingKey != null) {
            failure.setRoutingKey(routingKey);
        }
        failure.setMessageId(messageId);
        String rabbitReason = header(headers, "x-first-death-reason", DEFAULT_REASON);
        String failureReason = failure.getFailureReason();
        String rabbitReasonSuffix = " (RabbitMQ: " + rabbitReason + ")";
        if (failureReason == null) {
            failure.setFailureReason(rabbitReason);
        } else if (!failureReason.endsWith(rabbitReasonSuffix)) {
            failure.setFailureReason(failureReason + rabbitReasonSuffix);
        }
        failure.setPayload(new String(message.getBody(), StandardCharsets.UTF_8));

        repository.saveAndFlush(failure);
        channel.basicAck(properties.getDeliveryTag(), false);
        log.error("[RABBIT DLQ] Fallo persistido en auditoria_db. messageId={}, queue={}, originalQueue={}, reason={}",
                failure.getMessageId(), dlqQueue, failure.getOriginalQueue(), failure.getFailureReason());
    }

    private static String header(Map<String, Object> headers, String name) {
        return header(headers, name, null);
    }

    private static String header(Map<String, Object> headers, String name, String fallback) {
        Object value = headers.get(name);
        return value == null ? fallback : String.valueOf(value);
    }

    private static String firstRoutingKey(Object value) {
        if (value instanceof List<?> values && !values.isEmpty()) {
            return String.valueOf(values.get(0));
        }
        return value == null ? null : String.valueOf(value);
    }
}
