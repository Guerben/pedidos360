package cl.duoc.pedidos360_consumer.consumer;

import cl.duoc.pedidos360_consumer.model.RabbitFailureLog;
import cl.duoc.pedidos360_consumer.repository.RabbitFailureLogRepository;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utilidades compartidas: reintentos locales con backoff y ACK/NACK hacia DLQ.
 */
@Component
public class ConsumerSupport {

    private static final Logger log = LoggerFactory.getLogger(ConsumerSupport.class);

    @Value("${auditoria.max-reintentos:3}")
    private int maxReintentos = 3;

    private final RabbitFailureLogRepository failureLogRepository;

    public ConsumerSupport(RabbitFailureLogRepository failureLogRepository) {
        this.failureLogRepository = failureLogRepository;
    }

    public Instant parseFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(fecha);
        } catch (DateTimeParseException ex) {
            return Instant.now();
        }
    }

    public void procesarConReintentos(String dominio, String colaPrincipal, String dlq,
            String messageId, String payload, long deliveryTag, Channel channel, Runnable trabajo) throws IOException {
        Exception ultimoError = null;

        for (int intento = 1; intento <= maxReintentos; intento++) {
            try {
                trabajo.run();
                channel.basicAck(deliveryTag, false);
                log.info("[{}] basicAck emitido deliveryTag={} (intento {}/{}).",
                        dominio, deliveryTag, intento, maxReintentos);
                return;
            } catch (PermanentMessageException permanent) {
                log.error("[{}] Error permanente deliveryTag={}: {}. Enviando a DLQ.",
                        dominio, deliveryTag, permanent.getMessage());
                persistirFallo(colaPrincipal, dlq, messageId, payload, permanent.getMessage());
                channel.basicNack(deliveryTag, false, false);
                return;
            } catch (Exception ex) {
                ultimoError = ex;
                log.warn("[{}] Falla transitoria deliveryTag={} intento {}/{}: {}",
                        dominio, deliveryTag, intento, maxReintentos, ex.getMessage());
                if (intento < maxReintentos) {
                    sleepBackoff(intento);
                }
            }
        }

        log.error("[{}] Agotados {} reintentos deliveryTag={}. NACK -> DLQ. Causa: {}",
                dominio, maxReintentos, deliveryTag,
                ultimoError != null ? ultimoError.getMessage() : "desconocida");
        persistirFallo(colaPrincipal, dlq, messageId, payload,
                ultimoError != null ? ultimoError.getMessage() : "Error desconocido");
        channel.basicNack(deliveryTag, false, false);
    }

    private void persistirFallo(String colaPrincipal, String dlq, String messageId, String payload, String reason) {
        RabbitFailureLog failure = messageId == null
                ? new RabbitFailureLog()
                : failureLogRepository.findByMessageId(messageId).orElseGet(RabbitFailureLog::new);
        failure.setDlqQueue(dlq);
        failure.setOriginalQueue(colaPrincipal);
        failure.setMessageId(messageId);
        failure.setFailureReason(reason);
        failure.setPayload(payload == null ? "" : payload);
        failureLogRepository.saveAndFlush(failure);
    }

    private void sleepBackoff(int intento) {
        long millis = (long) Math.pow(2, intento - 1) * 1000L; // 1s, 2s, 4s
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Errores de payload / contrato que no se recuperan reintentando.
     */
    public static class PermanentMessageException extends RuntimeException {
        public PermanentMessageException(String message) {
            super(message);
        }

        public PermanentMessageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
