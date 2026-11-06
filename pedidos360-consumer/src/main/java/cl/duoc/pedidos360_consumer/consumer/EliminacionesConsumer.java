package cl.duoc.pedidos360_consumer.consumer;

import cl.duoc.pedidos360_consumer.config.RabbitNames;
import cl.duoc.pedidos360_consumer.dto.AuditoriaEventoDTO;
import cl.duoc.pedidos360_consumer.model.HistoricoBajas;
import cl.duoc.pedidos360_consumer.repository.HistoricoBajasRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class EliminacionesConsumer {

    private static final Logger log = LoggerFactory.getLogger(EliminacionesConsumer.class);

    private final ObjectMapper objectMapper;
    private final HistoricoBajasRepository repository;
    private final ConsumerSupport support;

    public EliminacionesConsumer(ObjectMapper objectMapper,
                                 HistoricoBajasRepository repository,
                                 ConsumerSupport support) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.support = support;
    }

    @RabbitListener(queues = RabbitNames.COLA_AUDITORIA_ELIMINACIONES)
    public void procesar(String payload, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                         @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {

        log.info("[ELIMINACIONES] Mensaje recibido deliveryTag={}: {}", deliveryTag, payload);

        support.procesarConReintentos("ELIMINACIONES", RabbitNames.QUEUE_ELIMINACIONES, RabbitNames.DLQ_ELIMINACIONES,
                messageId, payload, deliveryTag, channel, () -> {
            if (payload == null || payload.isBlank()) {
                throw new ConsumerSupport.PermanentMessageException("Payload vacio");
            }
            if (payload.contains("FORCE_ERROR")) {
                throw new ConsumerSupport.PermanentMessageException("Payload de prueba fuerza DLQ");
            }

            AuditoriaEventoDTO dto;
            try {
                dto = objectMapper.readValue(payload, AuditoriaEventoDTO.class);
            } catch (Exception ex) {
                throw new ConsumerSupport.PermanentMessageException("JSON invalido", ex);
            }

            if (dto.getEventoId() == null) {
                throw new ConsumerSupport.PermanentMessageException("eventoId ausente");
            }

            if (repository.existsByEventoId(dto.getEventoId())) {
                log.info("[ELIMINACIONES] Evento duplicado {} — ACK sin insertar.", dto.getEventoId());
                return;
            }

            HistoricoBajas entity = new HistoricoBajas();
            entity.setEventoId(dto.getEventoId());
            entity.setProductoId(dto.getProductoId());
            entity.setNombreProducto(dto.getNombreProducto());
            entity.setCantidad(dto.getCantidad());
            entity.setStock(dto.getStock());
            entity.setEstado(dto.getEstado());
            entity.setImageUrl(dto.getImageUrl());
            entity.setUsuarioSub(dto.getUsuarioSub());
            entity.setUsuarioEmail(dto.getUsuarioEmail());
            entity.setFechaEvento(support.parseFecha(dto.getFechaEvento()));
            repository.save(entity);

            log.info("[ELIMINACIONES] Historico baja persistido evento {} productoId={}",
                    dto.getEventoId(), dto.getProductoId());
        });
    }
}
