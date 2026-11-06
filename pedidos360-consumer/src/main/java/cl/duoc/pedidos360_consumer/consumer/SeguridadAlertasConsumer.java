package cl.duoc.pedidos360_consumer.consumer;

import cl.duoc.pedidos360_consumer.config.RabbitNames;
import cl.duoc.pedidos360_consumer.model.AuditoriaSeguridad;
import cl.duoc.pedidos360_consumer.repository.AuditoriaSeguridadRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class SeguridadAlertasConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeguridadAlertasConsumer.class);

    private final ObjectMapper objectMapper;
    private final AuditoriaSeguridadRepository repository;
    private final ConsumerSupport support;

    public SeguridadAlertasConsumer(ObjectMapper objectMapper,
                                    AuditoriaSeguridadRepository repository,
                                    ConsumerSupport support) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.support = support;
    }

    @RabbitListener(queues = RabbitNames.COLA_SEGURIDAD_ALERTAS)
    public void procesar(String payload, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                         @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {

        log.info("[SEGURIDAD] Mensaje recibido deliveryTag={}: {}", deliveryTag, payload);

        support.procesarConReintentos("SEGURIDAD", RabbitNames.QUEUE_SEGURIDAD, RabbitNames.DLQ_SEGURIDAD,
                messageId, payload, deliveryTag, channel, () -> {
            if (payload == null || payload.isBlank()) {
                throw new ConsumerSupport.PermanentMessageException("Payload vacio");
            }
            if (payload.contains("FORCE_ERROR")) {
                throw new ConsumerSupport.PermanentMessageException("Payload de prueba fuerza DLQ");
            }

            JsonNode node;
            try {
                node = objectMapper.readTree(payload);
            } catch (Exception ex) {
                throw new ConsumerSupport.PermanentMessageException("JSON invalido", ex);
            }

            String eventoIdRaw = text(node, "eventoId");
            if (eventoIdRaw == null || eventoIdRaw.isBlank()) {
                throw new ConsumerSupport.PermanentMessageException("eventoId ausente");
            }

            UUID eventoId;
            try {
                eventoId = UUID.fromString(eventoIdRaw);
            } catch (IllegalArgumentException ex) {
                throw new ConsumerSupport.PermanentMessageException("eventoId no es UUID", ex);
            }

            if (repository.existsByEventoId(eventoId)) {
                log.info("[SEGURIDAD] Evento duplicado {} — ACK sin insertar.", eventoId);
                return;
            }

            AuditoriaSeguridad entity = new AuditoriaSeguridad();
            entity.setEventoId(eventoId);
            entity.setTipoEvento(textOrDefault(node, "tipoEvento", "ALERTA_ACCESO_DENEGADO"));
            entity.setUsuarioSub(text(node, "usuarioSub"));
            entity.setUsuarioEmail(text(node, "usuarioEmail"));
            entity.setRuta(text(node, "ruta"));
            entity.setMetodo(text(node, "metodo"));
            entity.setMotivo(text(node, "motivo"));
            entity.setFechaEvento(support.parseFecha(text(node, "fechaEvento")));
            repository.save(entity);

            log.info("[SEGURIDAD] Alerta persistida evento {} usuario={}", eventoId, entity.getUsuarioSub());
        });
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String textOrDefault(JsonNode node, String field, String defaultValue) {
        String value = text(node, field);
        return value != null ? value : defaultValue;
    }
}
